package test.fastactor.message;

import test.fastactor.ActorRef;


public class Unhandled {

	private final Object message;
	private final ActorRef target;
	private final ActorRef sender;

	public Unhandled(Object message, ActorRef target, ActorRef sender) {
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
}
