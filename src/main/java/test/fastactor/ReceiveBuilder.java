package test.fastactor;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;


@SuppressWarnings({ "rawtypes", "unchecked" })
public class ReceiveBuilder {

	private static final Comparator<Matcher> COMPARATOR = (a, b) -> distance(b.type) - distance(a.type);

	final List<Matcher> consumers = new LinkedList<>();

	/**
	 * Consume message by a given {@link Consumer} when message type matches the prtovide type.
	 *
	 * @param <M> the message type - inferred
	 * @param type the type (a {@link Class}) to match
	 * @param consumer the {@link Consumer} to use when message is instance of a given type
	 * @return This {@link ReceiveBuilder}
	 */
	public <M> ReceiveBuilder match(final Class<M> type, final Consumer<M> consumer) {
		consumers.add(new Matcher(type, consumer));
		return this;
	}

	/**
	 * Consume message by a given {@link Consumer} if message has not been matched by other
	 * matchers.
	 *
	 * @param consumer the {@link Consumer} to invoke.
	 * @return This {@link ReceiveBuilder}
	 */
	public ReceiveBuilder matchAny(final Consumer<Object> consumer) {
		return match(Object.class, consumer);
	}

	/**
	 * @return {@link Consumer} to receive message in {@link Actor}
	 */
	Consumer<Object> create(final Consumer<Object> unhandled) {

		final int size = consumers.size();

		if (size == 1) { // most common case, let's put it first
			return new ConsumerForOne(consumers.get(0), unhandled);
		}
		if (size >= 2) { // less common case
			return new ConsumerForMany(sorted(), unhandled);
		}

		return unhandled;
	}

	Matcher[] sorted() {
		final var candidates = consumers.toArray(Matcher[]::new);
		Arrays.sort(candidates, COMPARATOR);
		return candidates;
	}

	/**
	 * How many classes there is in the inheritance tree of a given type. For example {@link Object}
	 * will have 0 (zero) because it does not inherit from anything. The {@link Integer}, however,
	 * will have distance of 2 because it inherits from two classes - the {@link Number} and
	 * {@link Object}.
	 *
	 * @param clazz the type to calculate distance from
	 * @return Number of classes in inheritance tree
	 */
	private static int distance(Class<?> clazz) {
		for (int i = 0; true; i++) {
			if ((clazz = clazz.getSuperclass()) == null) {
				return i;
			}
		}
	}

	static class Matcher {

		final Class<?> type;
		final Consumer<Object> consumer;

		<M> Matcher(final Class<M> type, final Consumer<M> consumer) {
			this.type = type;
			this.consumer = (Consumer) consumer;
		}
	}

	static abstract class ConsumerFor implements Consumer {

		@Override
		public void accept(final Object message) {
			findConsumerFor(message).accept(message);
		}

		abstract Consumer<Object> findConsumerFor(final Object message);

	}

	static class ConsumerForOne extends ConsumerFor {

		final Matcher matcher;
		final Consumer<Object> unhandled;

		ConsumerForOne(final Matcher matcher, final Consumer<Object> unhandled) {
			this.matcher = matcher;
			this.unhandled = unhandled;
		}

		@Override
		Consumer<Object> findConsumerFor(final Object message) {
			if (matcher.type.isInstance(message)) {
				return matcher.consumer;
			} else {
				return unhandled;
			}
		}
	}

	static class ConsumerForMany extends ConsumerFor {

		final Matcher[] candidates;
		final Consumer<Object> unhandled;

		ConsumerForMany(final Matcher[] candidates, final Consumer<Object> unhandled) {
			this.candidates = candidates;
			this.unhandled = unhandled;
		}

		@Override
		Consumer<Object> findConsumerFor(final Object message) {

			for (final Matcher match : candidates) {
				if (match.type.isInstance(message)) {
					return match.consumer;
				}
			}

			return unhandled;
		}
	}
}
