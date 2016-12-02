package org.biframework.threads;

/** Implemented if you want to run a piece of code inside a thread pool.
 */
public interface ThreadPoolRunnable {
    // XXX use notes or a hashtable-like
    // Important: ThreadData in JDK1.2 is implemented as a Hashtable( Thread -> object ),
    // expensive.
    
    /** Called when this object is first loaded in the thread pool.
     *  Important: all workers in a pool must be of the same type,
     *  otherwise the mechanism becomes more complex.
     */
    public Object[] getInitData();

    /** This method will be executed in one of the pool's threads. The
     *  thread will be returned to the pool.
     */
    public void runIt(Object thData[]);

}
