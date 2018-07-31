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

/**
 * 抽象重试连接时使用的策略
 *
 * Abstracts the policy to use when retrying connections
 */
//[$3 nick 2018-07-31]
public interface RetryPolicy
{
    /**
     * Called when an operation has failed for some reason. This method should return
     * true to make another attempt.
     *
     * 非常简单的设计了，从这一个接口开始引申到各个不同的实现
     * 这个接口有三个入参， 重试次数，已经消耗时间，sleeper
     *
     * @param retryCount the number of times retried so far (0 the first time)
     * @param elapsedTimeMs the elapsed time in ms since the operation was attempted
     * @param sleeper use this to sleep - DO NOT call Thread.sleep
     * @return true/false
     */
    public boolean      allowRetry(int retryCount, long elapsedTimeMs, RetrySleeper sleeper);
}
