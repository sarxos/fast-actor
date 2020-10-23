package test.fastactor.dsl;

import test.fastactor.ActorRef;
import test.fastactor.ActorSystem;


public interface Base extends Context {

	default ActorRef self() {
		return context().self();
	}

	default ActorRef sender() {
		return context().sender();
	}

	default void stop() {
		context().stop();
	}

	default ActorSystem system() {
		return context().system();
	}

	default <M> void tell(final M message, final ActorRef target) {
		system().tell(message, target, self());
	}

	default <M> void tell(final M message, final ActorRef target, final ActorRef sender) {
		system().tell(message, target, sender);
	}
}
