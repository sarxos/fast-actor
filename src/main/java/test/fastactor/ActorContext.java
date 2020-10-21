package test.fastactor;

import java.util.Deque;
import java.util.function.Consumer;


/**
 * The actor context representing the {@link ActorCell} this actor is running in.
 *
 * @author Bartosz Firyn (sarxos)
 *
 * @param <M> the message class the actor and corresponding cell can receive
 */
public interface ActorContext<M> {

	/**
	 * Get self actor-reference, i.e. the {@link ActorRef} which can be used to send message to self
	 * (this very {@link Actor}).
	 *
	 * @return Self {@link ActorRef} which can be used to send message to self
	 */
	ActorRef<M> self();

	/**
	 * Get message sender actor-reference, i.e. the {@link ActorRef} which can be used to send
	 * message to actor which sent current message to us.
	 *
	 * @return Sender {@link ActorRef} which can be used to reply to message originator
	 */
	ActorRef<?> sender();

	/**
	 * Get parent actor reference, i.e. the {@link ActorRef} which can be used to send message to
	 * parent actor (the one who created this very actor).
	 *
	 * @return Parent {@link ActorRef} which can be used to send message to the parent actor
	 */
	ActorRef<?> parent();

	/**
	 * Spawn new actor from {@link Props}. The newly spawned actor will become a child of this very
	 * actor, and this very actor will become the parent of a newly created child.
	 *
	 * @param <A> the type of the child actor
	 * @param <X> the type of the message the child actor can receive
	 * @param props the {@link Props} object used to create child actor
	 * @return The {@link ActorRef} which can be used to send message to newly created actor
	 */
	public <A extends Actor<X>, X> ActorRef<X> actorOf(final Props<A> props);

	/**
	 * @return List of children's {@link ActorRef}
	 */
	Deque<ActorRef<?>> children();

	/**
	 * @param behaviour the reference to new message {@link Consumer}
	 */
	void become(final Consumer<M> behaviour);

	/**
	 * Revert back to the previous message {@link Consumer}
	 */
	void unbecome();

	/**
	 * Immediately stops the actor. After the actor is stopped it will not accept any more messages.
	 * Both {@link Actor}, {@link ActorCell} and all corresponding resources will be dereferences
	 * shortly after. This will cause {@link Actor} to be removed from the {@link ActorSystem}.
	 */
	void stop();

	/**
	 * @return The {@link ActorSystem} this {@link Actor} resides in.
	 */
	ActorSystem system();
}
