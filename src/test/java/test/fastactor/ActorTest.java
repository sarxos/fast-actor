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
	public void test_preStartHundreds() throws InterruptedException, ExecutionException, TimeoutException {

		final var started = new ArrayList<CompletableFuture<Boolean>>();
		final var system = ActorSystem.create("xyz");

		class TestActor extends Actor<Integer> {

			final CompletableFuture<Boolean> started;

			public TestActor(final CompletableFuture<Boolean> started) {
				this.started = started;
			}

			@Override
			public void receive(final Integer number) {
				// do nothing
			}

			@Override
			public void preStart() {
				started.complete(true);
			}
		}

		for (int i = 0; i < 500; i++) {
			final var s = new CompletableFuture<Boolean>();
			started.add(s);
			system.actorOf(Props.create(() -> new TestActor(s)));
		}

		for (int i = 0; i < 500; i++) {
			started.get(i).get(500, TimeUnit.MILLISECONDS);
		}
	}
}
