import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.LongAdder;

import test.fastactor.Actor;
import test.fastactor.ActorSystem;
import test.fastactor.DeadLetters.DeadLetter;
import test.fastactor.Directive;
import test.fastactor.Props;
import test.fastactor.Receive;
import test.fastactor.dsl.Base;
import test.fastactor.dsl.Events;


public class Sender {

	private static final int N = 1_000_000;

	public static void main(String[] args) throws InterruptedException, ExecutionException {

		final LongAdder adder = new LongAdder();

		class Conductor extends Actor implements Base, Events {

			@Override
			public void preStart() {
				subscribeEvent(DeadLetter.class);
			}

			@Override
			public Receive receive() {
				return super.receive()
					.match(DeadLetter.class, this::onDeadLetter);
			}

			private void onDeadLetter(final DeadLetter dl) {
				System.out.println("Dead letter " + dl.getMessage() + " from " + dl.getSender() + " to " + dl.getTarget());
			}
		}

		class TestActor extends Actor implements Base {

			long count = 0;

			@Override
			public Receive receive() {
				return super.receive()
					.match(Integer.class, this::onInteger);
			}

			private void onInteger(final Integer i) {

				reply(i);

				if (count++ >= 1_000) {
					adder.add(count);
					count = 0;
				}
			}
		}

		final var system = ActorSystem.create("xyz");

		final var conductor = system.actorOf(Props.create(Conductor::new));

		System.out.println("conductor is " + conductor);

		conductor
			.ask(Directive.IDENTIFY)
			.toCompletableFuture()
			.get();

		final var ref1 = system.actorOf(Props.create(TestActor::new));
		final var ref2 = system.actorOf(Props.create(TestActor::new));

		long last = System.currentTimeMillis();

		for (int i = 0; i < N; i++) {
			if (i % 2 == 0) {
				ref1.tell(Integer.valueOf(i), ref2);
			} else {
				ref2.tell(Integer.valueOf(i), ref1);
			}
		}

		for (;;) {

			Thread.sleep(1000);

			long now = System.currentTimeMillis();
			long howMany = adder.sumThenReset();
			double rate = 1000 * (double) howMany / (now - last);

			last = now;

			System.out.format("%d\t%d\tmsg/s\n", Long.valueOf(now), Long.valueOf(Math.round(rate)));
		}

		// System.out.println("done");
	}
}
