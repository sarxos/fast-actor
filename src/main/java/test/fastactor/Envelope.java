package test.fastactor;

class Envelope {

	/**
	 * Value to be delivered.
	 */
	final Object message;

	/**
	 * Sender ID.
	 */
	final ActorRef sender;

	/**
	 * Target ID.
	 */
	final ActorRef target;

	Envelope(final Object message, final ActorRef sender, final ActorRef target) {
		this.message = message;
		this.sender = sender;
		this.target = target;
	}
}
