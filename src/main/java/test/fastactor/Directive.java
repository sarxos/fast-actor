package test.fastactor;

/**
 * A directive is a very special kind of message. Existing {@link ActorCell} cannot rejected a
 * {@link Directive}. It can, however, be rejected when {@link ActorCell} does not exist or is not
 * yet docked on the {@link ActorThread}. Every {@link Directive} can define a piece of code to be
 * executed in the context of the {@link ActorCell}. The execution is done by the very same
 * {@link ActorThread} where the target {@link ActorCell} is docked on.
 *
 * @author Bartosz Firyn (sarxos)
 */
public interface Directive {

	/**
	 * A execution mode defines how a {@link Directive} should be executed when approved by the
	 * {@link ActorCell}. The execution can be done in two ways. It can either be executed
	 * immediately, or it can wait in the inbox queue for its own turn to be processed.
	 */
	public enum ExecutionMode {

		/**
		 * Run directive immediately.
		 */
		RUN_IMMEDIATELY,

		/**
		 * Put directive at the inbox tail (will be run in the order of messages processing).
		 */
		RUN_IN_ORDER,
	}

	/**
	 * Execute this directive on the {@link ActorCell} provided in the argument. Execution will
	 * always be done by the {@link ActorThread} where given {@link ActorCell} is docked.
	 * 
	 * @param cell the {@link ActorCell} to execute this directive on
	 */
	default void approved(final ActorCell<? extends Actor> cell) {
		// do nothing by default, but feel free to override
	}

	/**
	 * Executed when directive is rejected by the {@link ActorCell} or the {@link ActorCell} has not
	 * been found.
	 */
	default void rejected() {
		// do nothing by default, but feel free to override
	}

	/**
	 * A {@link Directive} can be executed immediately or in the order of incoming messages. The
	 * {@link ExecutionMode#RUN_IMMEDIATELY} mode will cause {@link Directive} to be run
	 * immediately, without moving it to the inbox. The {@link ExecutionMode#RUN_IN_ORDER} will
	 * cause {@link Directive} to be run after messages which came to the {@link ActorCell} first.
	 * <p>
	 * 
	 * Default execution mode is {@link ExecutionMode#RUN_IMMEDIATELY} which means that
	 * {@link Directive} is executed immediately after it's received by the {@link ActorCell}.
	 *
	 * @return A {@link Directive} execution order
	 */
	default ExecutionMode mode() {
		return ExecutionMode.RUN_IMMEDIATELY;
	}
}
