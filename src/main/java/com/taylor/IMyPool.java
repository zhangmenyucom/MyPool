package com.taylor;

public interface IMyPool {

  /**
   * @desc   getConnection(获取连接对象)
   * @author taylor
   * @date  2016年11月25日 上午1:04:28
   */
  public PooledConnection getConnection();

  /**
   * 
   * @desc createNewConnection(这里用一句话描述这个方法的作用)
   * @param count
   * @author taylor
   * @date 2016年11月25日 上午1:04:17
   */
  public void createNewConnections(int count);

}
