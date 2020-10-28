package test.fastactor;

import static test.fastactor.ActorRef.noSender;
import static test.fastactor.ActorThreadPool.DEFAULT_THREAD_POOL_NAME;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jctools.maps.NonBlockingHashMap;
import org.jctools.maps.NonBlockingHashMapLong;

import test.fastactor.ActorThreadPool.DockingInfo;


public class ActorSystem {

	public static final long ZERO = 0;

	private static final int DEFAULT_THROUGHPUT = 10;

	final NonBlockingHashMap<String, ActorThreadPool> pools = new NonBlockingHashMap<>(1);
	final NonBlockingHashMapLong<DockingInfo> cells = new NonBlockingHashMapLong<>();

	final String name;
	final int throughput;

	final InternalActors internals;

	public ActorSystem(final String name, final int throughput) {

		this.name = name;
		this.throughput = throughput;

		addPool(new ActorThreadPool(this, DEFAULT_THREAD_POOL_NAME, 16));

		this.internals = new InternalActors();
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
		return actorOf(props, internals.user.uuid);
	}

	<A extends Actor> ActorRef actorOf(final Props<A> props, final long parent) {

		final var pool = getPoolFor(props).orElseThrow(poolNotFoundError(props));

		do {

			final var cell = new ActorCell<A>(this, props, parent);
			final var uuid = cell.uuid();

			// Since random numbers generator used to create unique cell IDs is not synchronized,
			// there is a very low chance a cell to be given the same UUID as some other existing
			// one. To prevent having two cells with the same UUID in the system we need to repeat
			// creation process again until we are happy with the result.

			if (cells.containsKey(uuid)) {
				continue;
			}

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

	public <M> void tell(final M message, final ActorRef target, final ActorRef sender) {
		tell(message, target.uuid, sender.uuid);
	}

	<M> void tell(final M message, final long target, final long sender) {

		final Envelope envelope = new Envelope(message, sender, target);
		final Runnable forwardToDeathLetter = () -> forward(envelope, internals.deathLetter.uuid);
		final Consumer<DockingInfo> deposit = info -> info
			.getPool()
			.deposit(envelope, info);

		getDockingInfoFor(target).ifPresentOrElse(deposit, forwardToDeathLetter);
	}

	<M> void forward(final Envelope envelope, final long newTarget) {
		final Object message = envelope.message;
		final long oldSender = envelope.sender;
		tell(message, newTarget, oldSender);
	}

	<M> void forwardToDeathLetter(final Envelope envelope) {
		forward(envelope, internals.deathLetter.uuid);
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

	private Optional<DockingInfo> getDockingInfoFor(final long uuid) {
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
		final ActorRef temp = actorOf(Props.create(TempActor::new), root.uuid);
		final ActorRef system = actorOf(Props.create(SystemActor::new), root.uuid);
		final ActorRef deathLetter = actorOf(Props.create(DeathLetter::new), root.uuid);
	}
}

class DeathLetter extends Actor {

	@Override
	public ReceiveBuilder receive() {
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
