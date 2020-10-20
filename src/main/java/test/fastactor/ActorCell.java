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

	static final Random RANDOM = new Random(System.currentTimeMillis());
	static final ThreadLocal<ActorContext<?>> CONTEXT = new ThreadLocal<>();

	private final Deque<ActorRef<?>> children = new ArrayDeque<>(0);
	private final Deque<Consumer<M>> behaviours = new ArrayDeque<>(0);
	private final UUID uuid = new UUID(RANDOM.nextLong(), RANDOM.nextLong());

	private final ActorSystem system;
	private final Props<A> props;
	private final ActorRef<?> parent;
	private final ActorRef<M> self;

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

	void invokeActorConstructor() {
		setActiveContext(this);
		try {
			actor = props.newActor();
		} finally {
			setActiveContext(null);
		}
	}

	void invokeActorPreStart() {
		if (actor != null) {
			actor.preStart();
		}
	}

	void invokeActorPostStop() {
		if (actor != null) {
			actor.postStop();
		}
	}

	public void invokeDirective(final Directive directive) {
		directive.executeOn(this);
	}

	public void invokeReceive(final Envelope<M> envelope) {

		this.sender = new ActorRef<>(system, envelope.sender);

		Optional
			.ofNullable(behaviours.peek())
			.orElse(getOrCreateActor()::receive)
			.accept(envelope.message);
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
}

class ActorInitializationDirective implements Directive {

	private final UUID target;

	public ActorInitializationDirective(UUID target) {
		this.target = target;
	}

	@Override
	public void executeOn(final ActorCell<?, ?> cell) {
		cell.getOrCreateActor(); // will initialize actor if not yet initialized
	}

	@Override
	public UUID getTarget() {
		return target;
	}
}
