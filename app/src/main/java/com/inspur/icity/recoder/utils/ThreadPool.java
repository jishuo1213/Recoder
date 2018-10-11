package com.inspur.icity.recoder.utils;

import android.support.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 创建一个可缓存的线程池,如果线程池的大小超过了处理任务所需要的线程，
 * 那么就会回收部分空闲（60秒不执行任务）的线程。当任务数增加时，此线程池又可以智能的添加新线程来处理任务
 * 此线程池不会对线程池大小做限制，线程池大小完全依赖于操作系统（或者说JVM）能够创建的最大线程大小。
 * 使用：ThreadPool.runMethod(r);
 */
public class ThreadPool extends Thread {
/*
	private static ExecutorService threadPool = null;

	private ThreadPool() {

	}

	*/
/**
	 * 获取单例实例
	 * 
	 *//*

	private static class PoolHolder {
		private static ExecutorService instance = Executors
				.newCachedThreadPool();
	}

	private static ExecutorService getInstance() {
		return PoolHolder.instance;

	}

	*/
/**
	 * 执行指定线程
	 * 
	 * @param r
	 * @return
	 *//*

	public static <R extends Runnable> void exec(R r) {
		if ((threadPool = getInstance()) == null) {
			// 意外情况threadPool为空时处理
			threadPool = Executors.newCachedThreadPool();
		}
		if (r != null) {
			// 执行线程
			threadPool.execute(r);
		}
	}

	*/
/**
	 * 关闭线程池
	 *//*

	public static void shutDown() {
		if (threadPool != null) {
			threadPool.shutdown();
		}
	}
*/


private static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {
    @Override
    public Thread newThread(@NonNull Runnable runnable) {
        thread = new ThreadPool(runnable);
        thread.setName("EventThread");
        return thread;
    }
};

    private static ThreadPool thread;

    private static ExecutorService service;

    private static int counter = 0;


    private ThreadPool(Runnable runnable) {
        super(runnable);
    }

    /**
     * check if the current thread is EventThread.
     *
     * @return true if the current thread is EventThread.
     */
    public static boolean isCurrent() {
        return currentThread() == thread;
    }

    /**
     * Executes a task in EventThread.
     *
     */
    public static <R extends Runnable> void exec(R task) {
        if (isCurrent()) {
            task.run();
        } else {
            nextTick(task);
        }
    }

    /**
     * Executes a task on the next loop in EventThread.
     *
     */
    public static void nextTick(final Runnable task) {
        ExecutorService executor;
        synchronized (ThreadPool.class) {
            counter++;
            if (service == null) {
                service = Executors.newSingleThreadExecutor(THREAD_FACTORY);
            }
            executor = service;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } finally {
                    synchronized (ThreadPool.class) {
                        counter--;
                        if (counter == 0) {
                            service.shutdown();
                            service = null;
                            thread = null;
                        }
                    }
                }
            }
        });
    }
}
