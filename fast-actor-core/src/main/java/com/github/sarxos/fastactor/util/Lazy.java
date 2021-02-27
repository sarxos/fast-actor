package com.github.sarxos.fastactor.util;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;


public final class Lazy<T> implements Supplier<T> {

	private final Supplier<? extends T> supplier;
	private T value;

	private Lazy(final Supplier<? extends T> supplier) {
		this.supplier = supplier;
	}

	/**
	 * Create a {@code Lazy} from a given {@code Supplier}. The supplier is asked only once and the
	 * value is memoized.
	 *
	 * @param <T> type of the lazy value
	 * @param supplier A supplier
	 * @return A new instance of Lazy
	 */
	@SuppressWarnings("unchecked")
	public static <T> Lazy<T> of(final Supplier<? extends T> supplier) {
		if (supplier instanceof Lazy) {
			return (Lazy<T>) supplier;
		} else {
			return new Lazy<>(supplier);
		}
	}

	/**
	 * Evaluates this lazy value and cache it. On subsequent calls return the value.
	 *
	 * @return The value
	 */
	@Override
	public T get() {
		return value == null ? compute() : value;
	}

	private T compute() {
		return requireNonNull(value = supplier.get(), "Lazy value must not be null!");
	}

	/**
	 * Checks, if this lazy value is evaluated.
	 *
	 * @return true, if the value is evaluated, false otherwise.
	 */
	public boolean isEvaluated() {
		return value != null;
	}

	public boolean isEmpty() {
		return value == null;
	}

	@Override
	public boolean equals(Object o) {
		return (o == this) || (o instanceof Lazy && Objects.equals(((Lazy<?>) o).get(), get()));
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(get());
	}

	@Override
	public String toString() {
		return Objects.toString(get());
	}

	public Lazy<T> ifEvaluated(final Consumer<T> consumer) {
		if (isEvaluated()) {
			consumer.accept(value);
		}
		return this;
	}
}
