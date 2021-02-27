package com.github.sarxos.fastactor.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.sarxos.fastactor.ActorSystem;


public class ProbeTest {

	private ActorSystem system;
	private Probe probe;

	@BeforeEach
	public void setup() {
		try {
			system = ActorSystem.create("ProbeTest");
		} catch (Exception e) {
			e.printStackTrace();
		}
		probe = new Probe(system);
	}

	@AfterEach
	public void teardown() {
		system.shutdown();
	}

	@Test
	public void test_receive() {

		final var expected = new Object();
		probe.ref().tell(expected);
		final var received = probe.receive();

		Assertions.assertSame(expected, received);
	}

	@Test
	public void test_receiveInterrupted() {
		Thread.currentThread().interrupt();
		Assertions.assertThrows(IllegalStateException.class, () -> probe.receive());
	}

	@Test
	public void test_receiveWithDuration() {

		final var expected = new Object();
		probe.ref().tell(expected);
		final var received = probe.receive(Duration.ofSeconds(1));

		Assertions.assertSame(expected, received);
	}

	@Test
	public void test_receiveWithDurationExceeded() {
		Assertions.assertThrows(AssertionError.class, () -> probe.receive(Duration.ofMillis(10)));
	}

	@Test
	public void test_receiveN() {

		final var expected = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6));
		expected.forEach(i -> probe.ref().tell(i));
		final var received = probe.receiveN(expected.size());

		for (int i = 0; i < expected.size(); i++) {
			Assertions.assertSame(expected.get(i), received.get(i));
		}
	}

	@Test
	public void test_receiveNWithDuration() {

		final var expected = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6));
		expected.forEach(i -> probe.ref().tell(i));
		final var received = probe.receiveN(expected.size(), Duration.ofSeconds(1));

		for (int i = 0; i < expected.size(); i++) {
			Assertions.assertSame(expected.get(i), received.get(i));
		}
	}

	@Test
	public void test_receiveNWithDurationExceeded() {
		Assertions.assertThrows(AssertionError.class, () -> probe.receiveN(10, Duration.ofMillis(10)));
	}

	@Test
	public void test_receiveNWithDurationExceededBetweenMessages() throws InterruptedException {

		final var expected = new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));

		final Thread t = new Thread(() -> expected.stream()
			.peek(probe.ref()::tell)
			.forEach(any -> sleep(50)));

		t.setDaemon(true);
		t.start();

		Assertions.assertThrows(AssertionError.class, () -> probe.receiveN(expected.size(), Duration.ofMillis(100)));

		t.join();
	}

	@Test
	public void test_receiveInstanceOf() {

		final var expected = Integer.valueOf(1);
		probe.ref().tell(expected);
		final var received = probe.receiveInstanceOf(Integer.class);

		Assertions.assertSame(expected, received);
	}

	@Test
	public void test_receiveInstanceOfWithWrongType() {
		probe.ref().tell("not a number");
		Assertions.assertThrows(AssertionError.class, () -> probe.receiveInstanceOf(Integer.class));
	}

	@Test
	public void test_receiveInstanceOfWithDuration() {

		final var expected = Integer.valueOf(1);
		probe.ref().tell(expected);
		final var received = probe.receiveInstanceOf(Integer.class, Duration.ofSeconds(1));

		Assertions.assertSame(expected, received);
	}

	@Test
	public void test_receiveInstanceOfWithDurationExceeded() {
		Assertions.assertThrows(AssertionError.class, () -> probe.receiveInstanceOf(Integer.class, Duration.ofMillis(10)));
	}

	@Test
	public void test_receiveNInstanceOf() {

		final var expected = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6));
		expected.forEach(i -> probe.ref().tell(i));
		final var received = probe.receiveNInstanceOf(expected.size(), Integer.class);

		for (int i = 0; i < expected.size(); i++) {
			Assertions.assertSame(expected.get(i), received.get(i));
		}
	}

	@Test
	public void test_receiveNInstanceOfWithWrongType() {

		Arrays
			.asList(1, "not a number") // 2 elements
			.forEach(probe.ref()::tell);

		Assertions.assertThrows(AssertionError.class, () -> probe.receiveNInstanceOf(2, Integer.class));
	}

	@Test
	public void test_receiveNInstanceOfWithDuration() {

		final var expected = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6));
		expected.forEach(i -> probe.ref().tell(i));
		final var received = probe.receiveNInstanceOf(expected.size(), Integer.class, Duration.ofSeconds(1));

		for (int i = 0; i < expected.size(); i++) {
			Assertions.assertSame(expected.get(i), received.get(i));
		}
	}

	@Test
	public void test_receiveNInstanceOfWithDurationExceeded() {
		Assertions.assertThrows(AssertionError.class, () -> probe.receiveNInstanceOf(10, Integer.class, Duration.ofMillis(10)));
	}

	@Test
	public void test_receiveNInstanceOfWithDurationExceededBetweenMessages() throws InterruptedException {

		final var expected = new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));

		final Thread t = new Thread(() -> expected.stream()
			.peek(probe.ref()::tell)
			.forEach(any -> sleep(50)));

		t.setDaemon(true);
		t.start();

		Assertions.assertThrows(AssertionError.class, () -> probe.receiveNInstanceOf(expected.size(), Integer.class, Duration.ofMillis(100)));

		t.join();
	}

	private static void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			throw new IllegalStateException(e);
		}
	}
}
