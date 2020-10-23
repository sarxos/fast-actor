package test.fastactor;

import static test.fastactor.ActorCell.DeliveryStatus.ACCEPTED;
import static test.fastactor.ActorCell.DeliveryStatus.REJECTED;
import static test.fastactor.ActorCell.Directives.STOP;
import static test.fastactor.ActorCell.ProcessingStatus.COMPLETE;
import static test.fastactor.ActorCell.ProcessingStatus.CONTINUE;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import test.fastactor.Directive.ExecutionMode;
import test.fastactor.dsl.Base;


/**
 * @author Bartosz Firyn (sarxos)
 *
 * @param <A> the actor type
 * @param <M> the message type
 */
public class ActorCell<A extends Actor<M>, M> implements ActorContext<M> {

	static class Directives {
		final static Directive INIT = new ActorInitializationDirective();
		final static Directive STOP = new ActorStopDirective();
		final static Directive CLEANUP = new ActorCellCleanupDirective();
	}

	static final Random RANDOM = new Random(System.currentTimeMillis());
	static final ThreadLocal<ActorContext<?>> CONTEXT = new ThreadLocal<>();

	private final Deque<ActorRef> children = new ArrayDeque<>(0);
	private final Deque<Consumer<M>> behaviours = new ArrayDeque<>(0);
	private final UUID uuid = new UUID(RANDOM.nextLong(), RANDOM.nextLong());

	private final Queue<Envelope<?>> inbox = new LinkedList<>();

	private final ActorSystem system;
	private final Props<A> props;
	private final ActorRef parent;
	private final ActorRef self;

	private boolean stopped = false;
	private Actor<M> actor;
	private ActorRef sender;

	public ActorCell(final ActorSystem system, final Props<A> props, final UUID parent) {
		this.system = system;
		this.props = props;
		this.parent = new ActorRef(system, parent);
		this.self = new ActorRef(system, uuid);
	}

	@SuppressWarnings("unchecked")
	static <M> ActorContext<M> getActiveContext() {
		return (ActorContext<M>) CONTEXT.get();
	}

	static <M> void setActiveContext(final ActorContext<M> context) {
		CONTEXT.set(context);
	}

	@Override
	public <P extends Actor<X>, X> ActorRef actorOf(final Props<P> props) {
		final ActorRef child = system.actorOf(props, this.uuid);
		children.add(child);
		return child;
	}

	Actor<M> getOrCreateActor() {
		if (actor == null) {
			invokeActorConstructor();
			invokeActorPreStart();
		}
		return actor;
	}

	boolean isActorInitialized() {
		return actor != null;
	}

	private void invokeActorConstructor() {
		setActiveContext(this);
		try {
			actor = props.newActor();
		} finally {
			setActiveContext(null);
		}
	}

	private void invokeActorPreStart() {
		if (actor != null) {
			actor.preStart();
		}
	}

	private void invokeActorPostStop() {
		if (actor != null) {
			actor.postStop();
		}
	}

	static enum DeliveryStatus {
		ACCEPTED,
		REJECTED,
	}

	public DeliveryStatus deliver(final Envelope<?> envelope) {
		return withSender(envelope.sender, () -> {
			if (envelope.message instanceof Directive) {
				return receiveDirective(envelope);
			} else {
				return receiveMessage(envelope);
			}
		});
	}

	private DeliveryStatus receiveDirective(final Envelope<?> envelope) {

		final var directive = envelope.asDirective().message;

		if (directive.mode() == ExecutionMode.RUN_IMMEDIATELY) {
			directive.executeOn(this);
		} else {
			inbox.offer(envelope);
		}

		return ACCEPTED;
	}

	private DeliveryStatus receiveMessage(final Envelope<?> envelope) {

		if (stopped) {
			return REJECTED;
		}

		if (inbox.offer(envelope)) {
			return ACCEPTED;
		}

		return REJECTED;
	}

	static enum ProcessingStatus {
		COMPLETE,
		CONTINUE,
	}

	/**
	 * @param throughput how many items in inbox should be processsed
	 * @return Return true if all items in inbox has been processed, false otherwise
	 */
	public ProcessingStatus process(final int throughput) {

		if (stopped) {
			return COMPLETE;
		}

		for (int i = 0; i < throughput; i++) {
			if (processInboxItem(inbox.poll())) {
				return COMPLETE;
			}
		}

		return CONTINUE;
	}

	@SuppressWarnings("unchecked")
	private boolean processInboxItem(final Envelope<?> envelope) {

		if (envelope == null) {
			return true; // last message
		}

		setCurrentSender(envelope);

		if (envelope.message instanceof Directive) {
			envelope.asDirective().message.executeOn(this);
		} else {
			Optional
				.ofNullable(behaviours.peek())
				.orElse(getOrCreateActor()::receive)
				.accept((M) envelope.message);
		}

		return false;
	}

	private void setCurrentSender(final Envelope<?> envelope) {
		this.sender = new ActorRef(system, envelope.sender);
	}

	@Override
	public void become(final Consumer<M> behaviour) {
		behaviours.push(behaviour);
	}

	@Override
	public void unbecome() {
		behaviours.pop();
	}

	public void unbecomeAll() {
		behaviours.clear();
	}

	/**
	 * Mark cell as stopped so it won't accept more messages. Any message delivered to this cell in
	 * the meantime (if any) will be rejected.
	 */
	@Override
	public void stop() {

		stopped = true;
		invokeActorPostStop();
		inbox.clear();

		if (hasChildren()) {
			system.actorOf(Props.create(() -> new ActorStopCoordinator(self(), children())));
		} else {
			system.tell(STOP, self, self);
		}
	}

	private <T> T withSender(final UUID uuid, final Supplier<T> run) {
		this.sender = new ActorRef(system, uuid);
		try {
			return run.get();
		} finally {
			this.sender = null;
		}
	}

	/**
	 * @return True if actor has children, false otherwise
	 */
	private boolean hasChildren() {
		return !children.isEmpty();
	}

	@Override
	public ActorRef self() {
		return self;
	}

	@Override
	public ActorRef parent() {
		return parent;
	}

	@Override
	public Deque<ActorRef> children() {
		return children;
	}

	@Override
	public ActorRef sender() {
		return sender;
	}

	@Override
	public ActorSystem system() {
		return system;
	}

	public UUID getUuid() {
		return uuid;
	}

	public String getThreadPoolName() {
		return props.getThreadPoolName();
	}
}

class ActorStopCoordinator extends Actor<ActorStopDirective.Ack> implements Base {

	private static final Directive CLEANUP = ActorCell.Directives.CLEANUP;

	private final ActorRef parent;
	private final Queue<ActorRef> children;
	private int howManyAlive;

	public ActorStopCoordinator(final ActorRef parent, final Queue<ActorRef> children) {
		this.parent = parent;
		this.children = children;
		this.howManyAlive = children.size();
	}

	@Override
	public void preStart() {
		children.forEach(this::tellActorToStop);
	}

	private void tellActorToStop(final ActorRef child) {
		tell(STOP, child);
	}

	@Override
	public void receive(final ActorStopDirective.Ack ack) {
		if (allChildrenDied()) {
			tell(CLEANUP, parent, parent);
			stop();
		}
	}

	private boolean allChildrenDied() {
		return --howManyAlive == 0;
	}
}

class ActorStopDirective implements Directive {

	@Override
	public void executeOn(final ActorCell<?, ?> cell) {

		final var sender = cell.sender();
		final var self = cell.self();

		// stop the cell and confirm to the sender that we indeed stopped

		cell.stop();
		sender.tell(Ack.INSTANCE, self);
	}

	static enum Ack {
		INSTANCE
	}
}

/**
 * A {@link Directive} to initialize {@link Actor} in the specified {@link ActorCell}. When executed
 * it will invoke {@link ActorCreator} to create new actor instance (if not yet created) and invoke
 * {@link Actor#preStart()}.
 */
class ActorInitializationDirective implements Directive {

	@Override
	public void executeOn(final ActorCell<?, ?> cell) {
		cell.getOrCreateActor();
	}
}

/**
 * A {@link Directive} to cleanup specified {@link ActorCell}. When executed it will remove the cell
 * from the system and the thread pool where cell is docked. Make sure to use this directive
 * whenever cell is no longer needed and can be removed.
 */
class ActorCellCleanupDirective implements Directive {

	@Override
	public void executeOn(final ActorCell<?, ?> cell) {
		final var system = cell.system();
		final var uuid = cell.getUuid();
		system.discard(uuid);
	}
}
