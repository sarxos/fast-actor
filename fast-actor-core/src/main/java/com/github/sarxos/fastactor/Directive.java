package com.github.sarxos.fastactor;

import static com.github.sarxos.fastactor.Directive.ExecutionMode.RUN_IMMEDIATELY;


/**
 * A directive is a very special kind of message. Existing {@link ActorCell} cannot rejected a
 * {@link Directive} like they can do with the ordinary message. A {@link Directive}, however, can
 * be failed when target {@link ActorCell} does not exist or when it is not docked on the
 * {@link ActorThread}. A {@link Directive} implementor can specify a piece of code to be executed
 * whenever one of these situations occur. In case when it's successfully delivered to the target
 * cell, the {@link Directive#execute(ActorCell)} is invoked. Otherwise the
 * {@link Directive#failed()} is invoked. In both cases the execution is done by the very same
 * thread that processes ordinary messages.
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
	default void execute(final ActorCell<? extends Actor> cell) {
		// do nothing by default, but feel free to override
	}

	/**
	 * Run when target {@link ActorCell} has not been found or when it is dead.
	 */
	default void failed() {
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
		return RUN_IMMEDIATELY;
	}

	final Directive IDENTIFY = new InternalDirectives.Identify();
	final Directive POISON_PILL = new InternalDirectives.PoisonPill();
}
