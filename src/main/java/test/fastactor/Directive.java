package test.fastactor;

/**
 * A directive is a piece of code executed on the {@link ActorCell} by the {@link Thread} where
 * {@link Actor} is docked.
 *
 * @author Bartosz Firyn (sarxos)
 */
public interface Directive {

	/**
	 * Execute this directive on the {@link ActorCell} provided in the argument. Execution will
	 * always be done by the {@link ActorThread} where given {@link ActorCell} is docked.
	 * 
	 * @param cell the {@link ActorCell} to execute this directive on
	 */
	void executeOn(final ActorCell<?, ?> cell);
}
