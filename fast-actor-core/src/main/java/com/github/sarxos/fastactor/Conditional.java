package com.github.sarxos.fastactor;

public interface Conditional<T> {

	boolean processIf(final T cell);

}
