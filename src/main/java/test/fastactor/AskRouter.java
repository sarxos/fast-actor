package test.fastactor;

import java.util.concurrent.CompletableFuture;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;


class AskRouter extends Actor {

	static final class Ask<R> {

		final CompletableFuture<R> completion = new CompletableFuture<>();
		final Object message;
		final ActorRef target;

		public Ask(final Object message, final ActorRef target) {
			this.message = message;
			this.target = target;
		}

		@SuppressWarnings("unchecked")
		void complete(final Object result) {
			completion.complete((R) result);
		}
	}

	static final Props<?> ASK_ACTOR = Props.create(AskRoutee::new);
	static final AskDone I_AM_DONE = new AskDone();

	final LongArrayList free = new LongArrayList();
	final LongOpenHashSet busy = new LongOpenHashSet();

	@Override
	public Receive receive() {
		return super.receive()
			.match(Ask.class, this::onAsk)
			.match(AskDone.class, this::onAskDone);
	}

	private void onAsk(final Ask<?> ask) {

		final var sender = context().self();
		final var ref = getFreeUuidOrCreateNewRoutee();
		final var uuid = ref.uuid();

		busy.add(uuid);
		ref.tell(ask, sender);
	}

	private void onAskDone(final AskDone done) {

		final var uuid = context()
			.sender()
			.uuid();

		busy.remove(uuid);
		free.push(uuid);
	}

	private ActorRef getFreeUuidOrCreateNewRoutee() {

		final var context = context();

		if (free.isEmpty()) {
			return context.actorOf(ASK_ACTOR);
		}

		final var uuid = free.popLong();
		final var system = context.system();

		// XXX PERF rework this class to hold ActorRef instead of long uuids

		return system.find(uuid);

	}

	static class AskDone {
	}

	static class AskRoutee extends Actor {

		private Ask<?> ask;

		@Override
		public Receive receive() {
			return super.receive()
				.match(Ask.class, this::onAsk)
				.matchAny(this::onResponse);
		}

		public void onAsk(final Ask<?> ask) {

			this.ask = ask;

			final var message = ask.message;
			final var target = ask.target;
			final var sender = context().self();
			final var system = context().system();

			system.tell(message, target, sender);
		}

		public void onResponse(final Object result) {

			this.ask.complete(result);
			this.ask = null;

			final var parent = context().parent();
			final var sender = context().self();

			parent.tell(I_AM_DONE, sender);
		}
	}
}
