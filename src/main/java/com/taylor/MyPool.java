package com.taylor;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.Data;

/**
 * @ClassName: MyPool
 * @Function: 数据库连接池
 * @date: 2016年11月25日 上午1:08:23
 * @author Taylor
 */
@Data
public class MyPool implements IMyPool {

	private String jdbcDriver;

	private String username;

	private String password;

	private String jdbcUrl;

	private int initCount;

	private int stepSize;

	private int poolMaxSize;

	private int poolMaxIdle;

	public  AtomicInteger currentIdleCount = new AtomicInteger(0);

	public  AtomicInteger busyCount = new AtomicInteger(0);

	private Vector<PooledConnection> poolConnections = new Vector<>();

	public MyPool() {
		initContext();
	}

	private void initContext() {
		Properties properties = new Properties();
		try {
			properties.load(this.getClass().getClassLoader().getResourceAsStream("config/jdbc.properties"));
		} catch (IOException e1) {
			System.out.println("jdbcp.properties解析错误，不存在");
			e1.printStackTrace();
		}
		this.jdbcDriver = properties.getProperty("jdbc.driver");
		this.username = properties.getProperty("jdbc.username");
		this.password = properties.getProperty("jdbc.password");
		this.jdbcUrl = properties.getProperty("jdbc.url");
		this.initCount = Integer.valueOf(properties.getProperty("jdbc.initSize"));
		this.stepSize = Integer.valueOf(properties.getProperty("jdbc.stepSize"));
		this.poolMaxSize = Integer.valueOf(properties.getProperty("jdbc.maxSize"));
		this.poolMaxIdle = Integer.valueOf(properties.getProperty("jdbc.maxIdle"));

		Driver driver;
		try {
			driver = (Driver) Class.forName(jdbcDriver).newInstance();
			DriverManager.registerDriver(driver);
		} catch (SQLException | InstantiationException | IllegalAccessException | ClassNotFoundException e) {
			System.out.println("数据库初始化失败");
			e.printStackTrace();
		}
		this.createNewConnections(initCount);
	}

	@Override
	public PooledConnection getConnection() {
		if (poolConnections.size() <= 0) {
			System.out.println("连接池中没有可用的连接");
			throw new RuntimeException("连接池中没有可用的连接");
		}
		return getRealConnection();
	}

	/**
	 * @desc getRealConnection(获取可用的连接)
	 * @author taylor
	 * @date 2016年11月25日 上午1:24:15
	 */
	private synchronized PooledConnection getRealConnection() {
		/**
		 * 获取连接
		 */
		for (PooledConnection cnn : poolConnections) {
			if (!cnn.isBusy()) {
				Connection connection = cnn.getConnection();
				try {
					if (connection != null && !connection.isValid(2000)) {
						poolConnections.remove(cnn);
						this.createNewConnections(1);
						continue;
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}
				cnn.setBusy(true);
				busyCount.incrementAndGet();
				currentIdleCount.decrementAndGet();
				return cnn;
			}
		}
		/**
		 * 超过线程数则等待
		 */
		if (poolConnections.size() >= poolMaxSize) {
			try {
				System.out.println("呜呜。。。池中已经没有连接了，先睡1秒。。。。");
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} else {
			this.createNewConnections(stepSize);

		}
		return getRealConnection();
	}

	/**
	 * @desc createNewConnections(创建连接)
	 * @param count
	 * @author taylor
	 * @date 2016年11月25日 上午1:06:12
	 */
	@Override
	public void createNewConnections(int count) {
		synchronized (this) {
			if (this.poolMaxSize > 0 && (poolConnections.size() + count) > this.poolMaxSize) {
				System.out.println("创建连接池数超过上限");
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				return;
			}
			for (int i = 0; i < count; i++) {
				currentIdleCount.incrementAndGet();
				try {
					Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
					PooledConnection myConnection = new PooledConnection(false, connection, this);
					poolConnections.add(myConnection);
				} catch (SQLException e) {
					System.out.println("获取数据库连接失败");
					e.printStackTrace();
				}

			}
		}

	}
}
