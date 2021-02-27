package com.github.sarxos.fastactor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.jctools.maps.NonBlockingHashMapLong;

import com.github.sarxos.fastactor.ActorThreadPool.ActorCellInfo;
import com.github.sarxos.fastactor.AskRouter.Ask;
import com.github.sarxos.fastactor.DeadLetters.DeadLetter;


public class ActorSystem {

	/**
	 * Non-existing UUID.
	 */
	public static final long ZERO_UUID = 0;

	public static final String DEFAULT_THREAD_POOL_NAME = "default-thread-pool";

	final Map<String, ActorThreadPool> pools = new HashMap<>(1);
	final NonBlockingHashMapLong<ActorCellInfo> cells = new NonBlockingHashMapLong<>();
	final ActorRef zero = new ActorRef(this, new ActorCellInfo(null, null, ZERO_UUID));
	final AtomicLong uuidGenerator = new AtomicLong(0);

	final String name;

	final int throughput;
	final int parallelism;
	final Configuration configuration;

	private InternalActors internal;

	public ActorSystem(final String name) {
		this.name = name;
		this.configuration = new Configuration();
		this.parallelism = configuration.getParallelism();
		this.throughput = configuration.getThroughput();
	}

	public static ActorSystem create(final String name) {
		return new ActorSystem(name)
			.withPool(new ActorThreadPool(DEFAULT_THREAD_POOL_NAME))
			.start();
	}

	public ActorSystem start() {

		startPools();
		createInternalActors();

		return this;
	}

	private void startPools() {
		for (var pool : pools.values()) {
			pool.start(this);
		}
	}

	private void createInternalActors() {
		internal = new InternalActors();
	}

	public String getName() {
		return name;
	}

	public ActorSystem withPool(final ActorThreadPool pool) {

		if (pool == null) {
			throw new IllegalArgumentException("Pool must not be null");
		}
		if (pools.putIfAbsent(pool.getName(), pool) != null) {
			throw new IllegalArgumentException("Pool with name " + pool.getName() + " already exists in this actor system");
		}

		return this;
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
		return actorOf(props, internal.user);
	}

	/**
	 * Return the {@link ActorRef} if the actor with a given uuid exists in the actor system,
	 * otherwise return null.
	 *
	 * @param uuid the actor uuid
	 * @return {@link ActorRef} if actor exists in the system, or null otherwise
	 */
	ActorRef find(final long uuid) {
		return Optional
			.ofNullable(cells.get(uuid))
			.map(info -> new ActorRef(this, info))
			.orElse(refForDeadLetters());
	}

	<A extends Actor> ActorRef actorOf(final Props<A> props, final ActorRef parent) {

		final var pool = getPoolFor(props).orElseThrow(poolNotFoundError(props));
		final var info = pool.prepareCellInfo(this, props);
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
		final var dispatcher = target.dispatcher();

		if (dispatcher == null) {
			forwardToDeadLetters(envelope);
		} else {
			dispatcher.deposit(envelope);
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

	public ActorRef noSender() {
		return zero;
	}

	public void shutdown() {
		pools
			.values()
			.stream()
			.map(ActorThreadPool::shutdown)
			.forEach(ActorThreadPool.Shutdown::awaitTermination);
	}

	class InternalActors {
		final ActorRef root = actorOf(Props.create(RootActor::new), zero);
		final ActorRef user = actorOf(Props.create(UserActor::new), root);
		final ActorRef system = actorOf(Props.create(SystemActor::new), root);
		final ActorRef askRouter = actorOf(Props.create(AskRouter::new), root);
		final ActorRef deadLetters = actorOf(Props.create(DeadLettersActor::new), root);
		final ActorRef eventBus = actorOf(Props.create(EventBusActor::new), system);
	}

	public static class Configuration {

		/**
		 * Default throughput (up to how many messages to process from the same actor before
		 * switching to the next one).
		 */
		public static final int DEFAULT_THROUGHPUT = 100;

		public static final int DEFAULT_PARALLELIZM = Runtime.getRuntime().availableProcessors();

		private int throughput = DEFAULT_THROUGHPUT;
		private int parallelism = DEFAULT_PARALLELIZM;

		public int getThroughput() {
			return throughput;
		}

		public void setThroughput(int throughput) {
			this.throughput = throughput;
		}

		public int getParallelism() {
			return parallelism;
		}

		public void setParallelism(int parallelism) {
			this.parallelism = parallelism;
		}
	}
}

class SystemActor extends Actor {
}

class UserActor extends Actor {
}

class RootActor extends Actor {
}
