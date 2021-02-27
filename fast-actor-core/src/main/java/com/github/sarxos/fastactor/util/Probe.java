package com.github.sarxos.fastactor.util;

import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.github.sarxos.fastactor.Actor;
import com.github.sarxos.fastactor.ActorRef;
import com.github.sarxos.fastactor.ActorSystem;
import com.github.sarxos.fastactor.Props;
import com.github.sarxos.fastactor.Receive;


/**
 * An utility to probe messages from inside the actors.
 *
 * @author Bartosz Firyn (sarxos)
 */
public class Probe {

	private static final long THREE_SECONDS = 3L;
	private static final Duration THREE_SECONDS_DURATION = Duration.ofSeconds(THREE_SECONDS);

	final BlockingQueue<Object> messages = new LinkedBlockingQueue<>();
	final ActorSystem system;
	final ActorRef ref;

	Probe(final ActorSystem system) {
		this.system = system;
		this.ref = system.actorOf(Props.create(ProbeActor::new));
	}

	/**
	 * @return {@link ActorRef} to be used to receive messages
	 */
	public ActorRef ref() {
		return ref;
	}

	/**
	 * Receive one message which is the instance of a given {@link Class} or throws
	 * {@link AssertionError} if different type has been received or when waiting time of
	 * {@value #THREE_SECONDS} seconds was exceeded.
	 *
	 * @param <T> inferred message type
	 * @param clazz the message class
	 * @return The received message.
	 */
	public <T> T receiveInstanceOf(final Class<T> clazz) {
		return receiveInstanceOf(clazz, THREE_SECONDS_DURATION);
	}

	/**
	 * Receive one message which is the instance of a given {@link Class} or throws
	 * {@link AssertionError} when provided time {@link Duration} was exceeded or when message of
	 * the incompatible type has been received.
	 *
	 * @param <T> inferred message type
	 * @param clazz the message class
	 * @param duration the max time {@link Duration} to wait for message
	 * @return The received message.
	 */
	@SuppressWarnings("unchecked")
	public <T> T receiveInstanceOf(final Class<T> clazz, final Duration duration) {

		final Object received = receive(duration);

		if (clazz.isInstance(received)) {
			return (T) received;
		} else {
			throw new AssertionError("Expected message of " + clazz + " but got " + received.getClass());
		}
	}

	/**
	 * Receive N messages which are the instance of a given {@link Class} or throws
	 * {@link AssertionError} if different type was received or when waiting time of
	 * {@value #THREE_SECONDS} seconds has been exceeded.
	 *
	 * @param <T> inferred message type
	 * @param n the number of messages to receive
	 * @param clazz the message class
	 * @return The received message.
	 */
	public <T> List<T> receiveNInstanceOf(final int n, final Class<T> clazz) {
		return receiveNInstanceOf(n, clazz, THREE_SECONDS_DURATION);
	}

	@SuppressWarnings("unchecked")
	public <T> List<T> receiveNInstanceOf(final int n, final Class<T> clazz, final Duration duration) {

		final Operation op = new Operation(duration);
		final List<T> received = new ArrayList<>(n);

		for (int i = 0; i < n; i++) {
			if (op.isTimeExceeded()) {
				throw new AssertionError("Duration " + duration + " has been exceeded");
			} else {
				final Object tmp = receive(duration);
				if (clazz.isInstance(tmp)) {
					received.add((T) tmp);
				} else {
					throw new AssertionError("Expected message of " + clazz + " but got " + tmp.getClass());
				}
			}
		}

		return received;
	}

	/**
	 * Receive N messages or throws {@link AssertionError} when waiting time of
	 * {@value #THREE_SECONDS} seconds has been exceeded.
	 *
	 * @param n the number of messages to be received
	 * @param duration the max time {@link Duration} to wait for messages
	 * @return The {@link List} of messages.
	 */
	public List<Object> receiveN(final int n) {
		return receiveN(n, THREE_SECONDS_DURATION);
	}

	/**
	 * Receive N messages or throws {@link AssertionError} when provided {@link Duration} has been
	 * exceeded.
	 *
	 * @param n the number of messages to be received
	 * @param duration the max time {@link Duration} to wait for messages
	 * @return The {@link List} of messages.
	 */
	public List<Object> receiveN(final int n, final Duration duration) {

		final Operation op = new Operation(duration);
		final List<Object> received = new ArrayList<>(n);

		for (int i = 0; i < n; i++) {
			if (op.isTimeExceeded()) {
				throw new AssertionError("Duration " + duration + " has been exceeded");
			} else {
				received.add(receive(duration));
			}
		}

		return received;
	}

	/**
	 * Receive one message or throws {@link AssertionError} when waiting time of
	 * {@value #THREE_SECONDS} seconds has been exceeded.
	 *
	 * @return The received message.
	 */
	public Object receive() {
		return receive(THREE_SECONDS_DURATION);
	}

	/**
	 * Receive one message in a given {@link Duration} or throws {@link AssertionError} instead.
	 *
	 * @param duration the max {@link Duration} to wait for the message
	 * @return The received message.
	 */
	public Object receive(final Duration duration) {

		final Object received;
		try {
			received = messages.poll(duration.toMillis(), MILLISECONDS);
		} catch (InterruptedException e) {
			throw new IllegalStateException(e);
		}

		if (received == null) {
			throw new AssertionError("Receive duration " + duration + " has been exceeded");
		}

		return received;
	}

	/**
	 * Small utility to test timeouts.
	 */
	static class Operation {

		final long start = currentTimeMillis();
		final long duration;

		public Operation(final Duration duration) {
			this.duration = duration.toMillis();
		}

		public boolean isTimeExceeded() {
			return currentTimeMillis() - start > duration;
		}
	}

	/**
	 * Internal {@link Actor} receiving messages.
	 */
	class ProbeActor extends Actor {

		@Override
		public Receive receive() {
			return super.receive().matchAny(messages::offer);
		}
	}
}
