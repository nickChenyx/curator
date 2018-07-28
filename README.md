# Apache Curator

## Curator 阅读计划

### 项目结构

- curator-client 是最基础的项目，用来作 zk 的客户端操作
- curator-framework 是基于 client 做了一系列的针对 zk 的可用封装
- curator-recipes 则是基于 framework，使用已有的 zk 操作实现各种分布式工具
- curator-test 维护了一个可用于测试的 zk 客户端
- curator-x-async 基于 framework 做了一系列异步接口封装
- 其余暂不表

### 任务规划

主旨是从下至上阅读整个项目代码，主抓核心流程。

先从 client 和 test 看起，理解 zk client 的操作流程及封装。

### 规约

- 需要对项目文件进行注释，用来展示当前的进度
```
[$0 name time] 这样的规范来表示一个文件的阅读进度

$
 0 表示标记下一步要看
 1 表示正在进行中
 2 表示大部分完成，任有小部分收尾
 3 表示文件阅读结束
name
 标记阅读者，多个阅读者用 | 分割，使用英文 name，如 nickChen|cx 来表示两个读者
time
 用 yyyy-MM-dd 的形式表示

标记的位置应在 class 声明上一行，如:
//[$3 nick 2018-07-25]
class Demo {
...
}
```

根据根项目下的 `src/main/java/com/nickchen/util/ProcessTree.java` 可以导出当前文件的阅读状态。


## What's is Apache Curator?

Apache Curator is a Java/JVM client library for Apache ZooKeeper[1], a distributed coordination service.

Apache Curator includes a high-level API framework and utilities to make using Apache ZooKeeper much easier and more reliable. It also includes recipes for common use cases and extensions such as service discovery and a Java 8 asynchronous DSL.
For more details, please visit the project website: http://curator.apache.org/

[1] Apache ZooKeeper https://zookeeper.apache.org/

