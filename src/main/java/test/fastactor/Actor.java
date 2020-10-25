package test.fastactor;

public abstract class Actor {

	private final ActorContext context = ActorContext.getActive();

	public ReceiveBuilder receive() {
		return new ReceiveBuilder();
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
