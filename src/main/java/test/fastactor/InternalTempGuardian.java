package test.fastactor;

import java.util.concurrent.CompletableFuture;

import test.fastactor.InternalTempGuardian.Ask;


class InternalTempGuardian extends Actor<Ask<?, ?>> {

	public static final class Ask<T, R> {

		final CompletableFuture<R> result = new CompletableFuture<R>();
		final Object message;
		final ActorRef<T> target;

		public Ask(final T message, final ActorRef<T> target) {
			this.message = message;
			this.target = target;
		}
	}

	@Override
	public void receive(final Ask<?, ?> ask) {
		context().actorOf(Props.create(() -> new InternalTempActor(ask)));
	}
}

class InternalTempActor extends Actor<Object> {

	final Ask<?, ?> ask;

	public InternalTempActor(final Ask<?, ?> ask) {
		this.ask = ask;
	}

	@Override
	public void receive(final Object ask) {

	}

}
