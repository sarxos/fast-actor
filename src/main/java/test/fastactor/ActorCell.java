package test.fastactor;

import static test.fastactor.ActorCell.DeliveryStatus.ACCEPTED;
import static test.fastactor.ActorCell.DeliveryStatus.REJECTED;
import static test.fastactor.ActorCell.ProcessingStatus.COMPLETE;
import static test.fastactor.ActorCell.ProcessingStatus.CONTINUE;
import static test.fastactor.ActorRef.noSender;
import static test.fastactor.ActorSystem.ZERO;
import static test.fastactor.Directives.DISCARD;
import static test.fastactor.Directives.STOP;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import test.fastactor.Directive.ExecutionMode;
import test.fastactor.Directives.Stop;
import test.fastactor.dsl.Base;


/**
 * @author Bartosz Firyn (sarxos)
 *
 * @param <A> the actor type
 * @param <M> the message type
 */
public class ActorCell<A extends Actor> implements ActorContext {

	private final LongOpenHashSet children = new LongOpenHashSet(1);
	private final Deque<Consumer<Object>> behaviours = new ArrayDeque<>(0);
	private final long uuid = UidGenerator.next();
	private final Queue<Envelope> inbox = new LinkedList<>();
	private final Consumer<Object> unhandled = this::unhandled;

	private final ActorSystem system;
	private final Props<A> props;
	private final ActorRef parent;
	private final ActorRef self;

	private boolean started = false;
	private boolean dead = false;
	private Actor actor;
	private ActorRef sender;

	ActorCell(final ActorSystem system, final Props<A> props, final long parent) {
		this.system = system;
		this.props = props;
		this.self = new ActorRef(system, uuid);
		this.parent = new ActorRef(system, parent);
	}

	ActorRef setup() {
		new CellSetupProtocol(self(), parent()).initiate();
		return self;
	}

	void start() {
		this.started = true;
		invokeActorConstructor();
		createReceiver();
		invokeActorPreStart();
	}

	@Override
	public <P extends Actor> ActorRef actorOf(final Props<P> props) {
		final ActorRef child = system.actorOf(props, this.uuid);
		children.add(child.uuid);
		return child;
	}

	private void createReceiver() {

		final Consumer<Object> receiver = actor
			.receive()
			.create(unhandled);

		become(receiver);
	}

	private void unhandled(final Object message) {
		// TODO send UnhandledMessage(message) message to the system even stream
	}

	boolean addChild(final ActorRef child) {
		return children.add(child.uuid);
	}

	boolean removeChild(final ActorRef child) {
		return children.remove(child.uuid);
	}

	boolean isActorInitialized() {
		return actor != null;
	}

	private void invokeActorConstructor() {
		ActorContext.setActive(this); // change to stack
		try {
			actor = props.newActor();
		} finally {
			ActorContext.setActive(null);
		}
	}

	private void invokeActorPreStart() {
		if (actor != null) {
			actor.preStart();
		}
	}

	private void invokeActorPostStop() {
		if (actor != null) {
			actor.postStop();
		}
	}

	static enum DeliveryStatus {
		ACCEPTED,
		REJECTED,
	}

	public DeliveryStatus deliver(final Envelope envelope) {
		return runWithSender(envelope.sender, () -> {
			if (envelope.message instanceof Directive) {
				return deliverDirective(envelope);
			} else {
				return deliverMessage(envelope);
			}
		});
	}

	private DeliveryStatus deliverDirective(final Envelope envelope) {

		final var directive = (Directive) envelope.message;

		if (directive.mode() == ExecutionMode.RUN_IMMEDIATELY) {
			directive.approved(this);
		} else {
			inbox.offer(envelope);
		}

		return ACCEPTED;
	}

	private DeliveryStatus deliverMessage(final Envelope envelope) {
		if (dead) {
			return REJECTED;
		} else if (inbox.offer(envelope)) {
			return ACCEPTED;
		} else {
			return REJECTED;
		}
	}

	static enum ProcessingStatus {
		COMPLETE,
		CONTINUE,
	}

	/**
	 * @param throughput how many items in inbox should be processed
	 * @return Return true if all items in inbox has been processed, false otherwise
	 */
	public ProcessingStatus process(final int throughput) {

		if (dead || !started) {
			return COMPLETE;
		}

		for (int i = 0; i < throughput; i++) {
			if (processItem(inbox.poll())) {
				return COMPLETE;
			}
		}

		return CONTINUE;
	}

	private boolean processItem(final Envelope envelope) {

		if (envelope == null) {
			return true; // last message
		}

		return runWithSender(envelope.sender, () -> {

			if (envelope.message instanceof Directive) {
				((Directive) envelope.message).approved(this);
			} else {
				behaviours
					.peek()
					.accept(envelope.message);
			}

			return Boolean.TRUE;
		}).booleanValue();
	}

	@Override
	public void become(final Consumer<Object> behaviour) {
		behaviours.push(behaviour);
	}

	@Override
	public Consumer<Object> unbecome() {
		if (behaviours.size() > 1) {
			return behaviours.pop();
		} else {
			return null;
		}
	}

	/**
	 * Mark cell as stopped so it won't accept more messages. Any message delivered to this cell in
	 * the meantime (if any) will be rejected.
	 */
	@Override
	public void stop() {

		dead = true;

		invokeActorPostStop();

		inbox.clear();
		behaviours.clear();

		actor = null;

		if (hasChildren()) {

			// copy children into new array so we do not leak the cell context to the outside
			// entities (in this case a reference to the children set)

			final var uuids = children.toLongArray();
			final var props = Props.create(() -> new ActorStopCoordinator(self, uuids));

			system.actorOf(props);
		} else {
			self.tell(DISCARD, self);
		}
	}

	private <T> T runWithSender(final long uuid, final Supplier<T> run) {
		this.sender = new ActorRef(system, uuid);
		try {
			return run.get();
		} finally {
			this.sender = null;
		}
	}

	/**
	 * @return True if actor has children, false otherwise
	 */
	private boolean hasChildren() {
		return !children.isEmpty();
	}

	@Override
	public ActorRef self() {
		return self;
	}

	@Override
	public ActorRef parent() {
		return parent;
	}

	@Override
	public ActorRef sender() {
		return sender;
	}

	@Override
	public ActorSystem system() {
		return system;
	}

	public long uuid() {
		return uuid;
	}

	public void reply(final Object message) {
		sender().tell(message, self());
	}

	public String getThreadPoolName() {
		return props.getThreadPoolName();
	}
}

/**
 * A random ID generator. Caller need to take special care to avoid duplicates.
 */
class UidGenerator {

	private static final AtomicLong NEXT_ID = new AtomicLong(0);

	static long next() {
		return NEXT_ID.incrementAndGet();
	}
}

class ActorStopCoordinator extends Actor implements Base {

	private final ActorRef parent;
	private final long[] children;
	private int howManyAlive;

	public ActorStopCoordinator(final ActorRef parent, final long[] children) {
		this.parent = parent;
		this.children = children;
		this.howManyAlive = children.length;
	}

	@Override
	public void preStart() {
		for (final long child : children) {
			tellChildToStop(child);
		}
	}

	private void tellChildToStop(final long child) {
		context().system().tell(STOP, child, parent.uuid);
	}

	@Override
	public ReceiveBuilder receive() {
		return super.receive()
			.match(Stop.Ack.class, this::onStopAck);
	}

	public void onStopAck(final Stop.Ack ack) {
		if (allChildrenDied()) {
			tell(DISCARD, parent, noSender());
			stop();
		}
	}

	private boolean allChildrenDied() {
		return --howManyAlive == 0;
	}
}

interface Directives {

	final static Directive START = new Start();
	final static Directive STOP = new Stop();
	final static Directive DISCARD = new Discard();

	/**
	 * Mark cell as initialized and start accepting messages.
	 */
	class Start implements Directive {
		public @Override void approved(final ActorCell<?> cell) {
			cell.start();
		}
	}

	/**
	 * Stop the cell and reply to the sender that it was indeed stopped.
	 */
	class Stop implements Directive {

		@Override
		public void approved(final ActorCell<?> cell) {
			cell.stop();
			cell.reply(Ack.Instance);
		}

		static enum Ack {
			Instance
		}
	}

	/**
	 * A {@link Directive} to cleanup specified {@link ActorCell}. When executed it will remove the
	 * cell from the system and the thread pool where cell is docked. Make sure to use this
	 * directive whenever cell is no longer needed and can be removed.
	 */
	class Discard implements Directive {

		@Override
		public void approved(final ActorCell<?> cell) {
			final var system = cell.system();
			final var uuid = cell.uuid();
			system.discard(uuid);
		}
	}
}

class CellSetupProtocol implements Protocol {

	final ActorRef child;
	final ActorRef parent;

	public CellSetupProtocol(ActorRef child, ActorRef parent) {
		this.child = child;
		this.parent = parent;
	}

	/**
	 * From child to parent. Tell parent to add child as its own. When parent approves, the
	 * {@link AddChildConfirmation} is send to child. When parent already died or did not exist in
	 * the first place, the {@link Directives#STOP} is send to the child instead.
	 */
	final class AddChild implements Directive {

		@Override
		public void approved(final ActorCell<?> cell) {
			cell.addChild(child);
			child.tell(new AddChildConfirmation(), parent);
		}

		@Override
		public void rejected() {
			child.tell(Directives.STOP, parent);
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
		public void approved(final ActorCell<?> cell) {
			cell.start();
		}

		@Override
		public void rejected() {
			parent.tell(new RemoveChild(), child);
		}
	}

	final class RemoveChild implements Directive {

		@Override
		public void approved(final ActorCell<?> cell) {
			cell.removeChild(child);
		}
	}

	/**
	 * @return True if parent actor is a root, false otherwise
	 */
	private boolean isParentTheRootActor() {
		return parent.uuid == ZERO;
	}

	@Override
	public void initiate() {
		if (isParentTheRootActor()) {
			child.tell(Directives.START, child);
		} else {
			parent.tell(new AddChild(), child);
		}
	}
}
