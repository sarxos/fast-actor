package test.fastactor;

import java.util.concurrent.CompletableFuture;

import test.fastactor.InternalTempGuardian.Ask;


class InternalTempGuardian extends Actor {

	public static final class Ask {

		final CompletableFuture<Object> completable = new CompletableFuture<>();
		final Object message;
		final ActorRef target;

		public Ask(final Object message, final ActorRef target) {
			this.message = message;
			this.target = target;
		}

		void complete(final Object result) {
			completable.complete(result);
		}
	}

	@Override
	public ReceiveBuilder receive() {
		return super.receive()
			.match(Ask.class, this::onAsk);
	}

	private void onAsk(final Ask ask) {
		context().actorOf(Props.create(() -> new InternalTempActor(ask)));
	}
}

class InternalTempActor extends Actor {

	final Ask ask;

	public InternalTempActor(final Ask ask) {
		this.ask = ask;
	}

	@Override
	public void preStart() {
		ask.target.tell(ask.message, context().self());
	}

	@Override
	public ReceiveBuilder receive() {
		return super.receive()
			.matchAny(this::onResponse);
	}

	public void onResponse(final Object result) {
		ask.complete(result);
		context().stop();
	}

}
