package test.fastactor;

/**
 * A directive is a piece of code executed on the {@link ActorCell} by the {@link Thread} where
 * {@link Actor} is docked.
 *
 * @author Bartosz Firyn (sarxos)
 */
public interface Directive {

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
	void executeOn(final ActorCell<?, ?> cell);

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
