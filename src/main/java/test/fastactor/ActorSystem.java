package test.fastactor;

import static test.fastactor.ActorCell.Directives.STOP_CELL;
import static test.fastactor.ActorRef.noSender;
import static test.fastactor.ActorThreadPool.DEFAULT_THREAD_POOL_NAME;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import test.fastactor.ActorThreadPool.DockingInfo;


public class ActorSystem {

	private static final int DEFAULT_THROUGHPUT = 10;

	final Map<String, ActorThreadPool> pools = new ConcurrentHashMap<>();
	final Map<UUID, DockingInfo> cells = new ConcurrentHashMap<>();

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
	public <A extends Actor<M>, M> ActorRef actorOf(final Props<A> props) {
		return actorOf(props, internals.user.uuid);
	}

	<A extends Actor<M>, M> ActorRef actorOf(final Props<A> props, final UUID parent) {

		final var pool = getPoolFor(props).orElseThrow(poolNotFoundError(props));

		do {

			final var cell = new ActorCell<A, M>(this, props, parent);
			final var uuid = cell.getUuid();

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
				return cell.initialize();
			}

		} while (true);
	}

	void discard(final UUID uuid) {

		final var info = getDockingInfoFor(uuid).orElseThrow(cellNotFoundError(uuid));
		final var threadIndex = info.threadIndex;
		final var threadPool = info.pool;

		threadPool.discard(uuid, threadIndex);

		cells.remove(uuid);
	}

	public <M> void tell(final M message, final ActorRef target, final ActorRef sender) {
		tell(message, target.uuid, sender.uuid);
	}

	<M> void tell(final M message, final UUID target, final UUID sender) {

		final Envelope<M> envelope = new Envelope<M>(message, sender, target);
		final DockingInfo targetInfo = getDockingInfoFor(target).orElseThrow(cellNotFoundError(target));

		targetInfo
			.getPool()
			.deposit(envelope, targetInfo);
	}

	<M> void forward(final Envelope<M> envelope, final UUID newTarget) {
		final M message = envelope.message;
		final UUID sender = envelope.sender;
		tell(message, newTarget, sender);
	}

	<M> void forwardToDeathLetter(final Envelope<M> envelope) {
		forward(envelope, internals.deathLetter.uuid);
	}

	/**
	 * Stops the actor pointed by the {@link ActorRef} provided in the argument. This is
	 * asynchronous operation and therefore actor may be still alive when this method completes.
	 *
	 * @param target the target to be stopped
	 */
	public void stop(final ActorRef target) {
		tell(STOP_CELL, target, noSender());
	}

	private Optional<DockingInfo> getDockingInfoFor(final UUID uuid) {
		return Optional.ofNullable(uuid).map(cells::get);
	}

	private Optional<ActorThreadPool> getPoolFor(final Props<? extends Actor<?>> props) {
		return getPoolFor(props.getThreadPoolName());
	}

	private Optional<ActorThreadPool> getPoolFor(final String poolName) {
		return Optional.ofNullable(pools.get(poolName));
	}

	private static Supplier<RuntimeException> poolNotFoundError(final Props<? extends Actor<?>> props) {
		return poolNotFoundError(props.getThreadPoolName());
	}

	private static Supplier<RuntimeException> poolNotFoundError(final String poolName) {
		return () -> new IllegalStateException("Thread pool with name " + poolName + " has not been found in the system");
	}

	private static Supplier<RuntimeException> cellNotFoundError(final UUID uuid) {
		return () -> new IllegalStateException("Cell with UUID " + uuid + " has not been found in the system");
	}

	class InternalActors {
		final ActorRef root = actorOf(Props.create(RootActor::new), (UUID) null);
		final ActorRef user = actorOf(Props.create(UserActor::new), root.uuid);
		final ActorRef temp = actorOf(Props.create(TempActor::new), root.uuid);
		final ActorRef system = actorOf(Props.create(SystemActor::new), root.uuid);
		final ActorRef deathLetter = actorOf(Props.create(DeathLetter::new), root.uuid);
	}
}

class DeathLetter extends Actor<Object> {

	@Override
	public void receive(final Object message) {
		System.out.println("Death letter: " + message);
	}
}

class SystemActor extends Actor<Object> {
	public @Override void receive(final Object message) {
		// do nothing
	}
}

class UserActor extends Actor<Object> {
	public @Override void receive(final Object message) {
		// do nothing
	}
}

class RootActor extends Actor<Object> {
	public @Override void receive(final Object messahe) {
		// do nothing
	}
}

class TempActor extends Actor<Object> {
	public @Override void receive(final Object message) {
		// do nothing
	}
}
