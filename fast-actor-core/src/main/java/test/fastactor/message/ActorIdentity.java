package test.fastactor.message;

import test.fastactor.ActorRef;


public class ActorIdentity {

	private final ActorRef ref;

	public ActorIdentity(ActorRef ref) {
		this.ref = ref;
	}

	public ActorRef ref() {
		return ref;
	}
}
