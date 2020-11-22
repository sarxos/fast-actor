package test.factactor.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.RunnerException;

import test.factactor.runner.BenchmarkRunner;

//import jdk.internal.vm.annotation.Contended;


@Fork(value = 1, jvmArgsPrepend = "-XX:-RestrictContended")
@Warmup(iterations = 10)
@Measurement(iterations = 25)
@Threads(2)
public class FalseSharingBenchmark {

	public static void main(String[] args) throws RunnerException {
		BenchmarkRunner.run(FalseSharingBenchmark.class);
	}

	@State(Scope.Group)
	public static class Unpadded {
		public long a;
		public long b;
	}

	@State(Scope.Group)
	public static class Padded {
		// @Contended
		public long a;
		public long b;
	}

	@Group("unpadded")
	@GroupThreads(1)
	@Benchmark
	public long updateUnpaddedA(Unpadded u) {
		return u.a++;
	}

	@Group("unpadded")
	@GroupThreads(1)
	@Benchmark
	public long updateUnpaddedB(Unpadded u) {
		return u.b++;
	}

	@Group("padded")
	@GroupThreads(1)
	@Benchmark
	public long updatePaddedA(Padded u) {
		return u.a++;
	}

	@Group("padded")
	@GroupThreads(1)
	@Benchmark
	public long updatePaddedB(Padded u) {
		return u.b++;
	}
}
