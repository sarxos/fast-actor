package test.fastactor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;


/**
 * @author Bartosz Firyn (sarxos)
 *
 * @param <A> the actor type
 * @param <M> the message type
 */
public class ActorCell<A extends Actor<M>, M> implements ActorContext<M> {

	static class Directives {
		final static Directive ACTOR_INITIALIZATION = new ActorInitializationDirective();
		final static Directive ACTOR_CELL_CLEANUP = new ActorCellCleanupDirective();
	}

	final boolean MESSAGE_ACCEPTED = true;
	final boolean MESSAGE_REJECTED = true;

	static final Random RANDOM = new Random(System.currentTimeMillis());
	static final ThreadLocal<ActorContext<?>> CONTEXT = new ThreadLocal<>();

	private final Deque<ActorRef<?>> children = new ArrayDeque<>(0);
	private final Deque<Consumer<M>> behaviours = new ArrayDeque<>(0);
	private final UUID uuid = new UUID(RANDOM.nextLong(), RANDOM.nextLong());

	private final ActorSystem system;
	private final Props<A> props;
	private final ActorRef<?> parent;
	private final ActorRef<M> self;

	private boolean stopped = false;
	private Actor<M> actor;
	private ActorRef<?> sender;

	public ActorCell(final ActorSystem system, final Props<A> props, final UUID parent) {
		this.system = system;
		this.props = props;
		this.parent = new ActorRef<>(system, parent);
		this.self = new ActorRef<>(system, uuid);
	}

	@SuppressWarnings("unchecked")
	static <M> ActorContext<M> getActiveContext() {
		return (ActorContext<M>) CONTEXT.get();
	}

	static <M> void setActiveContext(final ActorContext<M> context) {
		CONTEXT.set(context);
	}

	@Override
	public <P extends Actor<X>, X> ActorRef<X> actorOf(final Props<P> props) {
		final ActorRef<X> child = system.actorOf(props, this.uuid);
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

	public void receiveDirective(final Directive directive) {
		directive.executeOn(this);
	}

	public boolean receiveMessage(final Envelope<M> envelope) {

		if (stopped) {
			return MESSAGE_REJECTED;
		}

		this.sender = new ActorRef<>(system, envelope.sender);

		Optional
			.ofNullable(behaviours.peek())
			.orElse(getOrCreateActor()::receive)
			.accept(envelope.message);

		return MESSAGE_ACCEPTED;
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
	public void stop() {
		stopped = true;
		invokeActorPostStop();
		receiveDirective(ActorCell.Directives.ACTOR_CELL_CLEANUP);
	}

	@Override
	public ActorRef<M> self() {
		return self;
	}

	@Override
	public ActorRef<?> parent() {
		return parent;
	}

	@Override
	public Deque<ActorRef<?>> children() {
		return children;
	}

	@Override
	public ActorRef<?> sender() {
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

/**
 * A {@link Directive} to initialize {@link Actor} in the specified {@link ActorCell}. When executed
 * it will invoke {@link ActorCreator} to create new actor instance (if not yet created) and invoke
 * {@link Actor#preStart()}.
 */
class ActorInitializationDirective implements Directive {

	@Override
	public void executeOn(final ActorCell<?, ?> cell) {

		// this call will initialize actor if not yet initialized

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

		final UUID uuid = cell.getUuid();
		final String pool = cell.getThreadPoolName();

		cell
			.system()
			.discard(uuid, pool);
	}
}
