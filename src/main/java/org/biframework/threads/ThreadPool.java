package org.biframework.threads;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

/**
 * A thread pool that is trying to copy the apache process management.
 *
 * Should we remove this in favor of Doug Lea's thread package?
 *
 * @author Gal Shachor
 * @author Yoav Shapira <yoavs@apache.org>
 */
public class ThreadPool {

	/*
	 * Default values ...
	 */
	public static final int MAX_THREADS = 100;// 最大线程数
	public static final int MAX_THREADS_MIN = 5;// 最大线程数的最小值
	public static final int MAX_SPARE_THREADS = 30;// 最大空闲数
	public static final int MIN_SPARE_THREADS = 2;// 最小空闲数
	public static final int WORK_WAIT_TIMEOUT = 60 * 1000;// 超时时间

	/**
	 * Where the threads are held.
	 */
	protected ControlRunnable[] pool = null;

	/**
	 * A monitor thread that monitors the pool for idel threads. 监听线程
	 */
	protected MonitorRunnable monitor;

	/**
	 * Max number of threads that you can open in the pool. 最大线程
	 */
	protected int maxThreads;

	/**
	 * Min number of idel threads that you can leave in the pool. 线程池中的最小线程数
	 * 最小空闲线程
	 */
	protected int minSpareThreads;

	/**
	 * Max number of idel threads that you can leave in the pool. 线程池中的最大线程数
	 * 最大空闲线程
	 */
	protected int maxSpareThreads;

	/**
	 * Number of threads in the pool. 线程池中的数量
	 */
	protected int currentThreadCount;

	/**
	 * Number of busy threads in the pool. 线程池中繁忙线程的数量
	 */
	protected int currentThreadsBusy;

	/**
	 * Flag that the pool should terminate all the threads and stop.
	 * 终止和停止所有池中线程的标志
	 */
	protected boolean stopThePool;

	/**
	 * Flag to control if the main thread is 'daemon' 主线程孔昂志标志
	 */
	protected boolean isDaemon = true;

	/**
	 * The threads that are part of the pool. Key is Thread, value is the
	 * ControlRunnable 键=线程 值=ControlRunnable
	 */
	protected Hashtable<Thread, ControlRunnable> threads = new Hashtable<Thread, ControlRunnable>();

	protected Vector<ThreadPoolListener> listeners = new Vector<ThreadPoolListener>();

	/**
	 * Name of the threadpool 线程池的名字
	 */
	protected String name = "TP";

	/**
	 * Sequence. 序列
	 */
	protected int sequence = 1;

	/**
	 * Thread priority. 线程优先级
	 */
	protected int threadPriority = Thread.NORM_PRIORITY;

	private static ThreadPool tp = new ThreadPool();

	/**
	 * Constructor.
	 */
	private ThreadPool() {
		maxThreads = MAX_THREADS;
		maxSpareThreads = MAX_SPARE_THREADS;
		minSpareThreads = MIN_SPARE_THREADS;
		currentThreadCount = 0;
		currentThreadsBusy = 0;
		stopThePool = false;
	}

	public static ThreadPool getInstance() {
		return tp;
	}

	/**
	 * Create a ThreadPool instance.
	 *
	 * @param jmx
	 *            UNUSED
	 * @return ThreadPool instance. If JMX support is requested, you need to
	 *         call register() in order to set a name.
	 */
	public static ThreadPool createThreadPool(boolean jmx) {
		return new ThreadPool();
	}

	public synchronized void start() {
		stopThePool = false;
		currentThreadCount = 0;
		currentThreadsBusy = 0;

		adjustLimits();

		pool = new ControlRunnable[maxThreads];

		openThreads(minSpareThreads);
		if (maxSpareThreads < maxThreads) {
			monitor = new MonitorRunnable(this);
		}
	}

	public MonitorRunnable getMonitor() {
		return monitor;
	}

	/**
	 * Sets the thread priority for current and future threads in this pool.
	 *
	 * @param threadPriority
	 *            The new priority
	 * @throws IllegalArgumentException
	 *             If the specified priority is less than Thread.MIN_PRIORITY or
	 *             more than Thread.MAX_PRIORITY 设置线程优先级
	 */
	public synchronized void setThreadPriority(int threadPriority) {

		if (threadPriority < Thread.MIN_PRIORITY) {
			throw new IllegalArgumentException("new priority < MIN_PRIORITY");
		} else if (threadPriority > Thread.MAX_PRIORITY) {
			throw new IllegalArgumentException("new priority > MAX_PRIORITY");
		}

		// Set for future threads
		this.threadPriority = threadPriority;

		Enumeration<Thread> currentThreads = getThreads();
		Thread t = null;
		while (currentThreads.hasMoreElements()) {
			t = (Thread) currentThreads.nextElement();
			t.setPriority(threadPriority);
		}
	}

	/**
	 * Returns the priority level of current and future threads in this pool.
	 *
	 * @return The priority
	 */
	public int getThreadPriority() {
		return threadPriority;
	}

	public void setMaxThreads(int maxThreads) {
		this.maxThreads = maxThreads;
	}

	public int getMaxThreads() {
		return maxThreads;
	}

	public void setMinSpareThreads(int minSpareThreads) {
		this.minSpareThreads = minSpareThreads;
	}

	public int getMinSpareThreads() {
		return minSpareThreads;
	}

	public void setMaxSpareThreads(int maxSpareThreads) {
		this.maxSpareThreads = maxSpareThreads;
	}

	public int getMaxSpareThreads() {
		return maxSpareThreads;
	}

	public int getCurrentThreadCount() {
		return currentThreadCount;
	}

	public int getCurrentThreadsBusy() {
		return currentThreadsBusy;
	}

	public boolean isDaemon() {
		return isDaemon;
	}

	public static int getDebug() {
		return 0;
	}

	/**
	 * The default is true - the created threads will be in daemon mode. If set
	 * to false, the control thread will not be daemon - and will keep the
	 * process alive.
	 */
	public void setDaemon(boolean b) {
		isDaemon = b;
	}

	public boolean getDaemon() {
		return isDaemon;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public int getSequence() {
		return sequence++;
	}

	public void addThread(Thread t, ControlRunnable cr) {
		threads.put(t, cr);
		for (int i = 0; i < listeners.size(); i++) {
			ThreadPoolListener tpl = (ThreadPoolListener) listeners.elementAt(i);
			tpl.threadStart(this, t);
		}
	}

	public void removeThread(Thread t) {
		threads.remove(t);
		for (int i = 0; i < listeners.size(); i++) {
			ThreadPoolListener tpl = (ThreadPoolListener) listeners.elementAt(i);
			tpl.threadEnd(this, t);
		}
	}

	public void addThreadPoolListener(ThreadPoolListener tpl) {
		listeners.addElement(tpl);
	}

	public Enumeration<Thread> getThreads() {
		return threads.keys();
	}

	public void run(Runnable r) {
		ControlRunnable c = findControlRunnable();
		c.runIt(r);
	}

	//
	// You may wonder what you see here ... basically I am trying
	// to maintain a stack of threads. This way locality in time
	// is kept and there is a better chance to find residues of the
	// thread in memory next time it runs.
	//

	/**
	 * Executes a given Runnable on a thread in the pool, block if needed.
	 */
	public void runIt(ThreadPoolRunnable r) {
		if (null == r) {
			throw new NullPointerException();
		}

		ControlRunnable c = findControlRunnable();
		c.runIt(r);
	}

	private ControlRunnable findControlRunnable() {
		ControlRunnable c = null;

		if (stopThePool) {
			throw new IllegalStateException();
		}

		// Obtain a free thread from the pool.
		synchronized (this) {

			while (currentThreadsBusy == currentThreadCount) {
				// All threads are busy
				if (currentThreadCount < maxThreads) {
					// Not all threads were open,
					// Open new threads up to the max number of idel threads
					int toOpen = currentThreadCount + minSpareThreads;
					openThreads(toOpen);
				} else {

					// Wait for a thread to become idel.
					try {
						this.wait();
					}
					// was just catch Throwable -- but no other
					// exceptions can be thrown by wait, right?
					// So we catch and ignore this one, since
					// it'll never actually happen, since nowhere
					// do we say pool.interrupt().
					catch (InterruptedException e) {

					}

					// Pool was stopped. Get away of the pool.
					if (stopThePool) {
						break;
					}
				}
			}
			// Pool was stopped. Get away of the pool.
			if (0 == currentThreadCount || stopThePool) {
				throw new IllegalStateException();
			}

			// If we are here it means that there is a free thread. Take it.
			int pos = currentThreadCount - currentThreadsBusy - 1;
			c = pool[pos];
			pool[pos] = null;
			currentThreadsBusy++;

		}
		System.out.println(c);
		return c;
	}

	/**
	 * Stop the thread pool
	 */
	public synchronized void shutdown() {
		if (!stopThePool) {
			stopThePool = true;
			if (monitor != null) {
				monitor.terminate();
				monitor = null;
			}
			for (int i = 0; i < currentThreadCount - currentThreadsBusy; i++) {
				try {
					pool[i].terminate();
				} catch (Throwable t) {
					/*
					 * Do nothing... The show must go on, we are shutting down
					 * the pool and nothing should stop that.
					 */

				}
			}
			currentThreadsBusy = currentThreadCount = 0;
			pool = null;
			notifyAll();
		}
	}

	/**
	 * Called by the monitor thread to harvest idle threads.
	 */
	protected synchronized void checkSpareControllers() {

		if (stopThePool) {
			return;
		}

		if ((currentThreadCount - currentThreadsBusy) > maxSpareThreads) {
			int toFree = currentThreadCount - currentThreadsBusy - maxSpareThreads;

			for (int i = 0; i < toFree; i++) {
				ControlRunnable c = pool[currentThreadCount - currentThreadsBusy - 1];
				c.terminate();
				pool[currentThreadCount - currentThreadsBusy - 1] = null;
				currentThreadCount--;
			}

		}

	}

	/**
	 * Returns the thread to the pool. Called by threads as they are becoming
	 * idel.
	 */
	protected synchronized void returnController(ControlRunnable c) {

		if (0 == currentThreadCount || stopThePool) {
			c.terminate();
			return;
		}

		// atomic
		currentThreadsBusy--;

		pool[currentThreadCount - currentThreadsBusy - 1] = c;
		notify();
	}

	/**
	 * Inform the pool that the specific thread finish.
	 *
	 * Called by the ControlRunnable.run() when the runnable throws an
	 * exception.
	 */
	protected synchronized void notifyThreadEnd(ControlRunnable c) {
		currentThreadsBusy--;
		currentThreadCount--;
		notify();
	}

	/*
	 * Checks for problematic configuration and fix it. The fix provides
	 * reasonable settings for a single CPU with medium load. 检查有问题的配置和修复它。
	 * 此修复程序提供了一个单一CPU合理设置 中等负荷。
	 */
	protected void adjustLimits() {
		if (maxThreads <= 0) {
			// 如果最大线程数小于0则赋予默认线程数
			maxThreads = MAX_THREADS;
		} else if (maxThreads < MAX_THREADS_MIN) {
			// 如果最大线程数小于他的最小阀值,则最大线程数赋予默认值

			maxThreads = MAX_THREADS_MIN;
		}

		if (maxSpareThreads >= maxThreads) {
			// 如果最大空闲线程数大于默认值，将赋予默认值
			maxSpareThreads = maxThreads;
		}

		if (maxSpareThreads <= 0) {
			if (1 == maxThreads) {
				maxSpareThreads = 1;
			} else {
				maxSpareThreads = maxThreads / 2;
			}
		}

		if (minSpareThreads > maxSpareThreads) {
			minSpareThreads = maxSpareThreads;
		}

		if (minSpareThreads <= 0) {
			if (1 == maxSpareThreads) {
				minSpareThreads = 1;
			} else {
				minSpareThreads = maxSpareThreads / 2;
			}
		}
	}

	/**
	 * Create missing threads. 创建失踪线程
	 * 
	 * @param toOpen
	 *            Total number of threads we'll have open 线程数开放
	 */
	protected void openThreads(int toOpen) {

		if (toOpen > maxThreads) {
			toOpen = maxThreads;
		}

		for (int i = currentThreadCount; i < toOpen; i++) {
			pool[i - currentThreadsBusy] = new ControlRunnable(this);
		}

		currentThreadCount = toOpen;
	}

	/** @deprecated */
	void log(String s) {

		// loghelper.flush();
	}

	/**
	 * Periodically execute an action - cleanup in this case 定期执行清理 监听线程
	 */
	public static class MonitorRunnable implements Runnable {
		ThreadPool p;
		Thread t;
		int interval = WORK_WAIT_TIMEOUT;
		boolean shouldTerminate;

		MonitorRunnable(ThreadPool p) {
			this.p = p;
			this.start();
		}

		public void start() {
			shouldTerminate = false;
			t = new Thread(this);
			t.setDaemon(p.getDaemon());
			t.setName(p.getName() + "-Monitor");
			t.start();
		}

		public void setInterval(int i) {
			this.interval = i;
		}

		public void run() {
			while (true) {
				try {

					// Sleep for a while.
					synchronized (this) {
						this.wait(interval);
					}

					// Check if should terminate.
					// termination happens when the pool is shutting down.
					if (shouldTerminate) {
						break;
					}

					// Harvest idle threads.
					p.checkSpareControllers();

				} catch (Throwable t) {

				}
			}
		}

		public void stop() {
			this.terminate();
		}

		/**
		 * Stop the monitor
		 */
		public synchronized void terminate() {
			shouldTerminate = true;
			this.notify();
		}
	}

	/**
	 * A Thread object that executes various actions ( ThreadPoolRunnable )
	 * under control of ThreadPool 线程执行对象（ThreadPoolRunnable的各项行动） 控制下的线程池
	 */
	public static class ControlRunnable implements Runnable {
		/**
		 * ThreadPool where this thread will be returned 线程池在此线程中将被返回
		 */
		private ThreadPool p;

		/**
		 * The thread that executes the actions 线程执行的行动
		 */
		private ThreadWithAttributes t;

		/**
		 * The method that is executed in this thread 认为在这个线程执行的方法
		 */
		private ThreadPoolRunnable toRun;
		
		
		private Runnable toRunRunnable;

		/**
		 * Stop this thread 线程停止标志
		 */
		private boolean shouldTerminate;

		/**
		 * Activate the execution of the action 线程激活标志
		 */
		private boolean shouldRun;

		/**
		 * Per thread data - can be used only if all actions are of the same
		 * type. A better mechanism is possible ( that would allow association
		 * of thread data with action type ), but right now it's enough.
		 * *每个线程的数据-可以使用只有当所有的行动 同一类型。 一个更好的机制是可能的（这将使协会 螺纹行动类型的数据），但现在它是不够的。
		 * 
		 */
		private boolean noThData;

		/**
		 * Start a new thread, with no method in it
		 */
		ControlRunnable(ThreadPool p) {
			toRun = null;
			shouldTerminate = false;
			shouldRun = false;
			this.p = p;
			t = new ThreadWithAttributes(p, this);
			t.setDaemon(true);
			t.setName(p.getName() + "-Processor" + p.getSequence());
			t.setPriority(p.getThreadPriority());
			p.addThread(t, this);
			noThData = true;
			t.start();
		}

		public void run() {
			boolean _shouldRun = false;
			boolean _shouldTerminate = false;
			ThreadPoolRunnable _toRun = null;
			try {
				while (true) {
					try {
						/* Wait for work. */
						synchronized (this) {
							while (!shouldRun && !shouldTerminate) {
								this.wait();
							}
							_shouldRun = shouldRun;
							_shouldTerminate = shouldTerminate;
							_toRun = toRun;
						}

						if (_shouldTerminate) {

							break;
						}

						/* Check if should execute a runnable. */
						try {
							if (noThData) {
								if (_toRun != null) {
									Object thData[] = _toRun.getInitData();
									t.setThreadData(p, thData);

								}
								noThData = false;
							}

							if (_shouldRun) {
								if (_toRun != null) {
									_toRun.runIt(t.getThreadData(p));
								} else if (toRunRunnable != null) {
									toRunRunnable.run();
								} else {

								}
							}
						} catch (Throwable t) {

							/*
							 * The runnable throw an exception (can be even a
							 * ThreadDeath), signalling that the thread die.
							 *
							 * The meaning is that we should release the thread
							 * from the pool.
							 */
							_shouldTerminate = true;
							_shouldRun = false;
							p.notifyThreadEnd(this);
						} finally {
							if (_shouldRun) {
								shouldRun = false;
								/*
								 * Notify the pool that the thread is now idle.
								 */
								p.returnController(this);
							}
						}

						/*
						 * Check if should terminate. termination happens when
						 * the pool is shutting down.
						 */
						if (_shouldTerminate) {
							break;
						}
					} catch (InterruptedException ie) { /*
														 * for the wait
														 * operation
														 */
						// can never happen, since we don't call interrupt

					}
				}
			} finally {
				p.removeThread(Thread.currentThread());
			}
		}

		/**
		 * Run a task
		 *
		 * @param toRun
		 */
		public synchronized void runIt(Runnable toRun) {
			this.toRunRunnable = toRun;
			// Do not re-init, the whole idea is to run init only once per
			// thread - the pool is supposed to run a single task, that is
			// initialized once.
			// noThData = true;
			shouldRun = true;
			this.notify();
		}

		/**
		 * Run a task
		 *
		 * @param toRun
		 */
		public synchronized void runIt(ThreadPoolRunnable toRun) {
			this.toRun = toRun;
			// Do not re-init, the whole idea is to run init only once per
			// thread - the pool is supposed to run a single task, that is
			// initialized once.
			// noThData = true;
			shouldRun = true;
			this.notify();
		}

		public void stop() {
			this.terminate();
		}

		@SuppressWarnings("deprecation")
		public void kill() {
			t.stop();
		}

		public synchronized void terminate() {
			shouldTerminate = true;
			this.notify();
		}
	}

	/**
	 * Debug display of the stage of each thread. The return is html style, for
	 * display in the console ( it can be easily parsed too ).
	 *
	 * @return The thread status display
	 */
	public String threadStatusString() {
		StringBuffer sb = new StringBuffer();
		Iterator<Thread> it = threads.keySet().iterator();
		sb.append("<ul>");
		while (it.hasNext()) {
			sb.append("<li>");
			ThreadWithAttributes twa = (ThreadWithAttributes) it.next();
			sb.append(twa.getCurrentStage(this)).append(" ");
			sb.append(twa.getParam(this));
			sb.append("</li>\n");
		}
		sb.append("</ul>");
		return sb.toString();
	}

	/**
	 * Return an array with the status of each thread. The status indicates the
	 * current request processing stage ( for tomcat ) or whatever the thread is
	 * doing ( if the application using TP provide this info )
	 *
	 * @return The status of all threads
	 */
	public String[] getThreadStatus() {
		String status[] = new String[threads.size()];
		Iterator<Thread> it = threads.keySet().iterator();
		for (int i = 0; (i < status.length && it.hasNext()); i++) {
			ThreadWithAttributes twa = (ThreadWithAttributes) it.next();
			status[i] = twa.getCurrentStage(this);
		}
		return status;
	}

	/**
	 * Return an array with the current "param"
	 * thread. This is typically the last request.
	 *
	 * @return The params of all threads
	 */
	public String[] getThreadParam() {
		String status[] = new String[threads.size()];
		Iterator<Thread> it = threads.keySet().iterator();
		for (int i = 0; (i < status.length && it.hasNext()); i++) {
			ThreadWithAttributes twa = (ThreadWithAttributes) it.next();
			Object o = twa.getParam(this);
			status[i] = (o == null) ? null : o.toString();
		}
		return status;
	}

	/**
	 * Interface to allow applications to be notified when a threads are created
	 * and stopped.
	 */
	public static interface ThreadPoolListener {
		public void threadStart(ThreadPool tp, Thread t);

		public void threadEnd(ThreadPool tp, Thread t);
	}

}
