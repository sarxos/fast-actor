package test.fastactor;

import static test.fastactor.ActorThreadPool.DEFAULT_THREAD_POOL_NAME;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.jctools.maps.NonBlockingHashMap;
import org.jctools.maps.NonBlockingHashMapLong;

import test.fastactor.ActorThreadPool.ActorCellInfo;
import test.fastactor.AskRouter.Ask;
import test.fastactor.DeadLetters.DeadLetter;


public class ActorSystem {

	/**
	 * Non-existing UUID.
	 */
	public static final long ZERO_UUID = 0;

	/**
	 * Default throughput (up to how many messages to process from the same actor before switching
	 * to the next one).
	 */
	public static final int DEFAULT_THROUGHPUT = 100;

	final NonBlockingHashMap<String, ActorThreadPool> pools = new NonBlockingHashMap<>(1);
	final NonBlockingHashMapLong<ActorCellInfo> cells = new NonBlockingHashMapLong<>();
	final ActorRef zeroRef = new ActorRef(this, ZERO_UUID);
	final AtomicLong uuidGenerator = new AtomicLong(0);

	final String name;
	final int throughput;
	final InternalActors internal;

	public ActorSystem(final String name, final int throughput) {

		this.name = name;
		this.throughput = throughput;

		addPool(new ActorThreadPool(this, DEFAULT_THREAD_POOL_NAME));

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
		final var info = pool.prepareCellInfo(props);
		final var cell = new ActorCell<A>(this, props, info, parent);
		final var uuid = info.uuid;

		info.thread.dock(cell);

		if (cells.put(uuid, info) == null) {
			cell.setup();
		} else {
			throw new IllegalStateException("Cell wil ID " + uuid + " already exists in the system");
		}

		return cell.self();
	}

	void discard(final long uuid) {

		final var info = getDockingInfoFor(uuid).orElseThrow(cellNotFoundError(uuid));
		final var thread = info.thread;

		thread.remove(uuid);
		cells.remove(uuid);
	}

	long generateNextUuid() {
		return uuidGenerator.incrementAndGet();
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

		final var envelope = new Envelope(message, target, sender);
		final var cellInfo = cells.get(target.uuid);

		if (cellInfo == null) {
			forwardToDeadLetters(envelope);
		} else {
			cellInfo.thread.deposit(envelope);
		}
	}

	public <R> CompletionStage<R> ask(final Object message, final ActorRef target) {

		final var ask = new Ask<R>(message, target);
		final var ref = refForAskRouter();

		tell(ask, ref);

		return ask.completion;
	}

	/**
	 * Forward message to target.
	 *
	 * @param envelope the {@link Envelope} containing message
	 * @param target a new target uid
	 */
	void forward(final Envelope envelope, final ActorRef target) {
		final var message = envelope.message;
		final var sender = envelope.sender;
		tell(message, target, sender);
	}

	void forwardToDeadLetters(final Envelope envelope) {

		final var message = envelope.message;
		final var target = envelope.target;
		final var sender = envelope.sender;
		final var deadLetter = new DeadLetter(message, target, sender);
		final var deadLetters = internal.deadLetters;

		deadLetters.tell(deadLetter);
	}

	public void emitEvent(final Object event) {
		emitEvent(event, noSender());
	}

	public void emitEvent(final Object event, final ActorRef emitter) {
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

	private Optional<ActorCellInfo> getDockingInfoFor(final long uuid) {
		return Optional.ofNullable(cells.get(uuid));
	}

	private Optional<ActorThreadPool> getPoolFor(final Props<? extends Actor> props) {
		return getPoolFor(props.getThreadPool());
	}

	private Optional<ActorThreadPool> getPoolFor(final String poolName) {
		return Optional.ofNullable(pools.get(poolName));
	}

	private static Supplier<RuntimeException> poolNotFoundError(final Props<? extends Actor> props) {
		return poolNotFoundError(props.getThreadPool());
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

	public ActorRef refForDeadLetters() {
		return internal.deadLetters;
	}

	public ActorRef refForEventBus() {
		return internal.eventBus;
	}

	public ActorRef refFor(final long uuid) {
		return new ActorRef(this, uuid);
	}

	public ActorRef noSender() {
		return zeroRef;
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
