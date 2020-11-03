package test.fastactor.dsl;

import test.fastactor.ActorRef;
import test.fastactor.ActorSystem;


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
		system().tell(message, target, self());
	}

	default void tell(final Object message, final ActorRef target, final ActorRef sender) {
		system().tell(message, target, sender);
	}
}
