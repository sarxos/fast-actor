package test.fastactor;

import static test.fastactor.ActorCell.DeliveryStatus.ACCEPTED;
import static test.fastactor.ActorCell.DeliveryStatus.REJECTED;
import static test.fastactor.ActorCell.ProcessingStatus.COMPLETE;
import static test.fastactor.ActorCell.ProcessingStatus.CONTINUE;
import static test.fastactor.ActorRef.noSender;
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
public class ActorCell<A extends Actor> implements ActorContext, ParentChild, DeathWatch {

	private final LongOpenHashSet children = new LongOpenHashSet(1);
	private final LongOpenHashSet watchers = new LongOpenHashSet(0);
	private final LongOpenHashSet watchees = new LongOpenHashSet(0);
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

		setupParentChildRelation();

		return self;
	}

	void start() {

		this.started = true;

		invokeActorConstructor();
		createReceiver();
		invokeActorPreStart();
	}

	private void createReceiver() {

		final Consumer<Object> receiver = actor
			.receive()
			.create(unhandled);

		become(receiver);
	}

	private void unhandled(final Object message) {
		System.out.println("Unhandled: " + message); // TODO implement this properly
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

		if (hasWatchers()) { // am i watched by someone?
			sendTerminatedToWatchers();
		}

		if (hasWatchees()) { // am i watching someone?
			unwatchAllWatchees();
		}

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

	@Override
	public LongOpenHashSet children() {
		return children;
	}

	@Override
	public
		LongOpenHashSet watchers() {
		return watchers;
	}

	@Override
	public
		LongOpenHashSet watchees() {
		return watchees;
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

	@Override
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
