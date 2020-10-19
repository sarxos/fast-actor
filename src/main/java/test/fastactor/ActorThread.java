package test.fastactor;

import static java.util.concurrent.locks.LockSupport.park;
import static java.util.concurrent.locks.LockSupport.unpark;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jctools.queues.MpscLinkedQueue;


public class ActorThread extends Thread {

	/**
	 * Mapping between cell {@link UUID} and corresponding {@link ActorCell} instance.
	 */
	final Map<UUID, ActorCell<? extends Actor<?>, ?>> cells = new ConcurrentHashMap<>();

	/**
	 * {@link Runnable}s which will be run after this {@link Thread} is completed.
	 */
	final Deque<Runnable> terminators = new ArrayDeque<>(2);

	/**
	 * Queue to store messages from cells docked on this thread (internal communication).
	 */
	final Queue<Envelope<?>> internalInbox = new LinkedList<>();

	/**
	 * Queue to store messages from cells docked on other threads (interthread communication).
	 */
	final Queue<Envelope<?>> externalInbox = new MpscLinkedQueue<>();

	/**
	 * Fast L1 queue for bulk messages processing. Messages are not processed directly from inner
	 * and outer queues, but are first moved to this L1 queue, and then processed in bulk.
	 */
	final Queue<Envelope<?>> inbox;

	final ActorSystem system;

	/**
	 * The {@link ActorThreadPool} stores {@link ActorThread} instances in the list. This is a
	 * positional index of this {@link ActorThread} in this list.
	 */
	final int index;

	public ActorThread(final ActorSystem system, final String name, final int index) {
		super(getCurrentThreadGroup(), name);
		this.system = system;
		this.index = index;
		this.inbox = new ArrayDeque<>(system.throughput * 2);
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

		while (!isInterrupted()) {

			final boolean hasMoreExternalItems = transfer(system.throughput, externalInbox, inbox);
			final boolean hasMoreInternalItems = transfer(system.throughput, internalInbox, inbox);

			drain(inbox);

			final boolean inboxIsEmpty = !(hasMoreInternalItems || hasMoreExternalItems);

			if (inboxIsEmpty) {
				park(this);
			}
		}
	}

	private void drain(final Queue<Envelope<?>> inbox) {

		if (inbox.isEmpty()) {
			return;
		}

		inbox.forEach(this::handle);
		inbox.clear();
	}

	private <A, M> void handle(final Envelope<M> envelope) {

		final ActorCell<? extends Actor<M>, M> cell = findCellFor(envelope);

		if (cell != null) {
			cell.invokeReceive(envelope);
		} else {
			// TODO send to death letter
		}
	}

	@SuppressWarnings("unchecked")
	private <M> ActorCell<? extends Actor<M>, M> findCellFor(final Envelope<M> envelope) {
		return (ActorCell<? extends Actor<M>, M>) cells.get(envelope.target);
	}

	/**
	 * @param n how many messages to transfer from one queue to another
	 * @param source the queue to transfer messages from
	 * @param target the queue to transfer messages to
	 * @return True if there are (most likely) messages left in source queue, false otherwise.
	 */
	private <M> boolean transfer(final int n, final Queue<M> source, final Queue<M> target) {

		for (int i = 0; i < n; i++) {

			final M item = source.poll();

			if (item == null) {
				return false;
			} else {
				target.offer(item);
			}
		}

		return true;
	}

	private void terminated() {
		terminators.forEach(Runnable::run);
	}

	public ActorThread withTerminator(final Runnable terminator) {
		terminators.add(terminator);
		return this;
	}

	public <M> void dock(final ActorCell<? extends Actor<M>, M> cell) {

		final UUID uuid = cell.getUuid();
		final boolean overwritten = cells.putIfAbsent(uuid, cell) != null;

		if (overwritten) {
			throw new IllegalStateException("Cell with ID " + uuid + " already docked on thread " + getName());
		}
	}

	void deliverInternal(final Envelope<?> envelope) {
		deliver(envelope, internalInbox);
	}

	void deliverExternal(final Envelope<?> envelope) {
		deliver(envelope, externalInbox);
	}

	private void deliver(final Envelope<?> envelope, final Queue<Envelope<?>> inbox) {
		wakeUpWhen(inbox.add(envelope));
	}

	private void wakeUpWhen(final boolean modified) {
		if (modified) {
			unpark(this);
		}
	}
}
