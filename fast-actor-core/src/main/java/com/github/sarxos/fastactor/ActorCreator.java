package com.github.sarxos.fastactor;

@FunctionalInterface
public interface ActorCreator<T extends Actor> {

	/**
	 * Create actor.
	 *
	 * @return new actor
	 */
	T create();
}
