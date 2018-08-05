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
package org.apache.curator.retry;

import org.apache.curator.RetrySleeper;

/**
 * A retry policy that retries until a given amount of time elapses
 * 这个不同于父类是使用重试次数来限制的，
 * 而是利用已经使用的事件来进行限制。
 */
//[$3 nick 2018-08-05]
public class RetryUntilElapsed extends SleepingRetry
{
    private final int maxElapsedTimeMs;
    private final int sleepMsBetweenRetries;

    public RetryUntilElapsed(int maxElapsedTimeMs, int sleepMsBetweenRetries)
    {
        super(Integer.MAX_VALUE);
        this.maxElapsedTimeMs = maxElapsedTimeMs;
        this.sleepMsBetweenRetries = sleepMsBetweenRetries;
    }

    @Override
    public boolean allowRetry(int retryCount, long elapsedTimeMs, RetrySleeper sleeper)
    {
                                                                        // 关键代码，比较当前的已消耗事件是否超过最大限制
        return super.allowRetry(retryCount, elapsedTimeMs, sleeper) && (elapsedTimeMs < maxElapsedTimeMs);
    }

    @Override
    protected long getSleepTimeMs(int retryCount, long elapsedTimeMs)
    {
        return sleepMsBetweenRetries;
    }
}

