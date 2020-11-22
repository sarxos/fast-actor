package test.factactor.benchmark;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.RunnerException;

import test.factactor.benchmark.ThreadTerminationBenchmark.IterationCounter;
import test.factactor.runner.BenchmarkRunner;


@Fork(1)
@Threads(1)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 100, timeUnit = TimeUnit.MILLISECONDS)
public class ThreadTerminationBenchmark {

	public static void main(String[] args) throws RunnerException {
		BenchmarkRunner.run(ThreadTerminationBenchmark.class);
	}

	private static final Duration WAIT_TIME = Duration.ofMillis(1000);

	@AuxCounters
	@State(Scope.Thread)
	public static class IterationCounter {

		public long iterations;

		@Setup(Level.Iteration)
		public void clean() {
			iterations = 0;
		}
	}

	@Benchmark
	public void benchmarkThreadWithInterruptedFlag(final IterationCounter counter) throws InterruptedException {
		benchmark(new ThreadWithInterruptedFlag(counter));
	}

	@Benchmark
	public void benchmarkThreadWithAtomicBoolean(final IterationCounter counter) throws InterruptedException {
		benchmark(new ThreadWithAtomicBoolean(counter));
	}

	@Benchmark
	public void benchmarkThreadWithVolatileBoolean(final IterationCounter counter) throws InterruptedException {
		benchmark(new ThreadWithVolatileBoolean(counter));
	}

	@Benchmark
	public void benchmarkThreadWithPaddedVolatileBoolean(final IterationCounter counter) throws InterruptedException {
		benchmark(new ThreadWithPaddedVolatileBoolean(counter));
	}

	private <T extends Thread & Terminator> void benchmark(final T thread) throws InterruptedException {
		thread.start();
		Thread.sleep(WAIT_TIME.toMillis());
		thread.terminate();
		thread.join(WAIT_TIME.toMillis() * 2);
	}
}

interface Terminator {
	void terminate();
}

class ThreadWithInterruptedFlag extends Thread implements Terminator {

	final IterationCounter counter;

	public ThreadWithInterruptedFlag(IterationCounter counter) {
		this.counter = counter;
	}

	@Override
	public void run() {
		while (!isInterrupted()) {
			counter.iterations++;
		}
	}

	@Override
	public void terminate() {
		interrupt();
	}
}

class ThreadWithAtomicBoolean extends Thread implements Terminator {

	final IterationCounter counter;
	final AtomicBoolean terminated = new AtomicBoolean(false);

	public ThreadWithAtomicBoolean(IterationCounter counter) {
		this.counter = counter;
	}

	@Override
	public void run() {
		while (!terminated.get()) {
			counter.iterations++;
		}
	}

	@Override
	public void terminate() {
		terminated.set(true);
	}
}

class ThreadWithVolatileBoolean extends Thread implements Terminator {

	final IterationCounter counter;
	volatile boolean terminated = false;

	public ThreadWithVolatileBoolean(IterationCounter counter) {
		this.counter = counter;
	}

	@Override
	public void run() {
		while (!terminated) {
			counter.iterations++;
		}
	}

	@Override
	public void terminate() {
		terminated = true;
	}
}

class ThreadWithPaddedVolatileBoolean extends Thread implements Terminator {

	final IterationCounter counter;
	final PaddedVolatileBoolean terminated = new PaddedVolatileBoolean();

	public ThreadWithPaddedVolatileBoolean(IterationCounter counter) {
		this.counter = counter;
	}

	@Override
	public void run() {
		while (!terminated.value) {
			counter.iterations++;
		}
	}

	@Override
	public void terminate() {
		terminated.value = true;
	}

	@SuppressWarnings("unused")
	private static class Padding {
		long p00, p01, p02, p03, p04, p05, p06, p07, p08, p09 = 0;
		long p10, p11, p12, p13 = 0;
	}

	private static class VolatileBooleanBarrier extends Padding {
		public volatile boolean value = false;
	}

	@SuppressWarnings("unused")
	private static final class PaddedVolatileBoolean extends VolatileBooleanBarrier {
		long p00, p01, p02, p03, p04, p05, p06, p07, p08, p09 = 0;
		long p10, p11, p12, p13, p14 = 0;
	}
}
