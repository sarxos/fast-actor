package com.github.sarxos.fastactor;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import com.github.sarxos.fastactor.Receive;
import com.github.sarxos.fastactor.Receive.Matcher;


public class ReceiveBuilderTest {

	@Test
	public void test() {

		final var r = new Receive()
			.match(Number.class, n -> {})
			.match(Integer.class, i -> {});

		final Matcher[] sorted = r.sorted();

		assertSame(Integer.class, sorted[0].type);
		assertSame(Number.class, sorted[1].type);
	}

}
