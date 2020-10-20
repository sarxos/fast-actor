package test.fastactor;

/**
 * A directive is a piece of code executed on the {@link ActorCell} by the {@link Thread} where
 * {@link Actor} is docked.
 *
 * @author Bartosz Firyn (sarxos)
 */
public interface Directive extends Deliverable {

	void executeOn(final ActorCell<?, ?> cell);
}
