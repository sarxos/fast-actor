package test.fastactor.dsl;

import test.fastactor.EventBus;


public interface Events extends InternalContext {

	/**
	 * Subscribe to the event bus.
	 *
	 * @param type the type of event to be subscribed
	 */
	default void subscribe(final Class<?> type) {

		final var self = context().self();
		final var system = context().system();
		final var bus = system.refForEventBus();

		bus.tell(new EventBus.Subscribe(type, self), self);
	}

	/**
	 * Unsubscribe from the event bus.
	 *
	 * @param type the event type to unsubscribe
	 */
	default void unsubscribe(final Class<?> type) {

		final var self = context().self();
		final var system = context().system();
		final var bus = system.refForEventBus();

		bus.tell(new EventBus.Unsubscribe(type, self), self);
	}

	/**
	 * Emit event into event bus.
	 *
	 * @param event the event to be emitted
	 */
	default void emit(final Object event) {

		final var self = context().self();
		final var system = context().system();
		final var bus = system.refForEventBus();

		bus.tell(new EventBus.Event(event, self), self);
	}
}
