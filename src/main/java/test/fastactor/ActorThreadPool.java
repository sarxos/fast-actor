package test.fastactor;

import static test.fastactor.Props.RUN_ON_ANY_THREAD;

import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;


public class ActorThreadPool extends ThreadGroup {

	public static final String DEFAULT_THREAD_POOL_NAME = "default-thread-pool";
	public static final int DEFAULT_PARALLELIZM = Runtime.getRuntime().availableProcessors();

	final ActorSystem system;
	final ActorThreadFactory factory;
	final int parallelism;
	final ActorThread[] threads;
	final CountDownLatch guard;

	private int shift = 0;

	ActorThreadPool(final ActorSystem system, final String name) {
		this(system, name, DEFAULT_PARALLELIZM);
	}

	ActorThreadPool(final ActorSystem system, final String name, final int parallelism) {

		super(name);

		this.system = system;
		this.parallelism = parallelism;
		this.factory = new ActorThreadFactory(name);
		this.guard = new CountDownLatch(parallelism);
		this.threads = createThreads();
	}

	private ActorThread[] createThreads() {
		return IntStream
			.range(0, parallelism)
			.mapToObj(this::createThread)
			.peek(Thread::start)
			.toArray(ActorThread[]::new);
	}

	private ActorThread createThread(final int index) {
		return factory
			.newThread(this, system, index)
			.withTerminator(guard::countDown);
	}

	public Shutdown shutdown() {
		return new Shutdown().execute();
	}

	public ActorCellInfo prepareCellInfo(final Props<? extends Actor> props) {

		final var uuid = system.generateNextUuid();
		final var threadIndex = getDesiredThreadIndex(props);
		final var thread = threads[threadIndex];

		return new ActorCellInfo(this, thread, uuid);
	}

	private int getDesiredThreadIndex(final Props<? extends Actor> props) {
		if (props.threadIndex == RUN_ON_ANY_THREAD) {
			return shift++ % parallelism;
		} else {
			return props.threadIndex % parallelism;
		}
	}

	static class ActorCellInfo {

		final ActorThreadPool pool;
		final ActorThread thread;
		final long uuid;

		public ActorCellInfo(final ActorThreadPool pool, final ActorThread thread, final long uuid) {
			this.pool = pool;
			this.thread = thread;
			this.uuid = uuid;
		}

		public ActorThreadPool getPool() {
			return pool;
		}

		public String getPoolName() {
			return pool.getName();
		}

		public int getThreadIndex() {
			return thread.index;
		}

		public long getUuid() {
			return uuid;
		}

		@Override
		public String toString() {
			return new StringBuilder()
				.append(getClass().getName())
				.append("[ pool = ")
				.append(getPoolName())
				.append(", thread = ")
				.append(thread.getName())
				.append(", index = ")
				.append(thread.index)
				.append(", uuid = ")
				.append(uuid)
				.append(" ]")
				.toString();
		}
	}

	public class Shutdown {

		public Shutdown execute() {
			for (final var thread : threads) {
				thread.interrupt();
			}
			return this;
		}

		public void awaitTermination() {
			try {
				guard.await();
			} catch (InterruptedException e) {
				return;
			}
		}
	}
}
