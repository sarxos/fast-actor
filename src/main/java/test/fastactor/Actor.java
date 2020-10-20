package test.fastactor;

public abstract class Actor<M> {

	private final ActorContext<M> context = ActorCell.getActiveContext();

	public abstract void receive(final M message);

	public ActorContext<M> context() {
		return context;
	}

	public void preStart() {
		// please override when necessary
	}

	public void postStop() {
		// please override when necessary
	}
}
