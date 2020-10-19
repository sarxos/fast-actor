package test.fastactor;

public class Props<A extends Actor<?>> {

	final ActorCreator<A> creator;

	private Props(final ActorCreator<A> creator) {
		this.creator = creator;
	}

	public static <A extends Actor<?>> Props<A> create(final ActorCreator<A> creator) {
		return new Props<A>(creator);
	}

	public A newActor() {
		return creator.create();
	}

	public String getThreadPoolName() {
		return ActorThreadPool.DEFAULT_THREAD_POOL_NAME; // TODO add configurable names
	}
}
