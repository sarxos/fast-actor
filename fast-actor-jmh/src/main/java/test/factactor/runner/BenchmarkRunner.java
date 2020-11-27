package test.factactor.runner;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;


public final class BenchmarkRunner {

	public static void run(final Class<?> clazz) throws RunnerException {

		final String name = clazz.getSimpleName();

		final Options opt = new OptionsBuilder()
			.include(".*" + name + ".*")
			.resultFormat(ResultFormatType.TEXT)
			.result("results/" + name + ".txt")
			.shouldDoGC(true)
			.build();

		new Runner(opt).run();
	}

}
