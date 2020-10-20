package test.fastactor;

import java.util.Deque;
import java.util.function.Consumer;


public interface ActorContext<M> {

	ActorRef<M> self();

	ActorRef<?> sender();

	ActorRef<?> parent();

	public <P extends Actor<X>, X> ActorRef<X> actorOf(final Props<P> props);

	Deque<ActorRef<?>> children();

	void become(final Consumer<M> behaviour);

	void unbecome();

	ActorSystem system();
}
