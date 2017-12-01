# SpiderSDK
### [Deprecated] 效率太慢而且不大好用,准备重新写一个了

### 运行模式
1 单机模式(不推荐使用 能力较弱)                        
2 分布式模式(无身份)                    
3 分布式有身份(master,agent),还在开发中,见[SpiderMaster](https://github.com/xiaoshenke/SpiderMaster)

### 能力
1 反屏蔽识别                              
2 动态输入代理ip(暂不支持代理池)      
3 重试队列                 
4 任务中断可恢复

### 用法
工程设计思路:“基于配置“,即一切可配置化,包括运行模式,任务间隔,是否可重试,是否允许重复任务等等         

###### 1 配置文件：                          
配置文件默认路径 path-to-jar/conf/jobmanager.properties                
支持的配置项包括：                

````
enableRetrySpider  				//是否可重试
distributeMode=true 			//是否运行在分布式模式
enableScheduleImmediately=true //是否立即执行
enableInsertDuplicateJob=true  //是否允许插入重复任务
spiderScan 					    //扫描spider业务包
redisIp           				//分布式下redis server的ip
redisPort						//分布式下redis server的port
````

###### 2 业务Spider写法(这里介绍分布式模式下的写法)                

2.1 实现BaseSpider子类               

````
static BaseSpider.fromUrlNode  //用于从redis db反序列化
static BaseSpider.toUrlNode		//用于序列化到redis db

BaseSpider.buildRequest        //拼凑httprequest
BaseSpider.parseRealData       //解析

BaseSpider.checkBlockAndFailThisSpider(int) //根据httpCode判断是否被屏蔽
BaseSpider.checkBlockAndFailThisSpider(String) //根据网页内容判断是否屏蔽

````
分布式spider使用redis作为任务队列,因此需要序列化反序列化。这里使用两个静态函数约束实现spider扫描。注意在jobmanager.properties下,加上spiderScan=your-package有助于准确扫描。 
               


2.3 开启任务池                

````
JobManagerConfig.init();  			//读取配置文件
jobManager = JobManagerFactory.getJobManager();
jobManager.start();       			//开启任务池

IJob job = JobProvider.getJob();
job.setRealRunnable(your-spider);  //你的spider
jobManager.putJob(job);


````   



### 局限
1 目前该SDK仅支持mac环境，linux下并无测试。      
2 分布式模式下需要安装redis。                    





     













