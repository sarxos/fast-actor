package test.fastactor;

import static test.fastactor.ActorRef.noSender;
import static test.fastactor.ActorThreadPool.DEFAULT_THREAD_POOL_NAME;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.jctools.maps.NonBlockingHashMap;
import org.jctools.maps.NonBlockingHashMapLong;

import test.fastactor.ActorThreadPool.CellDockingInfo;
import test.fastactor.AskRouter.Ask;
import test.fastactor.DeadLetters.DeadLetter;


public class ActorSystem {

	/**
	 * Non-existing UUID. Used by {@link ActorRef#noSender()}.
	 */
	public static final long ZERO_UUID = 0;

	private static final int DEFAULT_THROUGHPUT = 10;

	final NonBlockingHashMap<String, ActorThreadPool> pools = new NonBlockingHashMap<>(1);
	final NonBlockingHashMapLong<CellDockingInfo> cells = new NonBlockingHashMapLong<>();

	final String name;
	final int throughput;

	final InternalActors internal;

	public ActorSystem(final String name, final int throughput) {

		this.name = name;
		this.throughput = throughput;

		addPool(new ActorThreadPool(this, DEFAULT_THREAD_POOL_NAME, 16));

		this.internal = new InternalActors();
	}

	public static ActorSystem create(final String name) {
		final ActorSystem system = new ActorSystem(name, DEFAULT_THROUGHPUT);
		return system;
	}

	public String getName() {
		return name;
	}

	public boolean addPool(final ActorThreadPool pool) {
		return pools.putIfAbsent(pool.getName(), pool) == null;
	}

	/**
	 * Public actor creation facility. System consumers should use this method to spawn new actors.
	 *
	 * @param <A> the actor class.
	 * @param <M> the expected message class
	 * @param props the actor {@link Props}
	 * @return New {@link ActorRef} which should be used to communicate with the actor
	 */
	public <A extends Actor> ActorRef actorOf(final Props<A> props) {
		return actorOf(props, internal.user.uuid);
	}

	/**
	 * Return the {@link ActorRef} if the actor with a given uuid exists in the actor system,
	 * otherwise return null.
	 *
	 * @param uuid the actor uuid
	 * @return {@link ActorRef} if actor exists in the system, or null otherwise
	 */
	public ActorRef find(final long uuid) {
		if (cells.containsKey(uuid)) {
			return new ActorRef(this, uuid);
		} else {
			return null;
		}
	}

	<A extends Actor> ActorRef actorOf(final Props<A> props, final long parent) {

		final var pool = getPoolFor(props).orElseThrow(poolNotFoundError(props));

		do {

			final var cell = new ActorCell<A>(this, props, parent);
			final var uuid = cell.uuid();

			final var info = pool.dock(cell);

			if (cells.putIfAbsent(uuid, info) != null) {
				pool.discard(uuid, info.threadIndex);
			} else {
				return cell.setup();
			}

		} while (true);
	}

	void discard(final long uuid) {

		final var info = getDockingInfoFor(uuid).orElseThrow(cellNotFoundError(uuid));
		final var threadIndex = info.threadIndex;
		final var threadPool = info.pool;

		threadPool.discard(uuid, threadIndex);

		cells.remove(uuid);
	}

	/**
	 * Send and forget. The {@link ActorRef#noSender()} is used as a sender so if the target actor
	 * replies the reply will be delivered to the dead-letters.
	 *
	 * @param message the message
	 * @param target the target {@link ActorRef}
	 */
	public void tell(final Object message, final ActorRef target) {
		tell(message, target, noSender());
	}

	/**
	 * Send and forget.
	 *
	 * @param message the message
	 * @param target the target {@link ActorRef}
	 * @param sender the sender {@link ActorRef}
	 */
	public void tell(final Object message, final ActorRef target, final ActorRef sender) {
		tell(message, target.uuid, sender.uuid);
	}

	/**
	 * Send and forget.
	 *
	 * @param message the message
	 * @param target the target uid
	 * @param sender the sender uid
	 */
	void tell(final Object message, final long target, final long sender) {

		final var envelope = new Envelope(message, sender, target);
		final var info = cells.get(target);

		if (info == null) {
			forwardToDeadLetters(envelope);
		} else {
			final var threadPool = info.getPool();
			final var threadIndex = info.threadIndex;
			threadPool.deposit(envelope, threadIndex);
		}
	}

	public <R> CompletionStage<R> ask(final Object message, final ActorRef target) {

		final var ask = new Ask<R>(message, target);
		final var ref = refForAskRouter();

		tell(ask, ref);

		return ask.completion;
	}

	public void forward(final Envelope envelope, final ActorRef target) {
		forward(envelope, target.uuid);
	}

	/**
	 * Forward message to target.
	 *
	 * @param envelope the {@link Envelope} containing message
	 * @param target a new target uid
	 */
	void forward(final Envelope envelope, final long target) {
		final var message = envelope.message;
		final var sender = envelope.sender;
		tell(message, target, sender);
	}

	void forwardToDeadLetters(final Envelope envelope) {

		final var message = envelope.message;
		final var target = new ActorRef(this, envelope.target);
		final var sender = new ActorRef(this, envelope.sender);
		final var deadLetter = new DeadLetter(message, target, sender);
		final var deadLetters = internal.deadLetters;

		deadLetters.tell(deadLetter);
	}

	public void emit(final Object event, final ActorRef emitter) {
		final var msg = new EventBus.Event(event, emitter);
		final var bus = refForEventBus();
		tell(msg, bus, emitter);
	}

	/**
	 * Stops the actor pointed by the {@link ActorRef} provided in the argument. This is
	 * asynchronous operation and therefore actor may be still alive when this method completes.
	 *
	 * @param target the target to be stopped
	 */
	public void stop(final ActorRef target) {
		tell(InternalDirectives.STOP, target, noSender());
	}

	private Optional<CellDockingInfo> getDockingInfoFor(final long uuid) {
		return Optional.ofNullable(cells.get(uuid));
	}

	private Optional<ActorThreadPool> getPoolFor(final Props<? extends Actor> props) {
		return getPoolFor(props.getThreadPoolName());
	}

	private Optional<ActorThreadPool> getPoolFor(final String poolName) {
		return Optional.ofNullable(pools.get(poolName));
	}

	private static Supplier<RuntimeException> poolNotFoundError(final Props<? extends Actor> props) {
		return poolNotFoundError(props.getThreadPoolName());
	}

	private static Supplier<RuntimeException> poolNotFoundError(final String poolName) {
		return () -> new IllegalStateException("Thread pool with name " + poolName + " has not been found in the system");
	}

	private static Supplier<RuntimeException> cellNotFoundError(final long uuid) {
		return () -> new IllegalStateException("Cell with UUID " + uuid + " has not been found in the system");
	}

	public ActorRef refForAskRouter() {
		return internal.askRouter;
	}

	public ActorRef refForDeathLetters() {
		return internal.deadLetters;
	}

	public ActorRef refForEventBus() {
		return internal.eventBus;
	}

	class InternalActors {
		final ActorRef root = actorOf(Props.create(RootActor::new), ZERO_UUID);
		final ActorRef user = actorOf(Props.create(UserActor::new), root.uuid);
		final ActorRef system = actorOf(Props.create(SystemActor::new), root.uuid);
		final ActorRef askRouter = actorOf(Props.create(AskRouter::new), root.uuid);
		final ActorRef deadLetters = actorOf(Props.create(DeadLettersActor::new), root.uuid);
		final ActorRef eventBus = actorOf(Props.create(EventBusActor::new), system.uuid);
	}
}

class SystemActor extends Actor {
}

class UserActor extends Actor {
}

class RootActor extends Actor {
}
