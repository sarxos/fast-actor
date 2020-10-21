package test.fastactor;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;


public class ActorTest {

	@Test
	public void test_tell() throws InterruptedException, ExecutionException, TimeoutException {

		final var received = new ArrayList<CompletableFuture<Boolean>>(5);
		received.add(new CompletableFuture<Boolean>());
		received.add(new CompletableFuture<Boolean>());
		received.add(new CompletableFuture<Boolean>());
		received.add(new CompletableFuture<Boolean>());
		received.add(new CompletableFuture<Boolean>());

		final var system = ActorSystem.create("xyz");

		class TestTellActor extends Actor<Integer> {

			@Override
			public void receive(final Integer i) {
				received.get(i).complete(true);
			}
		}

		final var ref = system.actorOf(Props.create(TestTellActor::new));

		for (int i = 0; i < 5; i++) {
			ref.tell(i, ActorRef.noSender());
			received.get(i).get(500, TimeUnit.MILLISECONDS);
		}
	}

	@Test
	public void test_preStart() throws InterruptedException, ExecutionException, TimeoutException {

		final var started = new CompletableFuture<Boolean>();
		final var system = ActorSystem.create("xyz");

		class TestActor extends Actor<Integer> {

			@Override
			public void receive(final Integer number) {
				// do nothing
			}

			@Override
			public void preStart() {
				started.complete(true);
			}
		}

		system.actorOf(Props.create(TestActor::new));

		started.get(500, TimeUnit.MILLISECONDS);
	}

	@Test
	public void test_postStop() throws InterruptedException, ExecutionException, TimeoutException {

		final var stopped = new CompletableFuture<Boolean>();
		final var system = ActorSystem.create("xyz");

		class TestActor extends Actor<Integer> {

			@Override
			public void receive(final Integer number) {
				// do nothing
			}

			@Override
			public void preStart() {
				context().stop();
			}

			@Override
			public void postStop() {
				stopped.complete(true);
			}
		}

		system.actorOf(Props.create(TestActor::new));

		stopped.get(500, TimeUnit.MILLISECONDS);
	}
}
