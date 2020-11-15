package test.fastactor;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import test.fastactor.DeathWatch.Terminated;
import test.fastactor.DeathWatch.UnwatchAck;
import test.fastactor.DeathWatch.WatchAck;


public class DeathWatchTest {

	@Test
	public void test_watch() throws InterruptedException, ExecutionException, TimeoutException {

		final var watchack = new CompletableFuture<WatchAck>();
		final var terminated = new CompletableFuture<Terminated>();

		class WatcherActor extends Actor {

			@Override
			public Receive receive() {
				return super.receive()
					.match(Watch.class, w -> context().watch(w.ref))
					.match(WatchAck.class, watchack::complete)
					.match(Terminated.class, terminated::complete);
			}
		}

		class WatchedActor extends Actor {
		}

		final var system = ActorSystem.create("xyz");
		final var watcher = system.actorOf(Props.create(WatcherActor::new));
		final var watched = system.actorOf(Props.create(WatchedActor::new));

		watcher.ask(Directive.IDENTIFY).toCompletableFuture().get(500, MILLISECONDS);
		watched.ask(Directive.IDENTIFY).toCompletableFuture().get(500, MILLISECONDS);

		watcher.tell(new Watch(watched));

		final WatchAck ack = watchack.get(500, TimeUnit.MILLISECONDS);

		assertEquals(watched, ack.ref());

		system.stop(watched);

		final Terminated term = terminated.get(500, TimeUnit.MILLISECONDS);

		assertEquals(watched, term.ref());
	}

	@Test
	public void test_unwatch() throws InterruptedException, ExecutionException, TimeoutException {

		final var watchAck = new CompletableFuture<WatchAck>();
		final var unwatchAck = new CompletableFuture<UnwatchAck>();
		final var terminated = new CompletableFuture<Terminated>();

		class WatcherActor extends Actor {

			@Override
			public Receive receive() {
				return super.receive()
					.match(Watch.class, w -> context().watch(w.ref))
					.match(Unwatch.class, u -> context().unwatch(u.ref))
					.match(WatchAck.class, watchAck::complete)
					.match(UnwatchAck.class, unwatchAck::complete)
					.match(Terminated.class, terminated::complete);
			}
		}

		class WatchedActor extends Actor {
		}

		final var system = ActorSystem.create("xyz");
		final var watcher = system.actorOf(Props.create(WatcherActor::new));
		final var watched = system.actorOf(Props.create(WatchedActor::new));

		watcher.ask(Directive.IDENTIFY).toCompletableFuture().get(500, MILLISECONDS);
		watched.ask(Directive.IDENTIFY).toCompletableFuture().get(500, MILLISECONDS);

		watcher.tell(new Watch(watched));
		watchAck.get(500, TimeUnit.MILLISECONDS);

		watcher.tell(new Unwatch(watched));
		unwatchAck.get(500, TimeUnit.MILLISECONDS);

		system.stop(watched);

		assertThrows(TimeoutException.class, () -> terminated.get(500, MILLISECONDS));
		assertNull(system.find(watched.uuid));
	}

	class Watch {

		final ActorRef ref;

		public Watch(ActorRef ref) {
			this.ref = ref;
		}
	}

	class Unwatch {

		final ActorRef ref;

		public Unwatch(ActorRef ref) {
			this.ref = ref;
		}
	}
}
