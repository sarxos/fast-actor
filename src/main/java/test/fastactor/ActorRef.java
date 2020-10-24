package test.fastactor;

public class ActorRef {

	public static final ActorRef NO_REF = new ActorRef(null, ActorSystem.ZERO);

	final ActorSystem system;
	final long uuid;

	ActorRef(final ActorSystem system, final long uuid) {
		this.system = system;
		this.uuid = uuid;
	}

	public static final ActorRef noSender() {
		return NO_REF;
	}

	@Override
	public String toString() {
		return uuid + "@" + system.getName();
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
