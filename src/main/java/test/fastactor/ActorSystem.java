package test.fastactor;

import static test.fastactor.ActorRef.noSender;
import static test.fastactor.ActorThreadPool.DEFAULT_THREAD_POOL_NAME;

import java.util.Optional;
import java.util.function.Supplier;

import org.jctools.maps.NonBlockingHashMap;
import org.jctools.maps.NonBlockingHashMapLong;

import test.fastactor.ActorThreadPool.CellDockingInfo;
import test.fastactor.AskRouter.Ask;


public class ActorSystem {

	public static final long ZERO = 0;

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
			forwardToDeathLetters(envelope);
		} else {
			final var threadPool = info.getPool();
			final var threadIndex = info.threadIndex;
			threadPool.deposit(envelope, threadIndex);
		}
	}

	Ask ask(final Object message, final long target) {

		final var ask = new Ask(message, target);
		final var router = internal.askRouter;
		final var sender = noSender();

		tell(ask, router, sender);

		return ask;
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

	void forwardToDeathLetters(final Envelope envelope) {
		forward(envelope, internal.deathLetters.uuid);
	}

	/**
	 * Stops the actor pointed by the {@link ActorRef} provided in the argument. This is
	 * asynchronous operation and therefore actor may be still alive when this method completes.
	 *
	 * @param target the target to be stopped
	 */
	public void stop(final ActorRef target) {
		tell(Directives.STOP, target, noSender());
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

	class InternalActors {
		final ActorRef root = actorOf(Props.create(RootActor::new), ZERO);
		final ActorRef user = actorOf(Props.create(UserActor::new), root.uuid);
		final ActorRef system = actorOf(Props.create(SystemActor::new), root.uuid);
		final ActorRef askRouter = actorOf(Props.create(AskRouter::new), root.uuid);
		final ActorRef deathLetters = actorOf(Props.create(DeathLetters::new), root.uuid);
	}
}

class DeathLetters extends Actor {

	@Override
	public Receive receive() {
		return super.receive()
			.matchAny(message -> System.out.println("Death letter: " + message));
	}
}

class SystemActor extends Actor {
}

class UserActor extends Actor {
}

class RootActor extends Actor {
}

class TempActor extends Actor {
}
