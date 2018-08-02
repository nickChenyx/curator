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

import org.apache.curator.ensemble.EnsembleProvider;
import org.apache.curator.utils.ZookeeperFactory;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

/**
 * 这个类就是持有 ZooKeeper 实例用的，具体是由 Helper 来维持实例
 */
//[$3 nick 2018-08-01]
class HandleHolder
{
    private final ZookeeperFactory zookeeperFactory;
    private final Watcher watcher;
    private final EnsembleProvider ensembleProvider;
    private final int sessionTimeout;
    private final boolean canBeReadOnly;

    private volatile Helper helper;

    /**
     * 熟悉的实现方式...
     * 这个接口用来提供一些关于 zk 的基础信息
     */
    private interface Helper
    {
        ZooKeeper getZooKeeper() throws Exception;
        
        String getConnectionString();

        int getNegotiatedSessionTimeoutMs();
    }

    HandleHolder(ZookeeperFactory zookeeperFactory, Watcher watcher, EnsembleProvider ensembleProvider, int sessionTimeout, boolean canBeReadOnly)
    {
        this.zookeeperFactory = zookeeperFactory;
        this.watcher = watcher;
        this.ensembleProvider = ensembleProvider;
        this.sessionTimeout = sessionTimeout;
        this.canBeReadOnly = canBeReadOnly;
    }

    ZooKeeper getZooKeeper() throws Exception
    {
        return (helper != null) ? helper.getZooKeeper() : null;
    }

    int getNegotiatedSessionTimeoutMs()
    {
        return (helper != null) ? helper.getNegotiatedSessionTimeoutMs() : 0;
    }

    String  getConnectionString()
    {
        return (helper != null) ? helper.getConnectionString() : null;
    }

    String getNewConnectionString()
    {
        String helperConnectionString = (helper != null) ? helper.getConnectionString() : null;
        return ((helperConnectionString != null) && !ensembleProvider.getConnectionString().equals(helperConnectionString)) ? helperConnectionString : null;
    }

    void closeAndClear(int waitForShutdownTimeoutMs) throws Exception
    {
        internalClose(waitForShutdownTimeoutMs);
        helper = null;
    }

    /**
     * 关闭现有的 zk 连接
     * 新建一个
     * 可以看到是 Helper 维持着 ZooKeeper 实例
     * @throws Exception
     */
    void closeAndReset() throws Exception
    {
        internalClose(0);

        // first helper is synchronized when getZooKeeper is called. Subsequent calls
        // are not synchronized.
        // 这个实现有点骚气，在内部 synchronized 内，开始了 new 的过程，然后就直接拿到了可用的 helper
        // 值得学习，完成了初始化创建同步，之后都不用同步块的作用！
        //[@@ nick]
        helper = new Helper()
        {
            private volatile ZooKeeper zooKeeperHandle = null;
            private volatile String connectionString = null;

            @Override
            public ZooKeeper getZooKeeper() throws Exception
            {
                synchronized(this)
                {
                    if ( zooKeeperHandle == null )
                    {
                        /*
                         * 还是靠 EnsembleProvider 提供 connectionString 来链接
                         */
                        connectionString = ensembleProvider.getConnectionString();
                        zooKeeperHandle = zookeeperFactory.newZooKeeper(connectionString, sessionTimeout, watcher, canBeReadOnly);
                    }

                    helper = new Helper()
                    {
                        @Override
                        public ZooKeeper getZooKeeper() throws Exception
                        {
                            return zooKeeperHandle;
                        }

                        @Override
                        public String getConnectionString()
                        {
                            return connectionString;
                        }

                        @Override
                        public int getNegotiatedSessionTimeoutMs()
                        {
                            return (zooKeeperHandle != null) ? zooKeeperHandle.getSessionTimeout() : 0;
                        }
                    };

                    return zooKeeperHandle;
                }
            }

            @Override
            public String getConnectionString()
            {
                return connectionString;
            }

            @Override
            public int getNegotiatedSessionTimeoutMs()
            {
                return (zooKeeperHandle != null) ? zooKeeperHandle.getSessionTimeout() : 0;
            }
        };
    }

    /**
     * 关闭zk 连接
     */
    private void internalClose(int waitForShutdownTimeoutMs) throws Exception
    {
        try
        {
            ZooKeeper zooKeeper = (helper != null) ? helper.getZooKeeper() : null;
            if ( zooKeeper != null )
            {
                Watcher dummyWatcher = new Watcher()
                {
                    @Override
                    public void process(WatchedEvent event)
                    {
                    }
                };
                /**
                 * 先把 watcher 给替换了，免得触发 balabla 事件
                 * 也就是说这样关掉是不会触发程序其他功能
                 */
                zooKeeper.register(dummyWatcher);   // clear the default watcher so that no new events get processed by mistake
                zooKeeper.close(waitForShutdownTimeoutMs);
            }
        }
        catch ( InterruptedException dummy )
        {
            Thread.currentThread().interrupt();
        }
    }
}
