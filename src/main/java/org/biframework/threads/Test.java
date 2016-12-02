package org.biframework.threads;

public class Test implements Runnable {
	public void run() {
		System.out.println("test");
	}

	public static void main(String[] args) {
		ThreadPool pool=ThreadPool.getInstance();
		pool.start();
	}

}
