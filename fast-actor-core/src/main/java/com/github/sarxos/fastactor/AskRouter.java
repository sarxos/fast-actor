package com.github.sarxos.fastactor;

import java.util.concurrent.CompletableFuture;

import com.github.sarxos.fastactor.dsl.Base;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;


class AskRouter extends Actor implements Base {

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

		final var ref = getFreeUuidOrCreateNewRoutee();
		final var uuid = ref.uuid();

		busy.add(uuid);

		tell(ask, ref);
	}

	private void onAskDone(final AskDone done) {

		final var uuid = sender().uuid();

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
		// so we can avoid calling system::find

		return system.find(uuid);
	}

	static final class AskDone {
	}

	static final class AskRoutee extends Actor implements Base {

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

			tell(message, target);
		}

		public void onResponse(final Object result) {

			this.ask.complete(result);
			this.ask = null;

			tell(I_AM_DONE, parent());
		}
	}
}
