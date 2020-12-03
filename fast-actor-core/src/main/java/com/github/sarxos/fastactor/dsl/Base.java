package com.github.sarxos.fastactor.dsl;

import com.github.sarxos.fastactor.ActorRef;
import com.github.sarxos.fastactor.ActorSystem;


public interface Base extends InternalContext {

	default ActorRef self() {
		return context().self();
	}

	default ActorRef sender() {
		return context().sender();
	}

	default long uuid() {
		return self().uuid();
	}

	default void stop() {
		context().stop();
	}

	default ActorSystem system() {
		return context().system();
	}

	default void tell(final Object message, final ActorRef target) {
		target.tell(message, self());
	}

	default void tell(final Object message, final ActorRef target, final ActorRef sender) {
		target.tell(message, sender);
	}

	default void reply(final Object message) {
		sender().tell(message, self());
	}

	default void forward(final Object message, final ActorRef target) {
		target.tell(message, sender());
	}
}
