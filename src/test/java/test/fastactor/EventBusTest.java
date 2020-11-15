package test.fastactor;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import test.fastactor.EventBus.Subscribe;
import test.fastactor.EventBus.SubscribeAck;
import test.fastactor.dsl.Events;


public class EventBusTest {

	class TestEvent {
	}

	@Test
	public void test_subscribe() throws InterruptedException, ExecutionException, TimeoutException {

		final var subscribed = new CompletableFuture<SubscribeAck>();
		final var received = new CompletableFuture<TestEvent>();

		class SubscriberActor extends Actor implements Events {

			@Override
			public void preStart() {
				subscribeEvent(TestEvent.class);
			}

			@Override
			public Receive receive() {
				return super.receive()
					.match(SubscribeAck.class, subscribed::complete)
					.match(TestEvent.class, received::complete);
			}
		}

		final var system = ActorSystem.create("xyz");
		final var ref = system.actorOf(Props.create(SubscriberActor::new));

		final SubscribeAck ack = subscribed.get(500, MILLISECONDS);
		final Subscribe sub = ack.getSubscribe();

		Assertions.assertEquals(ref, sub.getSubscriber());
		Assertions.assertEquals(TestEvent.class, sub.getEventType());
	}

	@Test
	public void test_emit() throws InterruptedException, ExecutionException, TimeoutException {

		class SubscriberActor extends Actor implements Events {

			final CompletableFuture<TestEvent> completable;
			final CompletableFuture<SubscribeAck> subscribed;

			public SubscriberActor(final CompletableFuture<TestEvent> completable, final CompletableFuture<SubscribeAck> subscribed) {
				this.completable = completable;
				this.subscribed = subscribed;

			}

			@Override
			public void preStart() {
				subscribeEvent(TestEvent.class);
			}

			@Override
			public Receive receive() {
				return super.receive()
					.match(SubscribeAck.class, subscribed::complete)
					.match(TestEvent.class, completable::complete);
			}
		}

		final var system = ActorSystem.create("xyz");

		final var received1 = new CompletableFuture<TestEvent>();
		final var received2 = new CompletableFuture<TestEvent>();
		final var received3 = new CompletableFuture<TestEvent>();
		final var received4 = new CompletableFuture<TestEvent>();
		final var received5 = new CompletableFuture<TestEvent>();

		final var subscribed1 = new CompletableFuture<SubscribeAck>();
		final var subscribed2 = new CompletableFuture<SubscribeAck>();
		final var subscribed3 = new CompletableFuture<SubscribeAck>();
		final var subscribed4 = new CompletableFuture<SubscribeAck>();
		final var subscribed5 = new CompletableFuture<SubscribeAck>();

		system.actorOf(Props.create(() -> new SubscriberActor(received1, subscribed1)));
		system.actorOf(Props.create(() -> new SubscriberActor(received2, subscribed2)));
		system.actorOf(Props.create(() -> new SubscriberActor(received3, subscribed3)));
		system.actorOf(Props.create(() -> new SubscriberActor(received4, subscribed4)));
		system.actorOf(Props.create(() -> new SubscriberActor(received5, subscribed5)));

		subscribed1.get(500, MILLISECONDS);
		subscribed2.get(500, MILLISECONDS);
		subscribed3.get(500, MILLISECONDS);
		subscribed4.get(500, MILLISECONDS);
		subscribed5.get(500, MILLISECONDS);

		final var event = new TestEvent();

		system.emitEvent(event);

		final TestEvent value1 = received1.get(500, MILLISECONDS);
		final TestEvent value2 = received2.get(500, MILLISECONDS);
		final TestEvent value3 = received3.get(500, MILLISECONDS);
		final TestEvent value4 = received4.get(500, MILLISECONDS);
		final TestEvent value5 = received5.get(500, MILLISECONDS);

		Assertions.assertSame(event, value1);
		Assertions.assertSame(event, value2);
		Assertions.assertSame(event, value3);
		Assertions.assertSame(event, value4);
		Assertions.assertSame(event, value5);
	}
}
