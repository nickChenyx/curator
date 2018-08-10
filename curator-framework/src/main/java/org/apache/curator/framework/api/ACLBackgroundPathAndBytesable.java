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
package org.apache.curator.framework.api;

/**
 * 这里也是一种设计风格，ParentACLable 的泛型是 BackgroundPathAndBytesable，且方法内返回的就是泛型类型，
 * 这样就可以链式调用 BackgroundPathAndBytesable 的方法了。
 * 这里很多接口都是这样的风格的实现。
 */
//[$3 nick 2018-08-08]
//[@@ nick]
public interface ACLBackgroundPathAndBytesable<T> extends
    ParentACLable<BackgroundPathAndBytesable<T>>,
    BackgroundPathAndBytesable<T>
{
}
