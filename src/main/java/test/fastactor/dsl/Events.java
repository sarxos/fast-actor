package test.fastactor.dsl;

import test.fastactor.EventBus;


public interface Events extends InternalContext {

	default void subscribe(final Class<?> type) {

		final var self = context().self();
		final var system = context().system();
		final var bus = system.refForEventBus();

		bus.tell(new EventBus.Subscribe(type, self), self);
	}

	default void unsubscribe(final Class<?> type) {

		final var self = context().self();
		final var system = context().system();
		final var bus = system.refForEventBus();

		bus.tell(new EventBus.Unsubscribe(type, self), self);
	}

	default void emit(final Object value) {

		final var self = context().self();
		final var system = context().system();
		final var bus = system.refForEventBus();

		bus.tell(new EventBus.Event(value, self), self);
	}
}
