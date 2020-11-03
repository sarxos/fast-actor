package test.fastactor;

import static java.util.Arrays.stream;
import static java.util.stream.Stream.concat;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.stream.Stream;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import test.fastactor.EventBus.Event;
import test.fastactor.EventBus.Subscribe;
import test.fastactor.EventBus.SubscribeAck;
import test.fastactor.EventBus.Unsubscribe;
import test.fastactor.EventBus.UnsubscribeAck;
import test.fastactor.dsl.Base;


public interface EventBus {

	class Subscribe {

		final Class<?> type;
		final ActorRef subscriber;

		public Subscribe(final Class<?> type, final ActorRef subscriber) {
			this.type = type;
			this.subscriber = subscriber;
		}
	}

	class SubscribeAck {

		final Subscribe subscribe;

		public SubscribeAck(final Subscribe subscribe) {
			this.subscribe = subscribe;
		}
	}

	class Unsubscribe {

		final Class<?> type;
		final ActorRef subscriber;

		public Unsubscribe(final Class<?> type, final ActorRef subscriber) {
			this.type = type;
			this.subscriber = subscriber;
		}
	}

	class UnsubscribeAck {

		final Unsubscribe unsubscribe;

		public UnsubscribeAck(Unsubscribe unsubscribe) {
			this.unsubscribe = unsubscribe;
		}
	}

	class Event {

		final Object value;
		final ActorRef emitter;

		public Event(final Object value, final ActorRef emitter) {
			this.value = value;
			this.emitter = emitter;
		}
	}
}

class EventBusActor extends Actor implements Base {

	private final LongOpenHashSet noSubscription = new LongOpenHashSet(0);
	private final Function<Class<?>, LongOpenHashSet> newSubscribersSet = $ -> new LongOpenHashSet();
	private final Map<Class<?>, LongOpenHashSet> subscriptions = new HashMap<>();

	@Override
	public Receive receive() {
		return super.receive()
			.match(Subscribe.class, this::onSubscribe)
			.match(Unsubscribe.class, this::onUnsubscribe)
			.match(Event.class, this::onEvent);
	}

	void onSubscribe(final Subscribe subscribe) {

		final var self = self();
		final var type = subscribe.type;
		final var subscriber = subscribe.subscriber;

		subscription(type).add(subscriber.uuid);

		subscriber.tell(new SubscribeAck(subscribe), self);
	}

	void onUnsubscribe(final Unsubscribe unsubscribe) {

		final var self = self();
		final var type = unsubscribe.type;
		final var subscriber = unsubscribe.subscriber;

		subscription(type).remove(subscriber.uuid);

		subscriber.tell(new UnsubscribeAck(unsubscribe), self);

	}

	void onEvent(final Event event) {

		final var value = event.value;
		final var clazz = value.getClass();

		ascendantsOf(clazz)
			.distinct()
			.forEach(checkIfSubscribedAndSend(event));
	}

	private Consumer<Class<?>> checkIfSubscribedAndSend(final Event event) {
		return clazz -> subscriptions
			.getOrDefault(clazz, noSubscription)
			.forEach(send(event));
	}

	private LongConsumer send(final Event event) {

		final var value = event.value;
		final var publisher = event.emitter.uuid;

		return subscriber -> system().tell(value, subscriber, publisher);
	}

	private LongOpenHashSet subscription(final Class<?> type) {
		return subscriptions.computeIfAbsent(type, newSubscribersSet);
	}

	private Stream<Class<?>> ascendantsOf(final Class<?> clazz) {

		if (clazz == null) {
			return Stream.empty();
		}

		final var superclass = concat(Stream.of(clazz), ascendantsOf(clazz.getSuperclass()));
		final var interfaces = stream(clazz.getInterfaces()).flatMap(this::ascendantsOf);

		return concat(superclass, interfaces);
	}
}
