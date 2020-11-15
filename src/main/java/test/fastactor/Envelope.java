package test.fastactor;

class Envelope {

	/**
	 * Value to be delivered.
	 */
	final Object message;

	/**
	 * Target ID.
	 */
	final ActorRef target;

	/**
	 * Sender ID.
	 */
	final ActorRef sender;

	Envelope(final ActorRef target, final Object message, final ActorRef sender) {
		this.message = message;
		this.target = target;
		this.sender = sender;
	}

	@Override
	public String toString() {

		final String envelopeClass = getClass().getName();
		final String messageClass = message.getClass().getName();
		final String senderRef = sender.toString();
		final String targetRef = target.toString();

		final int capacity = 0
			+ envelopeClass.length()
			+ messageClass.length()
			+ senderRef.length()
			+ targetRef.length()
			+ 12 + 9 + 7 + 1;

		return new StringBuilder(capacity)
			.append(envelopeClass)
			.append("[ message = ")
			.append(messageClass)
			.append(", from = ")
			.append(sender)
			.append(", to = ")
			.append(target)
			.append("]")
			.toString();
	}
}
