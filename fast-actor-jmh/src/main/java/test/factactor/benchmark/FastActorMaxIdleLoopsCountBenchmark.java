package test.factactor.benchmark;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.AuxCounters.Type;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.RunnerException;

import test.factactor.runner.BenchmarkRunner;
import test.fastactor.Actor;
import test.fastactor.ActorSystem;
import test.fastactor.ActorThread;
import test.fastactor.Props;
import test.fastactor.Receive;
import test.fastactor.dsl.Base;


@State(Scope.Thread)
@OutputTimeUnit(SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class FastActorMaxIdleLoopsCountBenchmark {

	public static void main(String[] args) throws RunnerException {
		BenchmarkRunner.run(FastActorMaxIdleLoopsCountBenchmark.class);
	}

	static final int MESSAGES_COUNT = 100;
	static final int EXPECTED_DELIVERIES_COUNT = 1_000_000;

	@State(Scope.Thread)
	@AuxCounters(Type.OPERATIONS)
	public static class DeliveryCounter {

		public volatile long count1;
		public volatile long count2;

		@Setup(Level.Iteration)
		public void clean() {
			count1 = 0;
			count2 = 0;
		}

		public long total() {
			return count1 + count2;
		}
	}

	// @Param({ "0", "1", "2", "4", "8", "16", "32", "8", "16", "32", "64", "128", "512", "2048",
	// "8192", "32768", "131072", "524288" })
	@Param({ "0", "1", "2", "4", "8", "16", "32", "8", "16", "32", "64", "128", "512" })
	public int maxIdleLoopsCount;

	ActorSystem system;
	CountDownLatch blocker;

	@Setup(Level.Iteration)
	public void setupIteration() {
		ActorThread.setMaxIdleLoopsCount(maxIdleLoopsCount);
		system = ActorSystem.create("perf-ping-pong");
	}

	@TearDown(Level.Iteration)
	public void teardown() {
		system.shutdown();
	}

	@Setup(Level.Invocation)
	public void setupInvocation() {
		blocker = new CountDownLatch(2);
	}

	@Benchmark
	public void benchmark(final DeliveryCounter counter) throws InterruptedException {

		final var ref1 = system.actorOf(Props.create(() -> new MILCActor1(counter, blocker)));
		final var ref2 = system.actorOf(Props.create(() -> new MILCActor2(counter, blocker)));

		IntStream
			.range(0, MESSAGES_COUNT)
			.forEach(i -> {
				final var a = i % 2 == 0 ? ref1 : ref2;
				final var b = i % 2 == 0 ? ref2 : ref1;
				a.tell(Integer.valueOf(i), b);
			});

		blocker.await();
	}

	static abstract class MILCBenchmarkActor extends Actor implements Base {

		final DeliveryCounter counter;
		final CountDownLatch blocker;

		long count = 0;

		public MILCBenchmarkActor(final DeliveryCounter counter, final CountDownLatch blocker) {
			this.counter = counter;
			this.blocker = blocker;
		}

		@Override
		public Receive receive() {
			return super.receive()
				.match(Integer.class, this::onInteger);
		}

		private void onInteger(final Integer i) {
			if (count++ >= EXPECTED_DELIVERIES_COUNT) {
				stop();
			} else {
				reply(i);
			}
		}
	}

	static class MILCActor1 extends MILCBenchmarkActor {

		public MILCActor1(final DeliveryCounter counter, final CountDownLatch blocker) {
			super(counter, blocker);
		}

		@Override
		public void postStop() {
			counter.count1 += count;
			blocker.countDown();
		}
	}

	static class MILCActor2 extends MILCBenchmarkActor {

		public MILCActor2(DeliveryCounter counter, CountDownLatch blocker) {
			super(counter, blocker);
		}

		@Override
		public void postStop() {
			counter.count2 += count;
			blocker.countDown();
		}
	}
}
