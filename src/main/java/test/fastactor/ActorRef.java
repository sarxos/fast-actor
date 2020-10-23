package test.fastactor;

import java.util.UUID;


public class ActorRef {

	public static final ActorRef NO_REF = new ActorRef(null, null);

	final ActorSystem system;
	final UUID uuid;

	ActorRef(final ActorSystem system, final UUID uuid) {
		this.system = system;
		this.uuid = uuid;
	}

	public static final ActorRef noSender() {
		return NO_REF;
	}

	public void tell(final Object message) {
		tell(message, noSender());
	}

	public void tell(final Object message, final ActorRef sender) {
		system.tell(message, this.uuid, sender.uuid);
	}

	public void ask(final Object message) {
		ask(message, noSender());
	}

	public void ask(final Object message, final ActorRef sender) {

	}
}
