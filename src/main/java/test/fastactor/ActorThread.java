package test.fastactor;

import static java.util.concurrent.locks.LockSupport.park;
import static java.util.concurrent.locks.LockSupport.unpark;
import static test.fastactor.ActorCell.DeliveryStatus.REJECTED;
import static test.fastactor.ActorCell.ProcessingStatus.COMPLETE;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jctools.queues.MpscLinkedQueue;


public class ActorThread extends Thread {

	private static final UUID ZERO_UUID = new UUID(0, 0);

	/**
	 * Mapping between cell {@link UUID} and corresponding {@link ActorCell} instance.
	 */
	final Map<UUID, ActorCell<? extends Actor<?>, ?>> dockedCells = new ConcurrentHashMap<>();

	/**
	 * The active cells are the ones which have at least one message in the inbox.
	 */
	final Map<UUID, ActorCell<? extends Actor<?>, ?>> activeCells = new HashMap<>();

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
			process();
		} finally {
			terminated();
		}
	}

	private void process() {

		final var queue = new LinkedList<Envelope<?>>();

		while (!isInterrupted()) {

			// move external messages to the temporary queue to avoid contention
			// move internal messages to the temporary queue to avoid concurrent modification

			externalQueue.drain(queue::offer);

			internalQueue.forEach(queue::offer);
			internalQueue.clear();

			deliverMessagesToCells(queue);

			queue.clear();

			processActiveCells();

			if (activeCells.isEmpty()) {
				park(this);
			}
		}
	}

	private void deliverMessagesToCells(final Queue<Envelope<?>> queue) {
		for (final var envelope : queue) {
			final var target = activeCells.computeIfAbsent(envelope.target, this::findCellFor);
			if (target == null || target.deliver(envelope) == REJECTED) {
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

		final var iterator = activeCells.values().iterator();

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
	private ActorCell findCellFor(final UUID uuid) {
		return dockedCells.get(uuid);
	}

	public ActorThread withTerminator(final Runnable terminator) {
		terminators.add(terminator);
		return this;
	}

	public void dock(final ActorCell<? extends Actor<?>, ?> cell) {

		final var uuid = cell.getUuid();
		final var overwritten = dockedCells.putIfAbsent(uuid, cell) != null;

		if (overwritten) {
			throw new IllegalStateException("Cell with ID " + uuid + " already docked on thread " + getName());
		}

		final var init = ActorCell.Directives.START_CELL;
		final var envelope = new Envelope<>(init, ZERO_UUID, uuid);

		deliverMessage(envelope);
	}

	public void discard(final UUID uuid) {
		dockedCells.remove(uuid);
	}

	public void deliverMessage(final Envelope<?> envelope) {
		if (this == currentThread()) {
			depositMessage(envelope, internalQueue);
		} else {
			depositMessage(envelope, externalQueue);
		}
	}

	private void depositMessage(final Envelope<?> envelope, final Queue<Envelope<?>> inbox) {
		wakeUpWhen(inbox.add(envelope));
	}

	private void wakeUpWhen(final boolean modified) {
		if (modified) {
			unpark(this);
		}
	}

	private void terminated() {
		terminators.forEach(Runnable::run);
	}
}
