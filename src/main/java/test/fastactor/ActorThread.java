package test.fastactor;

import static java.util.concurrent.locks.LockSupport.park;
import static java.util.concurrent.locks.LockSupport.unpark;
import static test.fastactor.ActorCell.DeliveryStatus.REJECTED;
import static test.fastactor.ActorCell.ProcessingStatus.COMPLETE;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;

import org.jctools.maps.NonBlockingHashMapLong;
import org.jctools.queues.MpscLinkedQueue;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;


public class ActorThread extends Thread {

	/**
	 * Mapping between cell {@link UUID} and corresponding {@link ActorCell} instance.
	 */
	final NonBlockingHashMapLong<ActorCell<? extends Actor<?>, ?>> dockedCells = new NonBlockingHashMapLong<>();

	/**
	 * The active cells are the ones which have at least one message in the inbox. This map is not
	 * thread-safe, so please do not use outside the {@link ActorThread} it's referenced on.
	 */
	final Long2ObjectOpenHashMap<ActorCell<? extends Actor<?>, ?>> active = new Long2ObjectOpenHashMap<>();

	/**
	 * {@link Runnable}s which will be run after this {@link Thread} is completed.
	 */
	final Deque<Runnable> terminators = new ArrayDeque<>(2);

	/**
	 * Queue to store messages from cells docked on this thread (internal communication).
	 */
	final Queue<Envelope<?>> internalQueue = new LinkedList<>();

	/**
	 * Queue to store messages from cells docked on other threads (interthread communication).
	 */
	final MpscLinkedQueue<Envelope<?>> externalQueue = new MpscLinkedQueue<>();

	final ActorSystem system;

	/**
	 * The {@link ActorThreadPool} stores {@link ActorThread} instances in the list. This is a
	 * positional index of this {@link ActorThread} in this list.
	 */
	final int index;

	/**
	 * How many messages should be processed by a single actor before moving to the next one.
	 */
	final int throughput;

	ActorThread(final ActorSystem system, final String name, final int index) {
		super(getCurrentThreadGroup(), name);
		this.system = system;
		this.index = index;
		this.throughput = system.throughput;
	}

	private static ThreadGroup getCurrentThreadGroup() {
		return Optional
			.ofNullable(System.getSecurityManager())
			.map(SecurityManager::getThreadGroup)
			.orElse(Thread.currentThread().getThreadGroup());
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

		final var queue = new LinkedList<Envelope<?>>();

		while (!isInterrupted()) {

			// move external messages to the temporary queue to avoid contention
			// move internal messages to the temporary queue to avoid concurrent modification

			externalQueue.drain(queue::offer);
			internalQueue.forEach(queue::offer);
			internalQueue.clear();

			deliver(queue);

			queue.clear();

			processActiveCells();

			if (active.isEmpty()) {
				park(this);
			}
		}
	}

	private void deliver(final Queue<Envelope<?>> queue) {
		for (final var envelope : queue) {
			deliver(envelope, active.computeIfAbsent(envelope.target, this::find));
		}
	}

	private void deliver(final Envelope<?> envelope, final ActorCell<?, ?> target) {
		if (target == null || target.deliver(envelope) == REJECTED) {
			if (envelope.message instanceof Directive) {
				((Directive) envelope.message).rejected();
			} else {
				system.forwardToDeathLetter(envelope);
			}
		}
	}

	/**
	 * Iterates over the active cells and process up to {@link #throughput} messages. When inbox is
	 * empty after processing completion, the cell becomes inactive and can be removed from the
	 * active cells map.
	 */
	private void processActiveCells() {

		final var iterator = active.values().iterator();

		while (iterator.hasNext()) {

			final var cell = iterator.next();
			final var status = cell.process(throughput);

			if (status == COMPLETE) {
				iterator.remove();
			} else {
				// more iterations required
			}
		}
	}

	@SuppressWarnings("rawtypes")
	private ActorCell find(final long uuid) {
		return dockedCells.get(uuid);
	}

	public ActorThread withTerminator(final Runnable terminator) {
		terminators.add(terminator);
		return this;
	}

	public void dock(final ActorCell<? extends Actor<?>, ?> cell) {

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
	 * @param id the {@link ActorCell} ID
	 * @return The removed {@link ActorCell} or null when no cell with a given ID was docked here
	 */
	public ActorCell<? extends Actor<?>, ?> undock(final long id) {
		return dockedCells.remove(id);
	}

	/**
	 * Deposit envelope with a message into the queue. This method will wake up the
	 * {@link ActorThread} if it was parked.
	 *
	 * @param envelope the envelope with message
	 */
	public void deposit(final Envelope<?> envelope) {
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
	private void deposit(final Envelope<?> envelope, final Queue<Envelope<?>> queue) {
		wakeUpWhen(queue.offer(envelope));
	}

	/**
	 * Wake up (unpark) the {@link ActorThread} if queues were modified.
	 *
	 * @param modified
	 */
	private void wakeUpWhen(final boolean modified) {
		if (modified) {
			unpark(this);
		}
	}

	private void onComplete() {
		terminators.forEach(Runnable::run);
	}
}
