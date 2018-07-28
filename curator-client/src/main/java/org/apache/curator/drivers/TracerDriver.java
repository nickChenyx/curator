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
package org.apache.curator.drivers;

import java.util.concurrent.TimeUnit;

/**
 * Mechanism for timing methods and recording counters
 *
 * 专门用来记录运行时间和计数的工具
 */
//[$3 nick 2018-07-28]
public interface TracerDriver
{
    /**
     * Record the given trace event
     *
     * 这个名字起的怪怪的，以为是跟踪所有的堆栈信息。结果只是记录一下事件的执行事件。
     *
     * @param name of the event
     * @param time time event took
     * @param unit time unit
     */
    public void     addTrace(String name, long time, TimeUnit unit);

    /**
     * Add to a named counter
     *
     * 针对一个 name 做计数
     *
     * @param name name of the counter
     * @param increment amount to increment
     */
    public void     addCount(String name, int increment);
}
