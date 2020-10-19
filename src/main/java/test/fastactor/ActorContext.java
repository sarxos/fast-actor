package test.fastactor;

import java.util.function.Consumer;


public interface ActorContext<M> {

	ActorRef<? extends Actor<M>, M> self();

	ActorRef<? extends Actor<?>, ?> sender();

	void become(final Consumer<M> behaviour);

	void unbecome();

	ActorSystem system();
}
