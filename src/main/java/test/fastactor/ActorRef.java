package test.fastactor;

import java.util.UUID;


public class ActorRef<M> {

	public static final ActorRef<Void> NO_REF = new ActorRef<>(null, null);

	final ActorSystem system;
	final UUID uuid;

	public ActorRef(final ActorSystem system, final UUID uuid) {
		this.system = system;
		this.uuid = uuid;
	}

	public static final ActorRef<Void> noSender() {
		return NO_REF;
	}

	@SuppressWarnings("unchecked")
	<X> ActorRef<X> cast() {
		return (ActorRef<X>) this;
	}

	public void tell(final M message) {
		tell(message, noSender());
	}

	public void tell(final M message, final ActorRef<?> sender) {
		system.tell(message, sender.uuid, this.uuid);
	}

	public void ask(final M message) {
		ask(message, noSender());
	}

	public void ask(final M message, final ActorRef<?> sender) {

	}
}
