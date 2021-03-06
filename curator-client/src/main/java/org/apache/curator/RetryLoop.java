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

import org.apache.curator.drivers.EventTrace;
import org.apache.curator.drivers.TracerDriver;
import org.apache.curator.utils.DebugUtils;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>Mechanism to perform an operation on Zookeeper that is safe against
 * disconnections and "recoverable" errors.</p>
 *
 * <p>
 * If an exception occurs during the operation, the RetryLoop will process it,
 * check with the current retry policy and either attempt to reconnect or re-throw
 * the exception
 * </p>
 *
 * Canonical usage:<br>
 * <pre>
 * RetryLoop retryLoop = client.newRetryLoop();
 * while ( retryLoop.shouldContinue() )
 * {
 *     try
 *     {
 *         // do your work
 *         ZooKeeper      zk = client.getZooKeeper();    // it's important to re-get the ZK instance in case there was an error and the instance was re-created
 *
 *         retryLoop.markComplete();
 *     }
 *     catch ( Exception e )
 *     {
 *         retryLoop.takeException(e);
 *     }
 * }
 * </pre>
 */
//[$3 nick 2018-08-04]
public class RetryLoop
{
    private boolean         isDone = false;
    private int             retryCount = 0;

    private final Logger            log = LoggerFactory.getLogger(getClass());
    private final long              startTimeMs = System.currentTimeMillis();
    private final RetryPolicy       retryPolicy;
    private final AtomicReference<TracerDriver>     tracer;

    /**
     * 这是个私有静态内部类，就用来做 Thred.sleep 的动作
     */
    private static final RetrySleeper  sleeper = new RetrySleeper()
    {
        @Override
        public void sleepFor(long time, TimeUnit unit) throws InterruptedException
        {
            unit.sleep(time);
        }
    };

    /**
     * Returns the default retry sleeper
     *
     * @return sleeper
     */
    public static RetrySleeper      getDefaultRetrySleeper()
    {
        return sleeper;
    }

    /**
     * Convenience utility: creates a retry loop calling the given proc and retrying if needed
     *
     * @param client Zookeeper
     * @param proc procedure to call with retry
     * @param <T> return type
     * @return procedure result
     * @throws Exception any non-retriable errors
     */
    public static<T> T      callWithRetry(CuratorZookeeperClient client, Callable<T> proc) throws Exception
    {
        return client.getConnectionHandlingPolicy().callWithRetry(client, proc);
    }

    /**
     * 构造函数里传入重试策略和追踪工具
     * @param retryPolicy
     * @param tracer
     */
    RetryLoop(RetryPolicy retryPolicy, AtomicReference<TracerDriver> tracer)
    {
        this.retryPolicy = retryPolicy;
        this.tracer = tracer;
    }

    /**
     * If true is returned, make an attempt at the operation
     *
     * @return true/false
     */
    public boolean      shouldContinue()
    {
        return !isDone;
    }

    /**
     * Call this when your operation has successfully completed
     */
    public void     markComplete()
    {
        isDone = true;
    }

    /**
     * Utility - return true if the given Zookeeper result code is retry-able
     * 判断是否需要 retry 的通用方法
     *
     * @param rc result code
     * @return true/false
     */
    public static boolean      shouldRetry(int rc)
    {
        return (rc == KeeperException.Code.CONNECTIONLOSS.intValue()) ||
            (rc == KeeperException.Code.OPERATIONTIMEOUT.intValue()) ||
            (rc == KeeperException.Code.SESSIONMOVED.intValue()) ||
            (rc == KeeperException.Code.SESSIONEXPIRED.intValue()) ||
            (rc == -13); // KeeperException.Code.NEWCONFIGNOQUORUM.intValue()) - using hard coded value for ZK 3.4.x compatibility
    }

    /**
     * Utility - return true if the given exception is retry-able
     * 开放的工具类，用于判断当前的异常是否是可以重连的异常类型
     *
     * @param exception exception to check
     * @return true/false
     */
    public static boolean      isRetryException(Throwable exception)
    {
        if ( exception instanceof KeeperException )
        {
            KeeperException     keeperException = (KeeperException)exception;
            return shouldRetry(keeperException.code().intValue());
        }
        return false;
    }

    /**
     * Pass any caught exceptions here
     *
     * @param exception the exception
     * @throws Exception if not retry-able or the retry policy returned negative
     */
    public void         takeException(Exception exception) throws Exception
    {
        boolean     rethrow = true;
        if ( isRetryException(exception) )
        {
            if ( !Boolean.getBoolean(DebugUtils.PROPERTY_DONT_LOG_CONNECTION_ISSUES) )
            {
                log.debug("Retry-able exception received", exception);
            }

            /**
             * 还是觉得设计非常美妙，这里的 retryPolicy， sleeper 以及方法体内的 tracer 都是接口，非常便于拓展！
             */
            if ( retryPolicy.allowRetry(retryCount++, System.currentTimeMillis() - startTimeMs, sleeper) )
            {
                new EventTrace("retries-allowed", tracer.get()).commit();
                if ( !Boolean.getBoolean(DebugUtils.PROPERTY_DONT_LOG_CONNECTION_ISSUES) )
                {
                    log.debug("Retrying operation");
                }
                rethrow = false;
            }
            else
            {
                new EventTrace("retries-disallowed", tracer.get()).commit();
                if ( !Boolean.getBoolean(DebugUtils.PROPERTY_DONT_LOG_CONNECTION_ISSUES) )
                {
                    log.debug("Retry policy not allowing retry");
                }
            }
        }

        if ( rethrow )
        {
            throw exception;
        }
    }
}
