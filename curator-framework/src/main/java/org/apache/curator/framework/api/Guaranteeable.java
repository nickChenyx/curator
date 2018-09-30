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
//[$3 nick 2018-09-29]
public interface Guaranteeable<T>
{
    /**
     * Solves edge cases where an operation may succeed on the server but connection failure occurs before a
     * response can be successfully returned to the client.
     * 
     * @see org.apache.curator.framework.api.GuaranteeableDeletable 
     *  
     * @return this
     */
    public T guaranteed();
}
