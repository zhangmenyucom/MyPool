package com.taylor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Vector;

import lombok.Data;

/**
 * @ClassName: MyConnection
 * @Function: 自定义数据库连接封装类
 * @date: 2016年11月25日 上午1:16:09
 * @author Taylor
 */
@Data
public class PooledConnection {

	private boolean isBusy;

	private Connection connection;

	private MyPool myPool;

	public synchronized void close() {
		this.isBusy = false;
		myPool.busyCount.decrementAndGet();
		myPool.currentIdleCount.incrementAndGet();
		if (myPool.currentIdleCount.get() > myPool.getPoolMaxIdle()) {
			synchronized (myPool) {
				if (myPool.currentIdleCount.get() > myPool.getPoolMaxIdle()) {
					Vector<PooledConnection> list = myPool.getPoolConnections();
					for (int i = list.size() - 1; i >= 0 && myPool.currentIdleCount.get() > myPool.getPoolMaxIdle(); i--) {
						if (!list.get(i).isBusy) {
							Connection cnn = list.remove(i).getConnection();
							if (cnn != null) {
								try {
									if (!cnn.isClosed()) {
										cnn.close();
									}
								} catch (SQLException e) {
									System.out.println("关闭连接失败");
									e.printStackTrace();
								}
							}
							myPool.currentIdleCount.decrementAndGet();
						}
					}
				}
			}
		}
		System.out.println("当前总连接数：" + myPool.getPoolConnections().size());
		System.out.println("当前工作连接数：" + myPool.busyCount);
		System.out.println("当前空闲连接数：" + myPool.currentIdleCount);

	}

	public PooledConnection(boolean isBusy, Connection connection, MyPool myPool) {
		super();
		this.isBusy = isBusy;
		this.connection = connection;
		this.myPool = myPool;
	}

	public ResultSet queryBySql(String sql) {
		Statement statement = null;
		ResultSet result = null;
		try {
			statement = this.connection.createStatement();
			result = statement.executeQuery(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return result;

	}

}
