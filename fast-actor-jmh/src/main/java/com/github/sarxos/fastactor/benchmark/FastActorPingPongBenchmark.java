package com.github.sarxos.fastactor.benchmark;

import static com.github.sarxos.fastactor.benchmark.FastActorPingPongBenchmark.EXPECTED_DELIVERIES_COUNT;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.AuxCounters.Type;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.RunnerException;

import com.github.sarxos.fastactor.Actor;
import com.github.sarxos.fastactor.ActorSystem;
import com.github.sarxos.fastactor.Props;
import com.github.sarxos.fastactor.Receive;
import com.github.sarxos.fastactor.benchmark.FastActorPingPongBenchmark.DeliveryCounter;
import com.github.sarxos.fastactor.dsl.Base;
import com.github.sarxos.fastactor.runner.BenchmarkRunner;


@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
public class FastActorPingPongBenchmark {

	public static void main(String[] args) throws RunnerException {
		BenchmarkRunner.run(FastActorPingPongBenchmark.class);
	}

	static final int MESSAGES_COUNT = 100;
	static final int EXPECTED_DELIVERIES_COUNT = 10_000_000;

	@AuxCounters(Type.OPERATIONS)
	@State(Scope.Thread)
	public static class DeliveryCounter {

		public long count1;
		public long count2;

		@Setup(Level.Iteration)
		public void clean() {
			count1 = 0;
			count2 = 0;
		}

		public long total() {
			return count1 + count2;
		}
	}

	@State(Scope.Thread)
	public static class Context {

		ActorSystem system;
		CountDownLatch blocker;

		@Setup(Level.Iteration)
		public void setupIteration() {
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
	}

	@Benchmark
	public void benchmark(final DeliveryCounter counter, Context context) throws InterruptedException {

		final var system = context.system;
		final var blocker = context.blocker;

		final var ref1 = system.actorOf(Props.create(() -> new TestActor(counter, blocker, 1)));
		final var ref2 = system.actorOf(Props.create(() -> new TestActor(counter, blocker, 2)));

		IntStream
			.range(0, MESSAGES_COUNT)
			.forEach(i -> {
				final var a = i % 2 == 0 ? ref1 : ref2;
				final var b = i % 2 == 0 ? ref2 : ref1;
				a.tell(Integer.valueOf(i), b);
			});

		blocker.await();
	}
}

class TestActor extends Actor implements Base {

	final DeliveryCounter counter;
	final CountDownLatch blocker;
	final int number;
	long count = 0;

	public TestActor(final DeliveryCounter counter, final CountDownLatch blocker, final int number) {
		this.counter = counter;
		this.blocker = blocker;
		this.number = number;
	}

	@Override
	public Receive receive() {
		return super.receive()
			.match(Integer.class, this::onInteger);
	}

	@Override
	public void postStop() {
		if (number == 1) {
			counter.count1 += count;
		} else {
			counter.count2 += count;
		}
		blocker.countDown();
	}

	private void onInteger(final Integer i) {
		if (count++ >= EXPECTED_DELIVERIES_COUNT) {
			stop();
		} else {
			reply(i);
		}
	}
}
