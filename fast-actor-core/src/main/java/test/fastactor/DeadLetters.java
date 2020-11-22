package test.fastactor;

import test.fastactor.DeadLetters.DeadLetter;
import test.fastactor.dsl.Events;


public interface DeadLetters {

	final class DeadLetter {

		private final Object message;
		private final ActorRef target;
		private final ActorRef sender;

		public DeadLetter(final Object message, final ActorRef target, final ActorRef sender) {
			this.message = message;
			this.target = target;
			this.sender = sender;
		}

		public Object getMessage() {
			return message;
		}

		public ActorRef getTarget() {
			return target;
		}

		public ActorRef getSender() {
			return sender;
		}

		@Override
		public String toString() {
			return new StringBuilder(getClass().getName())
				.append("[ message = ")
				.append(message.getClass().getName())
				.append(", from = ")
				.append(sender)
				.append(", to = ")
				.append(target)
				.append("]")
				.toString();
		}
	}
}

class DeadLettersActor extends Actor implements Events {

	@Override
	public Receive receive() {
		return super.receive()
			.match(DeadLetter.class, this::onDeadLetter);
	}

	private void onDeadLetter(final DeadLetter letter) {
		emitEvent(letter);
	}
}
