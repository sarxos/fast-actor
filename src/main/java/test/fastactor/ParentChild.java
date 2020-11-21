package test.fastactor;

import static test.fastactor.ActorSystem.ZERO_UUID;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;


public interface ParentChild extends ActorContext {

	LongOpenHashSet children();

	@Override
	default <P extends Actor> ActorRef actorOf(final Props<P> props) {
		final ActorRef child = system().actorOf(props, self());
		addChild(child);
		return child;
	}

	default boolean addChild(final ActorRef child) {
		return children().add(child.uuid());
	}

	default boolean removeChild(final ActorRef child) {
		return children().remove(child.uuid());
	}

	/**
	 * @return True if actor has children, false otherwise
	 */
	default boolean hasChildren() {
		return !children().isEmpty();
	}

	default void setupParentChildRelation() {

		final var parent = parent();
		final var child = self();

		new ParentChildProtocol(parent, child).initiate();
	}
}

class ParentChildProtocol implements Protocol {

	final ActorRef parent;
	final ActorRef child;

	public ParentChildProtocol(final ActorRef parent, final ActorRef child) {
		this.parent = parent;
		this.child = child;
	}

	/**
	 * From child to parent. Tell parent to add child as its own. When parent approves, the
	 * {@link AddChildConfirmation} is send to child. When parent already died or did not exist in
	 * the first place, the {@link InternalDirectives#STOP} is send to the child instead.
	 */
	final class AddChild implements Directive {

		@Override
		public void execute(final ActorCell<?> cell) {
			cell.addChild(child);
			child.tell(new AddChildConfirmation(), parent);
		}

		@Override
		public void failed() {
			child.tell(InternalDirectives.STOP, parent);
		}
	}

	/**
	 * From parent to child. Tell the child that it was added to the list of children in the parent
	 * {@link ActorCell}. When this {@link Directive} is approved by the child, its cell will start.
	 * When this {@link Directive} is rejected, the {@link RemoveChild} will be send to the parent
	 * to remove already added child.
	 */
	final class AddChildConfirmation implements Directive {

		@Override
		public void execute(final ActorCell<?> cell) {
			cell.start();
		}

		@Override
		public void failed() {
			parent.tell(new RemoveChild(), child);
		}
	}

	final class RemoveChild implements Directive {

		@Override
		public void execute(final ActorCell<?> cell) {
			cell.removeChild(child);
		}
	}

	/**
	 * @return True if parent actor is a root, false otherwise
	 */
	private boolean isChildTheRootActor() {
		return parent.uuid() == ZERO_UUID;
	}

	@Override
	public void initiate() {
		if (isChildTheRootActor()) {
			child.tell(InternalDirectives.START, child);
		} else {
			parent.tell(new AddChild(), child);
		}
	}
}
