package com.taylor;

import java.sql.ResultSet;
import java.sql.SQLException;

public class PoolManager {

	private static class PoolHolder {
		private static IMyPool mypool = new MyPool();
	}

	public static IMyPool getInstance() {
		return PoolHolder.mypool;
	}

	public  static void selectOne() {
		PooledConnection cnn = PoolManager.getInstance().getConnection();
		ResultSet rs = cnn.queryBySql("select * from test");
		System.out.println("线程名：" + Thread.currentThread().getName());
		try {
			while (rs.next()) {
				System.out.print(rs.getString(1) + "\t");
				System.out.print(rs.getString(2) + "\t");
				System.out.println();
			}
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		cnn.close();
	}

	public static void main(String[] args) throws SQLException {
		for (int i = 0; i <1500; i++) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					selectOne();
				}
			}).start();
		}

	}
}
