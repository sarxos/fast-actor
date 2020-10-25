package test.fastactor;

import java.util.Objects;


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

	@Override
	public String toString() {
		return "fa://" + system.getName() + "/" + uuid;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + system.name.hashCode();
		result = prime * result + Long.hashCode(uuid);
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

	private boolean equals0(ActorRef ref) {
		return Objects.equals(system.name, ref.system.name) && uuid == ref.uuid;
	}
}
