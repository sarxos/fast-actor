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


public class Probe {

	private static final Duration THREE_SECONDS = Duration.ofSeconds(3);

	final BlockingQueue<Object> messages = new LinkedBlockingQueue<>();
	final ActorSystem system;
	final ActorRef ref;

	Probe(final ActorSystem system) {
		this.system = system;
		this.ref = system.actorOf(Props.create(ProbeActor::new));
	}

	public ActorRef ref() {
		return ref;
	}

	public <T> T receiveInstanceOf(final Class<T> clazz) {
		return receiveInstanceOf(clazz, THREE_SECONDS);
	}

	@SuppressWarnings("unchecked")
	public <T> T receiveInstanceOf(final Class<T> clazz, final Duration duration) {

		final Object received = receive(duration);

		if (clazz.isInstance(received)) {
			return (T) received;
		}

		throw new AssertionError("Expected message of " + clazz + " but got " + received.getClass());
	}

	public <T> List<T> receiveNInstanceOf(final int n, final Class<T> clazz) {
		return receiveNInstanceOf(n, clazz, THREE_SECONDS);
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

	public List<Object> receiveN(final int n) {
		return receiveN(n, THREE_SECONDS);
	}

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

	public Object receive() {
		return receive(THREE_SECONDS);
	}

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

	static class Operation {

		final long start = System.currentTimeMillis();
		final long duration;

		public Operation(final Duration duration) {
			this.duration = duration.toMillis();
		}

		public boolean isTimeExceeded() {
			return currentTimeMillis() - start > duration;
		}
	}

	class ProbeActor extends Actor {

		@Override
		public Receive receive() {
			return super.receive().matchAny(messages::offer);
		}
	}
}
