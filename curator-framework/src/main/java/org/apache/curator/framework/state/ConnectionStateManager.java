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

package org.apache.curator.framework.state;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.listen.ListenerContainer;
import org.apache.curator.utils.Compatibility;
import org.apache.curator.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Closeable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Used internally to manage connection state
 *
 * 这个类处理了连接 state 的状态变更，并且存储在一个Size 20的队里中，一次次取出这些事件通知 Listener 处理
 *
 * 这个类的设计很好，有后台跑的任务 processEvent，有block等待连接，有notifyAll通知状态变更，是个不错的实践！
 */
//[$3 nick 2018-08-30]
//[@@ nick]
public class ConnectionStateManager implements Closeable
{
    private static final int QUEUE_SIZE;

    static
    {
        int size = 25;
        String property = System.getProperty("ConnectionStateManagerSize", null);
        if ( property != null )
        {
            try
            {
                size = Integer.parseInt(property);
            }
            catch ( NumberFormatException ignore )
            {
                // ignore
            }
        }
        QUEUE_SIZE = size;
    }

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final BlockingQueue<ConnectionState> eventQueue = new ArrayBlockingQueue<ConnectionState>(QUEUE_SIZE);
    private final CuratorFramework client;
    private final int sessionTimeoutMs;
    private final int sessionExpirationPercent;
    private final ListenerContainer<ConnectionStateListener> listeners = new ListenerContainer<ConnectionStateListener>();
    private final AtomicBoolean initialConnectMessageSent = new AtomicBoolean(false);
    private final ExecutorService service;
    private final AtomicReference<State> state = new AtomicReference<State>(State.LATENT);

    // guarded by sync
    private ConnectionState currentConnectionState;

    /**
     * 取决于 executors 是什么模型，默认是单线程跑，所以没啥内存可见性问题，如果改为多线程后台执行
     * 这里的 volatile 就重要了，保证了主存的可见性
     */
    private volatile long startOfSuspendedEpoch = 0;

    private enum State
    {
        LATENT,
        STARTED,
        CLOSED
    }

    /**
     * @param client        the client
     * @param threadFactory thread factory to use or null for a default
     * @param sessionTimeoutMs the ZK session timeout in milliseconds
     * @param sessionExpirationPercent percentage of negotiated session timeout to use when simulating a session timeout. 0 means don't simulate at all
     */
    public ConnectionStateManager(CuratorFramework client, ThreadFactory threadFactory, int sessionTimeoutMs, int sessionExpirationPercent)
    {
        this.client = client;
        this.sessionTimeoutMs = sessionTimeoutMs;
        this.sessionExpirationPercent = sessionExpirationPercent;
        if ( threadFactory == null )
        {
            threadFactory = ThreadUtils.newThreadFactory("ConnectionStateManager");
        }
        service = Executors.newSingleThreadExecutor(threadFactory);
    }

    /**
     * Start the manager
     */
    public void start()
    {
        Preconditions.checkState(state.compareAndSet(State.LATENT, State.STARTED), "Cannot be started more than once");

        service.submit
            (
                new Callable<Object>()
                {
                    @Override
                    public Object call() throws Exception
                    {
                        processEvents();
                        return null;
                    }
                }
            );
    }

    @Override
    public void close()
    {
        if ( state.compareAndSet(State.STARTED, State.CLOSED) )
        {
            service.shutdownNow();
            listeners.clear();
        }
    }

    /**
     * Return the listenable
     *
     * @return listenable
     */
    public ListenerContainer<ConnectionStateListener> getListenable()
    {
        return listeners;
    }

    /**
     * Change to {@link ConnectionState#SUSPENDED} only if not already suspended and not lost
     *                                             只有在尚未 suspended 且没有 lost 的情况下
     * @return true if connection is set to SUSPENDED
     */
    public synchronized boolean setToSuspended()
    {
        if ( state.get() != State.STARTED )
        {
            return false;
        }

        if ( (currentConnectionState == ConnectionState.LOST) || (currentConnectionState == ConnectionState.SUSPENDED) )
        {
            return false;
        }

        setCurrentConnectionState(ConnectionState.SUSPENDED);
        postState(ConnectionState.SUSPENDED);

        return true;
    }

    /**
     * Post a state change. If the manager is already in that state the change
     * is ignored. Otherwise the change is queued for listeners.
     *
     * @param newConnectionState new state
     * @return true if the state actually changed, false if it was already at that state
     */
    public synchronized boolean addStateChange(ConnectionState newConnectionState)
    {
        if ( state.get() != State.STARTED )
        {
            return false;
        }

        ConnectionState previousState = currentConnectionState;
        if ( previousState == newConnectionState )
        {
            return false;
        }
        setCurrentConnectionState(newConnectionState);

        ConnectionState localState = newConnectionState;
        // read only 也算 = =..
        boolean isNegativeMessage = ((newConnectionState == ConnectionState.LOST) || (newConnectionState == ConnectionState.SUSPENDED) || (newConnectionState == ConnectionState.READ_ONLY));
        if ( !isNegativeMessage && initialConnectMessageSent.compareAndSet(false, true) )
        {
            // 搭配上面的判断，主要是为了将 RECONNECT 和 CONNECTED 统一到 CONNECTED
            localState = ConnectionState.CONNECTED;
        }

        postState(localState);

        return true;
    }

    /**
     * wait 等待连接
     */
    public synchronized boolean blockUntilConnected(int maxWaitTime, TimeUnit units) throws InterruptedException
    {
        long startTime = System.currentTimeMillis();

        boolean hasMaxWait = (units != null);
        long maxWaitTimeMs = hasMaxWait ? TimeUnit.MILLISECONDS.convert(maxWaitTime, units) : 0;

        while ( !isConnected() )
        {
            if ( hasMaxWait )
            {
                long waitTime = maxWaitTimeMs - (System.currentTimeMillis() - startTime);
                if ( waitTime <= 0 )
                {
                    return isConnected();
                }

                wait(waitTime);
            }
            else
            {
                wait();
            }
        }
        return isConnected();
    }

    public synchronized boolean isConnected()
    {
        return (currentConnectionState != null) && currentConnectionState.isConnected();
    }

    /**
     * 传入 state 改变的事件，触发wait的方法唤醒重新判断连接状态
     * @param state
     */
    private void postState(ConnectionState state)
    {
        log.info("State change: " + state);

        notifyAll();

        while ( !eventQueue.offer(state) )
        {
            eventQueue.poll();
            log.warn("ConnectionStateManager queue full - dropping events to make room");
        }
    }

    /**
     * 后台线程一直在执行的任务，处理 state 改变事件
     */
    private void processEvents()
    {
        while ( state.get() == State.STARTED )
        {
            try
            {
                int useSessionTimeoutMs = getUseSessionTimeoutMs();
                // 如果上次 suspended 尚未存在，则取 sessionTimeout 的一半；否则取suspended 已经耗时多久
                long elapsedMs = startOfSuspendedEpoch == 0 ? useSessionTimeoutMs / 2 : System.currentTimeMillis() - startOfSuspendedEpoch;
                // poll 超时时间
                long pollMaxMs = useSessionTimeoutMs - elapsedMs;

                // 这个 timeout 时间计算有点恶心
                final ConnectionState newState = eventQueue.poll(pollMaxMs, TimeUnit.MILLISECONDS);
                if ( newState != null )
                {
                    if ( listeners.size() == 0 )
                    {
                        log.warn("There are no ConnectionStateListeners registered.");
                    }

                    listeners.forEach
                        (
                            new Function<ConnectionStateListener, Void>()
                            {
                                @Override
                                public Void apply(ConnectionStateListener listener)
                                {
                                    listener.stateChanged(client, newState);
                                    return null;
                                }
                            }
                        );
                }
                else if ( sessionExpirationPercent > 0 )
                {
                    synchronized(this)
                    {
                        checkSessionExpiration();
                    }
                }
            }
            catch ( InterruptedException e )
            {
                // swallow the interrupt as it's only possible from either a background
                // operation and, thus, doesn't apply to this loop or the instance
                // is being closed in which case the while test will get it
            }
        }
    }

    private void checkSessionExpiration()
    {
        if ( (currentConnectionState == ConnectionState.SUSPENDED) && (startOfSuspendedEpoch != 0) )
        {
            long elapsedMs = System.currentTimeMillis() - startOfSuspendedEpoch;
            int useSessionTimeoutMs = getUseSessionTimeoutMs();
            if ( elapsedMs >= useSessionTimeoutMs )
            {
                log.warn(String.format("Session timeout has elapsed while SUSPENDED. Injecting a session expiration. Elapsed ms: %d. Adjusted session timeout ms: %d", elapsedMs, useSessionTimeoutMs));
                try
                {
                    // 主动标记 close
                    Compatibility.injectSessionExpiration(client.getZookeeperClient().getZooKeeper());
                }
                catch ( Exception e )
                {
                    log.error("Could not inject session expiration", e);
                }
            }
        }
        else if ( currentConnectionState == ConnectionState.LOST )
        {
            try
            {
                // give ConnectionState.checkTimeouts() a chance to run, reset ensemble providers, etc.
                // 这里调用getZooKeeper()会触发ConnectionState.checkTimeouts()方法来重置zk的newConnectionString
                client.getZookeeperClient().getZooKeeper();
            }
            catch ( Exception e )
            {
                log.error("Could not get ZooKeeper", e);
            }
        }
    }

    private void setCurrentConnectionState(ConnectionState newConnectionState)
    {
        currentConnectionState = newConnectionState;
        startOfSuspendedEpoch = (currentConnectionState == ConnectionState.SUSPENDED) ? System.currentTimeMillis() : 0;
    }

    // 获取上次session超时时间，如果为0的话，取默认的值
    // 如果已经有了 suspended 的时间，那此时的 session超时时间还得乘以个 sessionExpirationPercent 百分比才是最后的时间
    private int getUseSessionTimeoutMs() {
        int lastNegotiatedSessionTimeoutMs = client.getZookeeperClient().getLastNegotiatedSessionTimeoutMs();
        int useSessionTimeoutMs = (lastNegotiatedSessionTimeoutMs > 0) ? lastNegotiatedSessionTimeoutMs : sessionTimeoutMs;
        useSessionTimeoutMs = sessionExpirationPercent > 0 && startOfSuspendedEpoch != 0 ? (useSessionTimeoutMs * sessionExpirationPercent) / 100 : useSessionTimeoutMs;
        return useSessionTimeoutMs;
    }

}
