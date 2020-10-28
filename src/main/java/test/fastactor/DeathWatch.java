package test.fastactor;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import test.fastactor.DeathWatch.Terminated;


public interface DeathWatch extends ActorContext {

	final class WatchAck implements Conditional {

		private final ActorRef ref;

		WatchAck(final ActorRef ref) {
			this.ref = ref;
		}

		public ActorRef ref() {
			return ref;
		}

		@Override
		public boolean processIf(final ActorCell<?> cell) {
			return !cell.watchees().contains(ref.uuid);
		}
	}

	final class UnwatchAck implements Conditional {

		private final ActorRef ref;

		UnwatchAck(final ActorRef ref) {
			this.ref = ref;
		}

		public ActorRef ref() {
			return ref;
		}

		@Override
		public boolean processIf(final ActorCell<?> cell) {
			return cell.watchees().contains(ref.uuid);
		}
	}

	final class Terminated implements Conditional {

		private final ActorRef ref;

		Terminated(final ActorRef ref) {
			this.ref = ref;
		}

		public ActorRef ref() {
			return ref;
		}

		@Override
		public boolean processIf(final ActorCell<?> cell) {
			return cell.watchees().contains(ref.uuid);
		}
	}

	LongOpenHashSet watchers();

	LongOpenHashSet watchees();

	@Override
	ActorRef self();

	@Override
	default ActorRef watch(final ActorRef watchee) {
		new WatchProtocol(self(), watchee).initiate();
		return watchee;
	}

	@Override
	default ActorRef unwatch(final ActorRef watchee) {
		new UnwatchProtocol(self(), watchee).initiate();
		return watchee;
	}

	default boolean addWatcher(final ActorRef ref) {
		return watchers().add(ref.uuid);
	}

	default boolean removeWatcher(final ActorRef ref) {
		return watchers().remove(ref.uuid);
	}

	default boolean hasWatchers() {
		return !watchers().isEmpty();
	}

	default boolean addWatchee(final ActorRef ref) {
		return watchees().add(ref.uuid);
	}

	default boolean removeWatchee(final ActorRef ref) {
		return watchees().remove(ref.uuid);
	}

	default boolean hasWatchees() {
		return !watchees().isEmpty();
	}

	default void sendTerminatedToWatchers() {

		final var self = self();
		final var system = system();
		final var terminated = new Terminated(self);
		final var iterator = watchers().iterator();

		while (iterator.hasNext()) {
			final var target = iterator.nextLong();
			final var sender = self.uuid;
			system.tell(terminated, target, sender);
		}
	}

	default void unwatchAllWatchees() {

		final var watcher = self();
		final var system = system();
		final var iterator = watchees().iterator();

		while (iterator.hasNext()) {
			final var uuid = iterator.nextLong();
			final var watchee = new ActorRef(system, uuid);
			new UnwatchProtocol(watcher, watchee).initiate();
		}
	}
}

class WatchProtocol implements Protocol {

	final ActorRef watcher;
	final ActorRef watchee;

	public WatchProtocol(final ActorRef watcher, final ActorRef watchee) {
		this.watcher = watcher;
		this.watchee = watchee;
	}

	private final class InternalWatch implements Directive {

		@Override
		public void approved(final ActorCell<?> cell) {
			cell.addWatcher(watcher);
			watcher.tell(new InternalWatchAck(), watchee);
			watcher.tell(new DeathWatch.WatchAck(watchee), watchee);
		}

		@Override
		public void failed() {
			watcher.tell(new Terminated(watchee));
		}
	}

	private final class InternalWatchAck implements Directive {

		@Override
		public void approved(final ActorCell<?> cell) {
			cell.addWatchee(watchee);
		}

		@Override
		public void failed() {
			watchee.tell(new InternalUnwatchFallback(), watcher);
		}
	}
	private final class InternalUnwatchFallback implements Directive {

		@Override
		public void approved(final ActorCell<?> cell) {
			cell.removeWatcher(watcher);
		}
	}

	@Override
	public void initiate() {
		watchee.tell(new InternalWatch(), watcher);
	}
}

class UnwatchProtocol implements Protocol {

	final ActorRef watcher;
	final ActorRef watchee;

	public UnwatchProtocol(final ActorRef watcher, final ActorRef watchee) {
		this.watcher = watcher;
		this.watchee = watchee;
	}

	private final class InternalUnwatch implements Directive {

		@Override
		public void approved(final ActorCell<?> cell) {
			cell.removeWatcher(watcher);
			watcher.tell(new InternalUnwatchAck(), watchee);
			watcher.tell(new DeathWatch.UnwatchAck(watchee), watchee);
		}

		@Override
		public void failed() {
			watcher.tell(new InternalUnwatchAck(), watchee);
			watcher.tell(new DeathWatch.UnwatchAck(watchee), watchee);
		}
	}

	private final class InternalUnwatchAck implements Directive {

		@Override
		public void approved(final ActorCell<?> cell) {
			cell.removeWatchee(watchee);
		}

		@Override
		public void failed() {
			watchee.tell(new InternalUnwatchFallback(), watcher);
		}
	}
	private final class InternalUnwatchFallback implements Directive {

		@Override
		public void approved(final ActorCell<?> cell) {
			cell.removeWatcher(watcher);
		}
	}

	@Override
	public void initiate() {
		watchee.tell(new InternalUnwatch(), watcher);
	}
}
