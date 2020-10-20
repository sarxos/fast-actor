package test.fastactor;

import static java.lang.Integer.MAX_VALUE;
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
import java.util.function.Consumer;

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

	final Queue<Directive> internalDirectives = new LinkedList<>();
	final Queue<Directive> externalDirectives = new MpscLinkedQueue<>();

	/**
	 * Queue to store messages from cells docked on this thread (internal communication).
	 */
	final Queue<Envelope<?>> internalInbox = new LinkedList<>();

	/**
	 * Queue to store messages from cells docked on other threads (interthread communication).
	 */
	final Queue<Envelope<?>> externalInbox = new MpscLinkedQueue<>();

	final ActorSystem system;

	/**
	 * The {@link ActorThreadPool} stores {@link ActorThread} instances in the list. This is a
	 * positional index of this {@link ActorThread} in this list.
	 */
	final int index;

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

		final Queue<Directive> directives = new LinkedList<>();
		final Queue<Envelope<?>> inbox = new ArrayDeque<>(throughput * 2);

		while (!isInterrupted()) {

			final boolean moreExternalDirectives = transfer(MAX_VALUE, externalDirectives, directives);
			final boolean moreInternalDirectives = transfer(MAX_VALUE, internalDirectives, directives);

			drainDirectives(directives);

			final boolean moreExternalItems = transfer(throughput, externalInbox, inbox);
			final boolean moreInternalItems = transfer(throughput, internalInbox, inbox);

			drainInbox(inbox);

			final boolean moreElementsToProcess = false
				|| moreExternalDirectives
				|| moreInternalDirectives
				|| moreExternalItems
				|| moreInternalItems;

			if (moreElementsToProcess) {
				continue;
			}

			park(this);
		}
	}

	private void drainDirectives(final Queue<Directive> directives) {
		drain(directives, this::handleDirective);
	}

	private void drainInbox(final Queue<Envelope<?>> inbox) {
		drain(inbox, this::handleMessage);
	}

	private <X> void drain(final Queue<X> queue, final Consumer<X> handler) {

		if (queue.isEmpty()) {
			return;
		}

		queue.forEach(handler);
		queue.clear();
	}

	private void handleDirective(final Directive envelope) {
		findCellFor(envelope).invokeDirective(envelope);
	}

	@SuppressWarnings("unchecked")
	private void handleMessage(final Envelope<?> envelope) {
		findCellFor(envelope).invokeReceive(envelope);
	}

	@SuppressWarnings("rawtypes")
	private ActorCell findCellFor(final Deliverable deliverable) {
		return cells.get(deliverable.getTarget());
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

		deliverDirective(new ActorInitializationDirective(uuid));
	}

	public void deliverDirective(final Directive directive) {
		if (this == currentThread()) {
			depositDirective(directive, internalDirectives);
		} else {
			depositDirective(directive, externalDirectives);
		}
	}

	private <M> void depositDirective(final Directive directive, final Queue<Directive> directives) {
		wakeUpWhen(directives.add(directive));
	}

	public void deliverMessage(final Envelope<?> envelope) {
		if (this == currentThread()) {
			depositMessage(envelope, internalInbox);
		} else {
			depositMessage(envelope, externalInbox);
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
