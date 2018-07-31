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

package org.apache.curator.utils;

/**
 * 这里使用 Boolean.getBoolean(DebugUtils.PROPERTY_LOG_EVENTS);
 * 这样的方式从 parseBoolean(System.getProperty(name)); 获取内容
 * 应该是标志各级日志是否打印的工具类
 */
//[$3 nick 2018-07-31]
public class DebugUtils
{
    public static final String PROPERTY_LOG_EVENTS = "curator-log-events";
    public static final String PROPERTY_DONT_LOG_CONNECTION_ISSUES = "curator-dont-log-connection-problems";
    public static final String PROPERTY_LOG_ONLY_FIRST_CONNECTION_ISSUE_AS_ERROR_LEVEL = "curator-log-only-first-connection-issue-as-error-level";
    public static final String PROPERTY_REMOVE_WATCHERS_IN_FOREGROUND = "curator-remove-watchers-in-foreground";
    public static final String PROPERTY_VALIDATE_NAMESPACE_WATCHER_MAP_EMPTY = "curator-validate-namespace-watcher-map-empty";


    private DebugUtils()
    {
    }
}
