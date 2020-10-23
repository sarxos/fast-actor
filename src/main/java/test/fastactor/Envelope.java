package test.fastactor;

import java.util.UUID;


public class Envelope<M> {

	/**
	 * Value to be delivered.
	 */
	final M message;

	/**
	 * Sender UUID.
	 */
	final UUID sender;

	/**
	 * Target UUID.
	 */
	final UUID target;

	Envelope(final M message, final UUID sender, final UUID target) {
		this.message = message;
		this.sender = sender;
		this.target = target;
	}

	@SuppressWarnings("unchecked")
	public Envelope<? extends Directive> asDirective() {
		return (Envelope<? extends Directive>) this;
	}
}
