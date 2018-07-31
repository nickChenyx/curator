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
package org.apache.curator.ensemble.exhibitor;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.Collection;

/**
 * POJO 描述"参展商"（Exhibitor）实例的集群
 * 主要是 zk 的服务提供者的连接属性
 * POJO for specifying the cluster of Exhibitor instances
 */
//[$3 nick 2018-07-31]
public class Exhibitors
{
    /**
     * 可能有多个 host name
     */
    private final Collection<String> hostnames;
    private final int restPort;
    /**
     * 在 Exhibitor 不可用的时候，使用备用的固定 connectionString 来链接
     */
    private final BackupConnectionStringProvider backupConnectionStringProvider;

    public interface BackupConnectionStringProvider
    {
        public String getBackupConnectionString() throws Exception;
    }

    /**
     *
     * @param hostnames set of Exhibitor instance host names
     * @param restPort the REST port used to connect to Exhibitor
     * @param backupConnectionStringProvider in case an Exhibitor instance can't be contacted, returns the fixed
     *                               connection string to use as a backup
     *                                         curator 好像很喜欢用这种类型，都是一个接口，然后让业务自己去拓展
     */
    public Exhibitors(Collection<String> hostnames, int restPort, BackupConnectionStringProvider backupConnectionStringProvider)
    {
        this.backupConnectionStringProvider = Preconditions.checkNotNull(backupConnectionStringProvider, "backupConnectionStringProvider cannot be null");
        this.hostnames = ImmutableList.copyOf(hostnames);
        this.restPort = restPort;
    }

    public Collection<String> getHostnames()
    {
        return hostnames;
    }

    public int getRestPort()
    {
        return restPort;
    }

    public String getBackupConnectionString() throws Exception
    {
        return backupConnectionStringProvider.getBackupConnectionString();
    }
}
