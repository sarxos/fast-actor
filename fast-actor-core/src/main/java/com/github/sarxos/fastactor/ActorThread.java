package com.github.sarxos.fastactor;

import static com.github.sarxos.fastactor.ActorCell.DeliveryStatus.ACCEPTED;
import static com.github.sarxos.fastactor.ActorCell.ProcessingStatus.COMPLETE;
import static com.github.sarxos.fastactor.ActorSystem.ZERO_UUID;
import static java.util.concurrent.locks.LockSupport.unpark;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

import org.jctools.maps.NonBlockingHashMapLong;
import org.jctools.queues.MpscUnboundedArrayQueue;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;


public class ActorThread extends Thread {

	/**
	 * How many idle loops {@link ActorThread} should perform before thread is parked.
	 */
	static int maxIdleLoopsCount = 1024;

	static long delay = Duration.ofMillis(100).toNanos();

	/**
	 * Mapping between cell {@link UUID} and corresponding {@link ActorCell} instance.
	 */
	final NonBlockingHashMapLong<ActorCell<? extends Actor>> dockedCells = new NonBlockingHashMapLong<>();

	/**
	 * The active cells are the ones which have at least one message in the inbox. This map is not
	 * thread-safe, so please do not use outside the {@link ActorThread} it's referenced on.
	 */
	final Long2ObjectOpenHashMap<ActorCell<? extends Actor>> activeCells = new Long2ObjectOpenHashMap<>();

	/**
	 * {@link Runnable}s which will be run after this {@link Thread} is completed.
	 */
	final Deque<Runnable> terminators = new ArrayDeque<>(2);

	/**
	 * Queue to store messages from cells docked on this thread (internal communication).
	 */
	final Queue<Envelope> internalQueue = new ArrayDeque<>();

	/**
	 * Queue to store messages from cells docked on other threads (interthread communication).
	 */
	final MpscUnboundedArrayQueue<Envelope> externalQueue = new MpscUnboundedArrayQueue<>(4096);

	/**
	 * Is thread parked.
	 */
	// final AtomicBoolean parked = new AtomicBoolean(true);

	final VolatileBoolean parked = new VolatileBoolean(false);

	// final VolatileBoolean running = new VolatileBoolean(true);

	/**
	 * The {@link ActorSystem} this {@link ActorThread} lives in.
	 */
	final ActorSystem system;

	/**
	 * The {@link ActorThreadPool} stores {@link ActorThread} instances in the list. This is a
	 * positional index of this {@link ActorThread} in the list.
	 */
	final int index;

	/**
	 * How many messages should be processed by a single actor before moving to the next one.
	 */
	final int throughput;

	ActorThread(final ThreadGroup group, final ActorSystem system, final String name, final int index) {
		super(group, name);
		this.system = system;
		this.index = index;
		this.throughput = system.throughput;
	}

	@Override
	public void run() {
		try {
			onRun();
		} finally {
			onComplete();
		}
	}

	private void onRun() {

		final var queue = new ArrayDeque<Envelope>();
		final var idler = new IdleLoopCounter(maxIdleLoopsCount);

		while (!isInterrupted()) {
			// while (running.value) {

			// move external messages to the temporary queue to avoid contention
			// move internal messages to the temporary queue to avoid concurrent modification

			var busy = 0;

			busy += drain(externalQueue, queue);
			busy += drain(internalQueue, queue);
			busy += deliver(queue);
			busy += process();

			if (busy == 0) {
				if (idler.shouldBeParked()) {
					park();
				} else {
					spin();
				}
			} else {
				idler.reset();
			}
		}
	}

	private void park() {
		parked.value = true;
		// LockSupport.park(this);
		LockSupport.parkNanos(delay);
		parked.value = false;
	}

	private void spin() {
		Thread.onSpinWait();
	}

	private <T> int drain(final Queue<T> source, final Queue<T> target) {
		int count = 0;
		do {
			final T element = source.poll();
			if (element == null) {
				return count;
			} else {
				if (target.offer(element)) {
					count++;
				}
			}
		} while (true);
	}

	private int deliver(final Queue<Envelope> queue) {

		var t = this;
		var i = 0;

		for (;;) {

			final var envelope = queue.poll();
			if (envelope == null) {
				return i;
			} else {
				i++;
			}

			final var uuid = envelope.target.uuid();
			if (uuid == ZERO_UUID) {
				system.forwardToDeadLetters(envelope);
			}

			final var cell = activeCells.computeIfAbsent(uuid, t::findCell);

			deliver(envelope, cell);
		}
	}

	private int deliver(final Envelope envelope, final ActorCell<? extends Actor> target) {

		if (target == null) {
			return noCellFoundForTarget(envelope, target);
		}

		final var status = target.deliver(envelope);

		if (status == ACCEPTED) {
			return 1;
		}

		if (envelope.message instanceof Directive) {
			((Directive) envelope.message).failed();
		} else {
			system.forwardToDeadLetters(envelope);
		}

		return 0;
	}

	private int noCellFoundForTarget(final Envelope envelope, final ActorCell<? extends Actor> target) {

		if (envelope.message instanceof Directive) {
			((Directive) envelope.message).failed();
		} else {
			system.forwardToDeadLetters(envelope);
		}

		return 0;
	}

	/**
	 * Iterates over the active cells and process up to {@link #throughput} messages. When inbox is
	 * empty after processing completion, the cell becomes inactive and can be removed from the
	 * active cells map.
	 */
	private int process() {

		final var iterator = activeCells.values().iterator();

		var i = 0;

		while (iterator.hasNext()) {

			final var cell = iterator.next();
			if (cell == null) {
				// XXX this is edgy - there should not be null here, but there is
				continue;
			}

			final var status = cell.process(throughput);

			if (status == COMPLETE) {
				iterator.remove();
			} else {
				i++;
				// more iterations required
			}
		}

		return i;
	}

	@SuppressWarnings("rawtypes")
	private ActorCell findCell(final long uuid) {
		return dockedCells.get(uuid);
	}

	public ActorThread withTerminator(final Runnable terminator) {
		terminators.add(terminator);
		return this;
	}

	public void dock(final ActorCell<? extends Actor> cell) {

		final var uuid = cell.uuid();
		final var overwritten = dockedCells.putIfAbsent(uuid, cell) != null;

		if (overwritten) {
			throw new IllegalStateException("Cell with ID " + uuid + " already docked on thread " + getName());
		}
	}

	/**
	 * Undock cell with a given ID from this {@link ActorThread}. This will permanently remove
	 * {@link ActorCell} with a given ID from the cells docked on this thread. If the cell was
	 * active this operation will not make it inactive, which means that all messages which were
	 * being delivered, will be processed till the end regardless if given cell is docked here.
	 *
	 * @param uuid the {@link ActorCell} ID
	 * @return The removed {@link ActorCell} or null when no cell with a given ID was docked here
	 */
	public ActorCell<? extends Actor> remove(final long uuid) {
		return dockedCells.remove(uuid);
	}

	/**
	 * Deposit envelope with a message into the queue. This method will wake up the
	 * {@link ActorThread} if it was parked.
	 *
	 * @param envelope the envelope with message
	 */
	public void deposit(final Envelope envelope) {
		if (this == currentThread()) {
			deposit(envelope, internalQueue);
		} else {
			deposit(envelope, externalQueue);
		}
	}

	/**
	 * Deposit envelope into the specified queue.
	 *
	 * @param envelope the envelope to deposit
	 * @param queue the queue where it should be added
	 */
	private void deposit(final Envelope envelope, final Queue<Envelope> queue) {
		queue.offer(envelope);
		wakeUp();
	}

	/**
	 * Wake up (unpark) the {@link ActorThread} if queues were modified.
	 *
	 * @param modified
	 */
	private void wakeUp() {
		while (parked.value) {
			parked.value = false;
			unpark(this);
		}
	}

	private void onComplete() {
		terminators.forEach(Runnable::run);
	}

	public static void setMaxIdleLoopsCount(int maxIdleLoopsCount) {
		ActorThread.maxIdleLoopsCount = maxIdleLoopsCount;
	}

	/**
	 * Simple counter to count idle loops to decide if {@link ActorThread} should be parked. The
	 * {@link ActorThread} should not be parked immediately after it's free, but only after if
	 * burned the {@value ActorThread#maxIdleLoopsCount} number of idle loops. This is to prevent it
	 * from early parking which causes performance drops when thread is unparked. When thread is not
	 * parked and it's idle cycling, the {@link Thread#onSpinWait()} method should be used to signal
	 * the runtime that it is busy-waiting.
	 */
	private static class IdleLoopCounter {

		int max;
		int counter;

		IdleLoopCounter(final int max) {
			this.max = max;
		}

		void reset() {
			counter = 0;
		}

		/**
		 * @return True if {@link ActorThread} should be parked or false otherwise.
		 */
		boolean shouldBeParked() {

			final int c = counter++;
			final int t = -((c - max) >> 31);
			counter *= t;

			return t == 0;
		}
	}

	/**
	 * Padded volatile boolean.
	 */
	@SuppressWarnings("unused")
	private final static class VolatileBoolean { // header 12b

		public volatile boolean value = false; // 13b

		VolatileBoolean(final boolean value) {
			this.value = value;
		}

		public volatile byte b010, b011, b012; // 16b
		public volatile byte b020, b021, b022, b023, b024, b025, b026, b027; // 24b
		public volatile byte b030, b031, b032, b033, b034, b035, b036, b037; // 32b
		public volatile byte b040, b041, b042, b043, b044, b045, b046, b047; // 40b
		public volatile byte b050, b051, b052, b053, b054, b055, b056, b057; // 48b
		public volatile byte b060, b061, b062, b063, b064, b065, b066, b067; // 56b
		public volatile byte b070, b071, b072, b073, b074, b075, b076, b077; // 64b
		public volatile byte b100, b101, b102, b103, b104, b105, b106, b107; // 72b
		public volatile byte b110, b111, b112, b113, b114, b115, b116, b117; // 80b
		public volatile byte b120, b121, b122, b123, b124, b125, b126, b127; // 88b
		public volatile byte b130, b131, b132, b133, b134, b135, b136, b137; // 96b
		public volatile byte b140, b141, b142, b143, b144, b145, b146, b147; // 104b
		public volatile byte b150, b151, b152, b153, b154, b155, b156, b157; // 112b
		public volatile byte b160, b161, b162, b163, b164, b165, b166, b167; // 120b
		public volatile byte b170, b171, b172, b173, b174, b175, b176, b177; // 128b
	}
}
