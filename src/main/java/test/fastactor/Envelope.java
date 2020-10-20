package test.fastactor;

import java.util.UUID;


public class Envelope<M> implements Deliverable {

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
	private final UUID target;

	public Envelope(final M message, final UUID sender, final UUID target) {
		this.message = message;
		this.sender = sender;
		this.target = target;
	}

	@Override
	public UUID getTarget() {
		return target;
	}
}
