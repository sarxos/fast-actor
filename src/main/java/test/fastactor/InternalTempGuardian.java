package test.fastactor;

import java.util.concurrent.CompletableFuture;

import test.fastactor.InternalTempGuardian.Ask;


@SuppressWarnings("rawtypes")
class InternalTempGuardian extends Actor<Ask> {

	public static final class Ask<T, R> {

		final CompletableFuture<R> completable = new CompletableFuture<R>();
		final T message;
		final ActorRef<T> target;

		public Ask(final T message, final ActorRef<T> target) {
			this.message = message;
			this.target = target;
		}

		void complete(final R result) {
			completable.complete(result);
		}
	}

	@Override
	@SuppressWarnings({ "unchecked" })
	public void receive(final Ask ask) {
		context().actorOf(Props.create(() -> new InternalTempActor(ask)));
	}
}

class InternalTempActor<T, R> extends Actor<R> {

	final Ask<T, R> ask;

	public InternalTempActor(final Ask<T, R> ask) {
		this.ask = ask;
	}

	@Override
	public void receive(final R result) {
		ask.complete(result);
		context().stop();
	}

	@Override
	public void preStart() {
		ask.target.tell(ask.message, context().self());
	}
}
