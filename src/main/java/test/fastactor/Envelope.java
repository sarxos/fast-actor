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

	public Envelope(final M value, final UUID sender, final UUID target) {
		this.message = value;
		this.sender = sender;
		this.target = target;
	}
}
