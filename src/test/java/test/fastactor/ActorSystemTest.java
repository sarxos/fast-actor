package test.fastactor;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;


public class ActorSystemTest {

	static final class TestActor extends Actor<Integer> {

		final AtomicInteger atomic;

		public TestActor(final AtomicInteger atomic) {
			this.atomic = atomic;
		}

		@Override
		public void receive(final Integer number) {
			atomic.set(number);
		}
	}

	@Test
	public void test_tell() {

		final var atomic = new AtomicInteger(0);
		final var system = ActorSystem.create("xyz");

		final var ref = system.actorOf(Props.create(() -> new TestActor(atomic)));

		for (int i = 0; i < 5; i++) {
			ref.tell(i, ActorRef.noSender());
			await().untilAtomic(atomic, is(i));
		}
	}

}
