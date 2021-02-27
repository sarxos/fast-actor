package com.github.sarxos.fastactor;

import static com.github.sarxos.fastactor.ActorSystem.DEFAULT_THREAD_POOL_NAME;


/**
 * This class represents immutable {@link Actor} properties required by the {@link ActorSystem} to
 * construct the {@link Actor} instance and run it. These properties are passed to every incarnation
 * of the created actor. Since this class is immutable, it is thread-safe and can be safely shared
 * between the actors of the actor system and external threads.
 *
 * @author Bartosz Firyn (sarxos)
 * @param <A> the actor type
 */
public class Props<A extends Actor> {

	public static final int RUN_ON_ANY_THREAD = -1;

	final ActorCreator<A> actorCreator;
	final String threadPool;
	final int threadIndex;

	private Props(final ActorCreator<A> creator, final String threadPool, final int threadIndex) {
		this.actorCreator = creator;
		this.threadPool = threadPool;
		this.threadIndex = threadIndex;
	}

	public static <A extends Actor> Props<A> create(final ActorCreator<A> creator) {
		return new Props<A>(creator, DEFAULT_THREAD_POOL_NAME, RUN_ON_ANY_THREAD);
	}

	public Props<A> inThreadPool(final String threadPool) {
		return new Props<A>(actorCreator, threadPool, threadIndex);
	}

	public Props<A> onThreadWithIndex(final int threadIndex) {
		return new Props<A>(actorCreator, threadPool, threadIndex);
	}

	public A newActor() {
		return actorCreator.create();
	}

	public String getThreadPool() {
		return threadPool;
	}

	public int getThreadIndex() {
		return threadIndex;
	}
}
