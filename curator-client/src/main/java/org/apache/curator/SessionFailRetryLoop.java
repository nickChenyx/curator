/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.curator;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.curator.utils.ThreadUtils;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import java.io.Closeable;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>
 *     See {@link RetryLoop} for the main details on retry loops. <b>All Curator/ZooKeeper operations
 *     should be done in a retry loop.</b>
 * </p>
 *
 * <p>
 *     The standard retry loop treats session failure as a type of connection failure. i.e. the fact
 *     that it is a session failure isn't considered. This can be problematic if you are performing
 *     a series of operations that rely on ephemeral nodes. If the session fails after the ephemeral
 *     node has been created, future Curator/ZooKeeper operations may succeed even though the
 *     ephemeral node has been removed by ZooKeeper.
 * </p>
 *
 * <p>
 *     Here's an example:
 * </p>
 *     <ul>
 *         <li>You create an ephemeral/sequential node as a kind of lock/marker</li>
 *         <li>You perform some other operations</li>
 *         <li>The session fails for some reason</li>
 *         <li>You attempt to create a node assuming that the lock/marker still exists
 *         <ul>
 *             <li>Curator will notice the session failure and try to reconnect</li>
 *             <li>In most cases, the reconnect will succeed and, thus, the node creation will succeed
 *             even though the ephemeral node will have been deleted by ZooKeeper.</li>
 *         </ul>
 *         </li>
 *     </ul>
 *
 * <p>
 *     The SessionFailRetryLoop prevents this type of scenario. When a session failure is detected,
 *     the thread is marked as failed which will cause all future Curator operations to fail. The
 *     SessionFailRetryLoop will then either retry the entire
 *     set of operations or fail (depending on {@link SessionFailRetryLoop.Mode})
 * </p>
 *
 * Canonical usage:<br>
 * <pre>
 * SessionFailRetryLoop    retryLoop = client.newSessionFailRetryLoop(mode);
 * retryLoop.start();
 * try
 * {
 *     while ( retryLoop.shouldContinue() )
 *     {
 *         try
 *         {
 *             // do work
 *         }
 *         catch ( Exception e )
 *         {
 *             retryLoop.takeException(e);
 *         }
 *     }
 * }
 * finally
 * {
 *     retryLoop.close();
 * }
 * </pre>
 *
 * 是用来处理和 zk 连接时 session 过期的时候，方便做重试策略。
 */
//[$3 nick 2018-08-04]
public class SessionFailRetryLoop implements Closeable
{
    private final CuratorZookeeperClient    client;
    private final Mode                      mode;
    private final Thread                    ourThread = Thread.currentThread();
    private final AtomicBoolean             sessionHasFailed = new AtomicBoolean(false);
    private final AtomicBoolean             isDone = new AtomicBoolean(false);
    /**
     * 类是 SessionFailRetryLoop，但实际上并没有继承 RetryLoop，而是采取了组合的形式。
     * // TODO 但实际上两个 {@link SessionFailRetryLoop} {@link RetryLoop} 有相似的方法，为啥没有抽象成接口呢？
     */
    private final RetryLoop                 retryLoop;

    /**
     * zk 的 watcher 用来观察是否是 session 过期事件
     */
    private final Watcher         watcher = new Watcher()
    {
        @Override
        public void process(WatchedEvent event)
        {
            if ( event.getState() == Event.KeeperState.Expired )
            {
                sessionHasFailed.set(true);
                failedSessionThreads.add(ourThread);
            }
        }
    };
    /**
     * 这里是 static 的静态 set，存的时候存的都是 ourThread。 用来记录被标记过期的线程。
     */
    private static final Set<Thread>        failedSessionThreads = Sets.newSetFromMap(Maps.<Thread, Boolean>newConcurrentMap());

    /**
     * 专门新建的一个标记 session 过期的异常，会在 {@link ConnectionState#getZooKeeper()} 里被抛出
     */
    public static class SessionFailedException extends Exception
    {
      private static final long serialVersionUID = 1L;
    }

    public enum Mode
    {
        /**
         * If the session fails, retry the entire set of operations when {@link SessionFailRetryLoop#shouldContinue()}
         * is called
         */
        RETRY,

        /**
         * If the session fails, throw {@link KeeperException.SessionExpiredException} when
         * {@link SessionFailRetryLoop#shouldContinue()} is called
         */
        FAIL
    }

    /**
     * Convenience utility: creates a "session fail" retry loop calling the given proc
     * 一个快捷工具，
     * @param client Zookeeper
     * @param mode how to handle session failures
     * @param proc procedure to call with retry
     * @param <T> return type
     * @return procedure result
     * @throws Exception any non-retriable errors
     */
    public static<T> T      callWithRetry(CuratorZookeeperClient client, Mode mode, Callable<T> proc) throws Exception
    {
        T                       result = null;
        SessionFailRetryLoop    retryLoop = client.newSessionFailRetryLoop(mode);
        retryLoop.start();
        try
        {
            while ( retryLoop.shouldContinue() )
            {
                try
                {
                    result = proc.call();
                }
                catch ( Exception e )
                {
                    ThreadUtils.checkInterrupted(e);
                    retryLoop.takeException(e);
                }
            }
        }
        finally
        {
            retryLoop.close();
        }
        return result;
    }

    SessionFailRetryLoop(CuratorZookeeperClient client, Mode mode)
    {
        this.client = client;
        this.mode = mode;
        retryLoop = client.newRetryLoop();
    }

    static boolean sessionForThreadHasFailed()
    {
        return failedSessionThreads.contains(Thread.currentThread());
    }

    /**
     * SessionFailRetryLoop must be started
     * 必须要运行 start 方法，这样才能把 watcher 注册上去
     */
    public void     start()
    {
        Preconditions.checkState(Thread.currentThread().equals(ourThread), "Not in the correct thread");

        /**
         * 最后还是添加到 {@link ConnectionState#parentWatchers}
         */
        client.addParentWatcher(watcher);
    }

    /**
     * If true is returned, make an attempt at the set of operations
     * 将 isDone 标记为 true，同时返回之前的值。
     *
     * 初始值是 false，执行该方法的时候强制转为 true。
     * 如果在处理 retry 的时候又出现了 session fail，则再标记为 false
     *
     * @return true/false
     */
    public boolean      shouldContinue()
    {
        boolean     localIsDone = isDone.getAndSet(true);
        return !localIsDone;
    }

    /**
     * Must be called in a finally handler when done with the loop
     *
     * 注意必须放在 finally 代码段内执行
     */
    @Override
    public void close()
    {
        Preconditions.checkState(Thread.currentThread().equals(ourThread), "Not in the correct thread");
        failedSessionThreads.remove(ourThread);

        client.removeParentWatcher(watcher);
    }

    /**
     * Pass any caught exceptions here
     * 处理所有捕获的异常，核心方法
     *
     * @param exception the exception
     * @throws Exception if not retry-able or the retry policy returned negative
     */
    public void         takeException(Exception exception) throws Exception
    {
        Preconditions.checkState(Thread.currentThread().equals(ourThread), "Not in the correct thread");

        /**
         * 判断是否需要传递到 {@link SessionFailRetryLoop#retryLoop}
         */
        boolean     passUp = true;
        if ( sessionHasFailed.get() )
        {
            switch ( mode )
            {
                /**
                 * 这里就涉及到了 {@link SessionFailRetryLoop.Mode} 的设计了
                 * RETRY 模式的话，重置fail状态，若异常还是 SessionFailedException 的话，重置 Done，方便直接调用 {@link SessionFailRetryLoop#shouldContinue()}  方法，
                 * 在业务层继续处理。（这一块是上层强耦合的）
                 * FAIL 模式的话，会直接将遇到的异常传递到 {@link SessionFailRetryLoop#retryLoop} 进行处理，
                 * 感觉这里命名应该叫 PASSUP 更好点。
                 */
                case RETRY:
                {
                    sessionHasFailed.set(false);
                    failedSessionThreads.remove(ourThread);
                    if ( exception instanceof SessionFailedException )
                    {
                        isDone.set(false);
                        passUp = false;
                    }
                    break;
                }

                case FAIL:
                {
                    break;
                }
            }
        }

        if ( passUp )
        {
            retryLoop.takeException(exception);
        }
    }
}
