package test.fastactor;

public class Envelope {

	/**
	 * Value to be delivered.
	 */
	final Object message;

	/**
	 * Sender ID.
	 */
	final long sender;

	/**
	 * Target ID.
	 */
	final long target;

	Envelope(final Object message, final long sender, final long target) {
		this.message = message;
		this.sender = sender;
		this.target = target;
	}
}
