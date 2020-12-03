package com.github.sarxos.fastactor;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

import com.github.sarxos.fastactor.ActorThreadPool.ActorCellInfo;


/**
 * The class representing the basic actor communication channel.
 *
 * @author Bartosz Firyn (sarxos)
 */
public class ActorRef {

	final ActorSystem system;
	final ActorThread thread;
	final long uuid;

	ActorRef(final ActorSystem system, final ActorCellInfo info) {
		this.system = system;
		this.thread = info.thread;
		this.uuid = info.uuid;
	}

	public long uuid() {
		return uuid;
	}

	public ActorThread dispatcher() {
		return thread;
	}

	/**
	 * Send message to the actor represented by this actor-reference. Use no-sender actor-reference
	 * as the sender. Recipient will be unable to reply to this message. Or to be more clear - it
	 * can reply, but the replied message will be forwarded to death-letters.
	 *
	 * @param message the message
	 */
	public void tell(final Object message) {
		tell(message, system.noSender());
	}

	public void tell(final Object message, final ActorRef sender) {
		system.tell(message, this, sender);
	}

	public <R> CompletionStage<R> ask(final Object message) {
		return system.ask(message, this);
	}

	@Override
	public String toString() {
		return "fa://" + system.getName() + "/" + uuid();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + system.name.hashCode();
		result = prime * result + Long.hashCode(uuid());
		return result;
	}

	@Override
	public boolean equals(Object obj) {

		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}

		return equals0((ActorRef) obj);
	}

	private boolean equals0(final ActorRef ref) {

		final boolean sameSystemName = Objects.equals(system.name, ref.system.name);
		final boolean sameUuid = uuid() == ref.uuid();

		return sameSystemName && sameUuid;
	}
}
