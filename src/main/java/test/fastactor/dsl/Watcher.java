package test.fastactor.dsl;

import test.fastactor.Actor;
import test.fastactor.ActorRef;


/**
 * An utility interface exposing methods to watch {@link Actor}s.
 *
 * @author Bartosz Firyn (sarxos)
 */
public interface Watcher extends InternalContext {

	/**
	 * @param ref the {@link ActorRef} of the {@link Actor} to start watching
	 */
	default void watch(final ActorRef ref) {
		context().watch(ref);
	}

	/**
	 * @param ref the {@link ActorRef} of the {@link Actor} to stop watching
	 */
	default void unwatch(final ActorRef ref) {
		context().unwatch(ref);
	}
}
