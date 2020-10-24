package test.fastactor;

public class Envelope<M> {

	/**
	 * Value to be delivered.
	 */
	final M message;

	/**
	 * Sender ID.
	 */
	final long sender;

	/**
	 * Target ID.
	 */
	final long target;

	Envelope(final M message, final long sender, final long target) {
		this.message = message;
		this.sender = sender;
		this.target = target;
	}

	@SuppressWarnings("unchecked")
	public Envelope<? extends Directive> asDirective() {
		return (Envelope<? extends Directive>) this;
	}
}
