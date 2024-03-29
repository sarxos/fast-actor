package com.github.sarxos.fastactor;

import java.util.concurrent.atomic.AtomicInteger;


public class ActorThreadFactory {

	final AtomicInteger counter = new AtomicInteger(0);
	final String poolName;

	public ActorThreadFactory(final String poolName) {
		this.poolName = poolName;
	}

	public ActorThread newThread(final ThreadGroup group, final ActorSystem system, final int index) {

		final int number = counter.incrementAndGet();
		final String systemName = system.getName();
		final String name = getNextIncrementalName(systemName, poolName, number);

		final ActorThread thread = new ActorThread(group, system, name, index);
		thread.setDaemon(false);
		thread.setPriority(Thread.NORM_PRIORITY);

		return thread;
	}

	private static String getNextIncrementalName(final String systemName, final String poolName, final int number) {
		return poolName + "-" + String.format("%03d", Integer.valueOf(number)) + "@" + systemName;
	}
}
