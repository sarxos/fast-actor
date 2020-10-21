package test.fastactor;

import static test.fastactor.ActorThreadPool.DEFAULT_THREAD_POOL_NAME;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import test.fastactor.ActorThreadPool.DockingInfo;


public class ActorSystem {

	private static final int DEFAULT_THROUGHPUT = 10;

	private final Map<String, ActorThreadPool> pools = new ConcurrentHashMap<>();
	private final Map<UUID, DockingInfo> cells = new ConcurrentHashMap<>();

	final String name;
	final int throughput;

	final HardwiredActors hardwired;

	public ActorSystem(final String name, final int throughput) {

		this.name = name;
		this.throughput = throughput;

		registerThreadPool(new ActorThreadPool(this, DEFAULT_THREAD_POOL_NAME, 16));

		this.hardwired = new HardwiredActors();
	}

	public static ActorSystem create(final String name) {
		final ActorSystem system = new ActorSystem(name, DEFAULT_THROUGHPUT);
		return system;
	}

	public String getName() {
		return name;
	}

	public boolean registerThreadPool(final ActorThreadPool pool) {
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
	public <A extends Actor<M>, M> ActorRef<M> actorOf(final Props<A> props) {
		return actorOf(props, hardwired.user.uuid);
	}

	<A extends Actor<M>, M> ActorRef<M> actorOf(final Props<A> props, final UUID parent) {

		final ActorCell<A, M> cell = new ActorCell<A, M>(this, props, parent);
		final ActorThreadPool pool = getPoolFor(props).orElseThrow(poolNotFoundError(props));
		final DockingInfo details = pool.dock(cell);
		final UUID uuid = cell.getUuid();
		final boolean overwritten = cells.putIfAbsent(uuid, details) != null;

		if (overwritten) {
			throw new IllegalStateException("Cell " + uuid + " docking details were already present in map");
		}

		return cell.self();
	}

	void discard(final UUID uuid, final String poolName) {

		final DockingInfo info = getDockingInfoFor(uuid).orElseThrow(cellNotFoundError(uuid));
		final int threadIndex = info.threadIndex;

		getPoolFor(poolName)
			.orElseThrow(poolNotFoundError(poolName))
			.discard(uuid, threadIndex);

		cells.remove(uuid);
	}

	<M> void tell(final M message, final UUID sender, final UUID target) {

		final Envelope<M> envelope = new Envelope<M>(message, sender, target);
		final DockingInfo targetInfo = getDockingInfoFor(target).orElseThrow(cellNotFoundError(target));

		targetInfo
			.getPool()
			.deliver(envelope, targetInfo);
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

	class HardwiredActors {
		final ActorRef<Object> root = actorOf(Props.create(RootActor::new), (UUID) null);
		final ActorRef<Object> user = actorOf(Props.create(UserActor::new), root.uuid);
		final ActorRef<Object> temp = actorOf(Props.create(TempActor::new), root.uuid);
		final ActorRef<Object> system = actorOf(Props.create(SystemActor::new), root.uuid);
		final ActorRef<Object> deathLetter = actorOf(Props.create(DeathLetter::new), root.uuid);
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
