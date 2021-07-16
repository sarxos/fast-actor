package com.github.sarxos.fastactor;

import static com.github.sarxos.fastactor.Props.RUN_ON_ANY_THREAD;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Supplier;


public class ActorThreadPool extends ThreadGroup {

	private final ActorThreadFactory factory;

	private int parallelism;
	private ActorThread[] threads;
	private int shift = 0;

	public ActorThreadPool(final String name) {
		super(name);
		this.factory = new ActorThreadFactory(name);
	}

	void start(final ActorSystem system) {

		this.parallelism = system.parallelism;
		this.threads = new ActorThread[parallelism];

		// Create all threads.

		for (int index = 0; index < parallelism; index++) {
			threads[index] = newThread(system, index);
		}

		// Start all threads.

		for (int index = 0; index < parallelism; index++) {
			threads[index].start();
		}
	}

	/**
	 * For unit tests only!
	 *
	 * @return Threads allocated in this pool.
	 */
	ActorThread[] getThreads() {
		return threads;
	}

	private ActorThread newThread(final ActorSystem system, final int index) {
		return factory.newThread(this, system, index);
	}

	public Shutdown shutdown() {
		return new Shutdown().execute();
	}

	public ActorCellInfo prepareCellInfo(final ActorSystem system, final Props<? extends Actor> props) {

		final var uuid = system.generateNextUuid();
		final var index = getThreadIndex(props);
		final var thread = threads[index];

		return new ActorCellInfo(this, thread, uuid);
	}

	private int getThreadIndex(final Props<? extends Actor> props) {

		final var i = props.threadIndex;
		final var p = parallelism;

		if (i == RUN_ON_ANY_THREAD) {
			return shift++ % p;
		} else {
			return i % p;
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

			for (int i = 0; i < threads.length; i++) {
				final var t = threads[i];
				if (t != null) {
					t.interrupt();
				}
			}

			return this;
		}

		public void awaitTermination() {
			try {
				do {
					if (isAnyAlive(threads)) {
						Thread.onSpinWait();
						Thread.sleep(10);
					} else {
						return;
					}
				} while (true);
			} catch (InterruptedException e) {
				return;
			}
		}

		private boolean isAnyAlive(final Thread[] threads) {
			for (int i = 0; i < threads.length; i++) {
				if (threads[i].isAlive()) {
					return true;
				}
			}
			return false;
		}
	}
}

final class AtomicActorThreadsArray extends AtomicReferenceArray<ActorThread> {

	private static final long serialVersionUID = 1L;

	public AtomicActorThreadsArray(final int length) {
		super(length);
	}

	public final ActorThread getOrCompute(final int i, final Supplier<ActorThread> supplier) {

		final ActorThread thread;
		final ActorThread tmp = get(i);

		if (tmp == null) {
			if (compareAndSet(i, null, thread = supplier.get())) {
				return thread;
			} else {
				return get(i);
			}
		}

		return tmp;
	}
}
