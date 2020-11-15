package test.fastactor;

public class Sender {

	private static final int N = 1_000_000;

	public static void main(String[] args) throws InterruptedException {

		class TestActor extends Actor {

			long count = 0;
			long last = System.currentTimeMillis();

			@Override
			public Receive receive() {
				return super.receive()
					.match(Integer.class, this::onInteger);
			}

			private void onInteger(final Integer i) {
				var self = context().self();
				var sender = context().sender();
				sender.tell(i, self);

				final int K = N * 1;

				if (count++ >= K) {
					long now = System.currentTimeMillis();
					System.out.println(now + "\t" + (now - last));
					count = 0;
					last = now;
				}
			}
		}

		final var system = ActorSystem.create("xyz");

		final var ref1 = system.actorOf(Props.create(TestActor::new));
		final var ref2 = system.actorOf(Props.create(TestActor::new));

		for (int i = 0; i < N; i++) {
			ref1.tell(Integer.valueOf(i), ref2);
		}

		// System.out.println("done");
	}
}
