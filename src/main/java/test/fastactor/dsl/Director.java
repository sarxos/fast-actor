package test.fastactor.dsl;

import test.fastactor.ActorRef;
import test.fastactor.Directive;


public interface Director extends Context {

	default void tell(final Directive directive, final ActorRef target) {
		tell(directive, target, context().self());
	}

	default void tell(final Directive directive, final ActorRef target, final ActorRef sender) {
		target.tell(directive, sender);
	}
}
