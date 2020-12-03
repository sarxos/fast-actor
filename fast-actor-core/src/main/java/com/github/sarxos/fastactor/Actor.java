package com.github.sarxos.fastactor;

public abstract class Actor {

	private final ActorContext context = ActorCell.getActiveContext();

	public Receive receive() {
		return new Receive();
	}

	public ActorContext context() {
		return context;
	}

	public void preStart() {
		// please override when necessary
	}

	public void postStop() {
		// please override when necessary
	}
}
