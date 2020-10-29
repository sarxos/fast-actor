package test.fastactor;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import test.fastactor.DeathWatch.Terminated;
import test.fastactor.DeathWatch.WatchAck;


@SuppressWarnings("boxing")
public class DeathWatchTest {

	@Test
	public void test_watch() throws InterruptedException, ExecutionException, TimeoutException {

		final var watchack = new CompletableFuture<WatchAck>();
		final var terminated = new CompletableFuture<Terminated>();

		class WatcherActor extends Actor {

			@Override
			public Receive receive() {
				return super.receive()
					.match(ActorRef.class, context()::watch)
					.match(WatchAck.class, watchack::complete)
					.match(Terminated.class, terminated::complete);
			}
		}

		class WatchedActor extends Actor {
		}

		final var system = ActorSystem.create("xyz");
		final ActorRef watcher = system.actorOf(Props.create(WatcherActor::new));
		final ActorRef watched = system.actorOf(Props.create(WatchedActor::new));

		await().until(() -> system.cells.containsKey(watched.uuid));
		await().until(() -> system.cells.containsKey(watcher.uuid));

		watcher.tell(watched);

		final WatchAck ack = watchack.get(500, TimeUnit.MILLISECONDS);

		assertEquals(watched, ack.ref());

		system.stop(watched);

		final Terminated term = terminated.get(500, TimeUnit.MILLISECONDS);

		assertEquals(watched, term.ref());

	}

}
