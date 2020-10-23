package test.fastactor.dsl;

import test.fastactor.ActorRef;


public interface Tell extends Context {

	default <M> void tell(final M message, final ActorRef target) {
		tell(message, target, context().self());
	}

	default <M> void tell(final M message, final ActorRef target, final ActorRef sender) {
		target.tell(message, sender);
	}
}
