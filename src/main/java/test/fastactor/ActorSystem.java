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

	final ActorRef<DeathLetter, Object> deathLetter;

	public ActorSystem(final String name, final int throughput) {

		this.name = name;
		this.throughput = throughput;

		registerThreadPool(new ActorThreadPool(this, DEFAULT_THREAD_POOL_NAME, 16));

		this.deathLetter = actorOf(Props.create(() -> new DeathLetter()));
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

	public <A extends Actor<M>, M> ActorRef<A, M> actorOf(final Props<A> props) {

		final ActorCell<A, M> cell = new ActorCell<A, M>(this, props);
		final ActorThreadPool pool = getPoolFor(props).orElseThrow(poolNotFoundError(props));
		final DockingInfo details = pool.dock(cell);
		final UUID uuid = cell.getUuid();
		final boolean overwritten = cells.putIfAbsent(uuid, details) != null;

		if (overwritten) {
			throw new IllegalStateException("Cell " + uuid + " docking details were already present in map");
		}

		return cell.self();
	}

	<M, S extends Actor<?>, T extends Actor<M>> void tell(final M message, final UUID sender, final UUID target) {

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
		return Optional.ofNullable(pools.get(props.getThreadPoolName()));
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
}

class DeathLetter extends Actor<Object> {

	@Override
	public void receive(final Object message) {
		System.out.println("Death letter received: " + message);
	}
}
