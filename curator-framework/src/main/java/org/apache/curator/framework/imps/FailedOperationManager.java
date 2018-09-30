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
package org.apache.curator.framework.imps;

import org.apache.curator.framework.CuratorFramework;

import org.apache.curator.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

/**
 * 通过向 zk 添加 T 数据，用来确保该操作会执行成功
 *
 */
//[$3 nick 2018-09-29]
abstract class FailedOperationManager<T>
{
    private final Logger log = LoggerFactory.getLogger(getClass());
    protected final CuratorFramework client;
    
    @VisibleForTesting
    volatile FailedOperationManagerListener<T> debugListener = null;
    
    interface FailedOperationManagerListener<T>
    {
       public void pathAddedForGuaranteedOperation(T detail);
    }

    FailedOperationManager(CuratorFramework client)
    {
        this.client = client;
    }

    void addFailedOperation(T details)
    {
        if ( debugListener != null )
        {
            debugListener.pathAddedForGuaranteedOperation(details);
        }
        
        
        if ( client.getState() == CuratorFrameworkState.STARTED )
        {
            log.debug("Details being added to guaranteed operation set: " + details);
            try
            {
                executeGuaranteedOperationInBackground(details);
            }
            catch ( Exception e )
            {
            ThreadUtils.checkInterrupted(e);
            addFailedOperation(details);
        }
        }
    }

    /**
     * 主要扩展点，用来做 guarantee 操作
     * @param details details
     * @throws Exception e
     */
    protected abstract void executeGuaranteedOperationInBackground(T details) throws Exception;
}
