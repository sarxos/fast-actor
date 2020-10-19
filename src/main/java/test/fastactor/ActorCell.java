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

	private final Deque<Consumer<M>> behaviours = new ArrayDeque<>(0);
	private final ActorSystem system;

	final UUID uuid = new UUID(RANDOM.nextLong(), RANDOM.nextLong());
	final ActorRef<A, M> self;

	private Holder holder;
	private ActorRef<? extends Actor<?>, ?> sender;

	public ActorCell(final ActorSystem system, final Props<A> props) {
		this.system = system;
		this.self = new ActorRef<>(system, uuid);
		this.holder = new Holder(props);
	}

	@SuppressWarnings("unchecked")
	static <M> ActorContext<M> getActiveContext() {
		return (ActorContext<M>) CONTEXT.get();
	}

	public void invokeReceive(final Envelope<M> envelope) {

		this.sender = new ActorRef<>(system, envelope.sender);

		Optional
			.ofNullable(behaviours.peek())
			.orElse(holder.actor()::receive)
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
	public ActorRef<A, M> self() {
		return self;
	}

	@Override
	public ActorRef<? extends Actor<?>, ?> sender() {
		return sender;
	}

	@Override
	public ActorSystem system() {
		return system;
	}

	public String getThreadPoolName() {
		return holder.getProps().getThreadPoolName();
	}

	public UUID getUuid() {
		return uuid;
	}

	class Holder {

		private final Props<A> props;
		private A actor;

		public Holder(final Props<A> props) {
			this.props = props;

		}

		public Props<A> getProps() {
			return props;
		}

		public A actor() {
			if (actor == null) {
				CONTEXT.set(ActorCell.this);
				actor = props.newActor();
			}
			return actor;
		}

		public void initialize() {
			this.actor = props.newActor();
		}
	}
}
