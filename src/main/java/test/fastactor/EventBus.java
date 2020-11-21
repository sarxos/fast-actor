package test.fastactor;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;

import java.util.IdentityHashMap;
import java.util.Map;
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

	/**
	 * Subscribe {@link ActorRef} to the event type given by the provided {@link Class}.
	 */
	class Subscribe {

		final Class<?> eventType;
		final ActorRef subscriber;

		/**
		 * @param type the event {@link Class} to subscribe
		 * @param subscriber the {@link ActorRef} of the subscribing actor
		 */
		public Subscribe(final Class<?> type, final ActorRef subscriber) {
			this.eventType = type;
			this.subscriber = subscriber;
		}

		public Class<?> getEventType() {
			return eventType;
		}

		public ActorRef getSubscriber() {
			return subscriber;
		}
	}

	/**
	 * The confirmation that {@link Subscribe} was done successfully.
	 */
	class SubscribeAck {

		final Subscribe subscribe;

		public SubscribeAck(final Subscribe subscribe) {
			this.subscribe = subscribe;
		}

		public Subscribe getSubscribe() {
			return subscribe;
		}
	}

	/**
	 * Unsubscribe {@link ActorRef} from the event type given by the provided {@link Class}.
	 */
	class Unsubscribe {

		final Class<?> eventType;
		final ActorRef subscriber;

		/**
		 * @param type the event {@link Class} to unsubscribe
		 * @param subscriber the {@link ActorRef} of the subscribing actor
		 */
		public Unsubscribe(final Class<?> type, final ActorRef subscriber) {
			this.eventType = type;
			this.subscriber = subscriber;
		}

		public Class<?> getEventType() {
			return eventType;
		}

		public ActorRef getSubscriber() {
			return subscriber;
		}
	}

	class UnsubscribeAck {

		final Unsubscribe unsubscribe;

		public UnsubscribeAck(final Unsubscribe unsubscribe) {
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

	private static final LongOpenHashSet NO_SUBSCRIBERS = new LongOpenHashSet(0) {

		private static final long serialVersionUID = 1L;

		@Override
		public void forEach(final LongConsumer action) {
			// do nothing, this is empty set
		}
	};

	private static final Function<Class<?>, LongOpenHashSet> NEW_SUBSCRIBERS_SET = $ -> new LongOpenHashSet();

	private final Map<Class<?>, LongOpenHashSet> subscriptions = new IdentityHashMap<>();
	private final Map<Class<?>, Class<?>[]> classAncestorsCache = new IdentityHashMap<>();

	@Override
	public Receive receive() {
		return super.receive()
			.match(Subscribe.class, this::onSubscribe)
			.match(Unsubscribe.class, this::onUnsubscribe)
			.match(Event.class, this::onEvent);
	}

	void onSubscribe(final Subscribe subscribe) {

		final var self = self();
		final var type = subscribe.eventType;
		final var subscriber = subscribe.subscriber;

		getSubscribersFor(type).add(subscriber.uuid());

		subscriber.tell(new SubscribeAck(subscribe), self);
	}

	void onUnsubscribe(final Unsubscribe unsubscribe) {

		final var self = self();
		final var type = unsubscribe.eventType;
		final var subscriber = unsubscribe.subscriber;
		final var uuid = subscriber.uuid();

		getSubscribersFor(type).remove(uuid);

		subscriber.tell(new UnsubscribeAck(unsubscribe), self);
	}

	void onEvent(final Event event) {

		final var eventValue = event.value;
		final var eventClass = eventValue.getClass();
		final var classes = classAncestorsCache.computeIfAbsent(eventClass, this::computeAncestors);

		for (final Class<?> clazz : classes) {
			checkIfSubscribedAndSend(clazz, event);
		}
	}

	private void checkIfSubscribedAndSend(final Class<?> clazz, final Event event) {
		subscriptions
			.getOrDefault(clazz, NO_SUBSCRIBERS)
			.forEach(send(event));
	}

	private LongConsumer send(final Event event) {

		final var value = event.value;
		final var emitter = event.emitter;

		return subscriber -> system()
			.find(subscriber)
			.tell(value, emitter);
	}

	private LongOpenHashSet getSubscribersFor(final Class<?> type) {
		return subscriptions.computeIfAbsent(type, NEW_SUBSCRIBERS_SET);
	}

	private Class<?>[] computeAncestors(final Class<?> clazz) {
		return ancestorsStream(clazz)
			.distinct()
			.collect(toList())
			.toArray(Class[]::new);
	}

	private Stream<Class<?>> ancestorsStream(final Class<?> clazz) {

		if (clazz == null) {
			return Stream.empty();
		}

		final var superclass = concat(Stream.of(clazz), ancestorsStream(clazz.getSuperclass()));
		final var interfaces = stream(clazz.getInterfaces()).flatMap(this::ancestorsStream);

		return concat(superclass, interfaces);
	}
}
