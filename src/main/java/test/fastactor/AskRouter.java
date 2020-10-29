package test.fastactor;

import java.util.concurrent.CompletableFuture;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;


class AskRouter extends Actor {

	public static final class Ask {

		final CompletableFuture<Object> future = new CompletableFuture<>();
		final Object message;
		final long target;

		public Ask(final Object message, final long target) {
			this.message = message;
			this.target = target;
		}

		void complete(final Object result) {
			future.complete(result);
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

	private void onAsk(final Ask ask) {

		final var system = context().system();
		final var sender = context().self().uuid;
		final var uuid = getFreeUuidOrCreateNewRoutee();

		busy.add(uuid);

		system.tell(ask, uuid, sender);
	}

	private void onAskDone(final AskDone done) {

		final var ref = context().sender();
		final var uuid = ref.uuid;

		busy.remove(uuid);
		free.push(uuid);
	}

	private long getFreeUuidOrCreateNewRoutee() {

		if (!free.isEmpty()) {
			return free.popLong();
		}

		final var ref = context().actorOf(ASK_ACTOR);
		final var uuid = ref.uuid;

		return uuid;

	}

	static class AskDone {
	}

	static class AskRoutee extends Actor {

		private Ask ask;

		@Override
		public Receive receive() {
			return super.receive()
				.match(Ask.class, this::onAsk)
				.matchAny(this::onResponse);
		}

		public void onAsk(final Ask ask) {

			this.ask = ask;

			final var message = ask.message;
			final var target = ask.target;
			final var sender = context().uuid();
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
