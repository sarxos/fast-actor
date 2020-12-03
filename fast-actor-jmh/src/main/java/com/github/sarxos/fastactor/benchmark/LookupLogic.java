package com.github.sarxos.fastactor.benchmark;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;


@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class LookupLogic {

	private static final Random RANDOM = new Random();
	private static final int LENGTH = 1024 * 1024;
	private static final boolean[] LOGIC = new boolean[] { false, true };

	private final int[] numbers = new int[LENGTH];
	private int position = 0;

	@Setup(Level.Iteration)
	public void setUp() {
		for (int i = 0; i < numbers.length; i++) {
			numbers[i] = randomIntZeroOne();
		}
	}

	@Setup(Level.Invocation)
	public void reset() {
		if (position++ >= numbers.length) {
			position = 0;
		}
	}

	@Benchmark
	@OutputTimeUnit(TimeUnit.NANOSECONDS)
	@BenchmarkMode(Mode.AverageTime)
	public boolean booleanOperation() {
		return numbers[position] == 1;
	}

	@Benchmark
	@OutputTimeUnit(TimeUnit.NANOSECONDS)
	@BenchmarkMode(Mode.AverageTime)
	public boolean booleanLookupLogic() {
		return LOGIC[numbers[position]];
	}

	private final int randomIntZeroOne() {
		return RANDOM.nextInt(2);
	}
}
