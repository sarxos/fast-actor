package com.github.sarxos.fastactor;

/**
 * Dispatcher is used to deposit message.
 *
 * @author Bartosz Firyn (sarxos)
 */
public interface Dispatcher {

	void deposit(final Envelope envelope);
}
