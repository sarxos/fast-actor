package test.fastactor;

import java.util.UUID;


public class ActorRef<T extends Actor<M>, M> {

	public static final ActorRef<Actor<Void>, Void> NO_REF = new ActorRef<>(null, null);

	final ActorSystem system;
	final UUID uuid;

	public ActorRef(final ActorSystem system, final UUID uuid) {
		this.system = system;
		this.uuid = uuid;
	}

	public static final ActorRef<Actor<Void>, Void> noSender() {
		return NO_REF;
	}

	public <X> void tell(final M message, final ActorRef<? extends Actor<X>, X> sender) {
		system.tell(message, sender.uuid, this.uuid);
	}
}
