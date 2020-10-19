package test.fastactor;

import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;


public class ActorThreadPool {

	public static final String DEFAULT_THREAD_POOL_NAME = "default-thread-pool";

	static final Random RANDOM = new Random();

	final ActorSystem system;
	final String name;
	final ActorThreadFactory factory;
	final int parallelism;
	final List<ActorThread> threads;
	final CountDownLatch guard;

	ActorThreadPool(final ActorSystem system, final String name, final int parallelism) {
		this.system = system;
		this.name = name;
		this.parallelism = parallelism;
		this.factory = new ActorThreadFactory(name);
		this.guard = new CountDownLatch(parallelism);
		this.threads = unmodifiableList(createThreads());
	}

	private List<ActorThread> createThreads() {
		return IntStream
			.range(0, parallelism)
			.mapToObj(this::createThread)
			.peek(Thread::start)
			.collect(toCollection(() -> new ArrayList<>(parallelism)));
	}

	private ActorThread createThread(final int index) {
		return factory
			.newThread(system, index)
			.withTerminator(guard::countDown);
	}

	public Shutdown shutdown() {
		return new Shutdown().execute();
	}

	public String getName() {
		return name;
	}

	public <M> DockingInfo dock(final ActorCell<? extends Actor<M>, M> cell) {

		final int threadIndex = RANDOM.nextInt(parallelism);
		final ActorThread thread = threads.get(threadIndex);

		thread.dock(cell);

		return new DockingInfo(this, threadIndex);
	}

	public <M> void deliver(final Envelope<M> envelope, final DockingInfo info) {

		final Thread currentThread = Thread.currentThread();
		final ActorThread dockingThread = threadAt(info.threadIndex);

		// when caller is a local thread, then deliver message to the internal queue, and
		// otherwise, if caller is on a separate thread, deliver message to the external
		// queue

		if (currentThread == dockingThread) {
			dockingThread.deliverInternal(envelope);
		} else {
			dockingThread.deliverExternal(envelope);
		}
	}

	private ActorThread threadAt(final int threadIndex) {
		return threads.get(threadIndex);
	}

	static class DockingInfo {

		final ActorThreadPool pool;
		final int threadIndex;

		public DockingInfo(final ActorThreadPool pool, final int threadIndex) {
			this.pool = pool;
			this.threadIndex = threadIndex;
		}

		public ActorThreadPool getPool() {
			return pool;
		}

		public String getPoolName() {
			return pool.getName();
		}
	}

	public class Shutdown {

		public Shutdown execute() {
			threads.forEach(Thread::interrupt);
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
