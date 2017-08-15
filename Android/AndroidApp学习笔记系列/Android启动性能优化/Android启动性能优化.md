Android有着许多可视化性能工具：TraceView、Systrace、HierarchyViewer等等，各自的优缺点在[通过自动注入Android Trace代码的性能解决方案](https://github.com/hycmanson/MyDocument/blob/master/Android/AndroidApp%E5%AD%A6%E4%B9%A0%E7%AC%94%E8%AE%B0%E7%B3%BB%E5%88%97/%E9%80%9A%E8%BF%87%E8%87%AA%E5%8A%A8%E6%B3%A8%E5%85%A5Android%20Trace%E4%BB%A3%E7%A0%81%E7%9A%84%E6%80%A7%E8%83%BD%E8%A7%A3%E5%86%B3%E6%96%B9%E6%A1%88/%E9%80%9A%E8%BF%87%E8%87%AA%E5%8A%A8%E6%B3%A8%E5%85%A5Android%20Trace%E4%BB%A3%E7%A0%81%E7%9A%84%E6%80%A7%E8%83%BD%E8%A7%A3%E5%86%B3%E6%96%B9%E6%A1%88.md)有介绍。通过自动注入Android Trace代码的方式，来分析启动的性能。

#1.SvipLifeCycleObserver.onActivityResumed的Mtop请求:

![1](https://raw.githubusercontent.com/hycmanson/AndroidLearning/master/MarkDownImages/Performance1.png)

图一 Welcome.onResume耗时

显然，在Welcome onResume的时候SvipLifeCycleObserver onActivityResumed耗时在秒级别以上，SvipLifeCycleObserver onActivityResumed在等待Mtop的初始化完成，而SvipLifeCycleObserver onActivityResumed是在main thread里面进行，导致了整个启动时间的延长，测试了一下SvipLifeCycleObserver onActivityResumed的占比情况：

|冷启动(ms)|SVipBusiness(ms)|占比(%)|
|---------|----------------|------|
|4112     |1165            |28.33 |
|3765     |790             |20.98 |
|4460     |1226            |27.49 |
|4514     |1235            |27.36 |
|4565     |1184            |25.94 |
|4441     |1219            |27.45 |
|4520     |1174            |25.97 |
|4277     |1171            |27.38 |
|4486     |1242            |27.69 |
|4204     |889             |21.15 |

SvipLifeCycleObserver onActivityResumed异步化Mtop后：

```java
        //  mSvipBusiness.getIsApassUser();
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                mSvipBusiness.getIsApassUser();
                return null;
            }
        }.execute();
```

几乎没有耗时：

```java
01-05 16:36:59.252 D/SvipLifeCycleObserver    (22469): SVipBusiness time : 2
01-05 16:37:13.002 D/SvipLifeCycleObserver     (23728): SVipBusiness time : 0
01-05 16:37:38.242 D/SvipLifeCycleObserver     (25561): SVipBusiness time : 3
01-05 16:39:17.502 D/SvipLifeCycleObserver     (32550): SVipBusiness time : 1
```

#2.AppMonitor Init:

AppMonitor是性能埋点的初始化，在TaobaoInitializer里面：

```java
    @Global @Async
    public void initBaseSDK() {
        //初始化app-monitor-sdk
        AppMonitor.init(Globals.getApplication());
    ...
```

显然是一个Async的init过程，但Trace文件里面：

![2](https://raw.githubusercontent.com/hycmanson/AndroidLearning/master/MarkDownImages/Performance2.png)

图二 AppMonitor Init线程

AppMonitor Init既然在UI Thread里面进行的，为了近一步确认，dump 出AppMonitor Init的Stack信息：

```java
01-06 21:07:15.374 27044 27044 E AppMonitor: app monitor init
01-06 21:07:15.374 27044 27044 D AppMonitor: java.lang.Throwable
01-06 21:07:15.374 27044 27044 D AppMonitor:  at com.alibaba.mtl.appmonitor.AppMonitor.init(AppMonitor.java:79)
01-06 21:07:15.374 27044 27044 D AppMonitor:  at com.taobao.tao.frameworkwrapper.AtlasMonitorImpl.<init>(AtlasMonitorImpl.java:25)
01-06 21:07:15.374 27044 27044 D AppMonitor:  at com.taobao.tao.TaobaoApplicationFake.onCreate(TaobaoApplicationFake.java:88)
01-06 21:07:15.374 27044 27044 D AppMonitor:  at com.taobao.tao.TaobaoApplication.onCreate(TaobaoApplication.java:68)
01-06 21:07:15.374 27044 27044 D AppMonitor:  at android.app.Instrumentation.callApplicationOnCreate(Instrumentation.java:1007)
01-06 21:07:15.374 27044 27044 D AppMonitor:  at android.taobao.atlas.runtime.InstrumentationHook.callApplicationOnCreate(InstrumentationHook.java:702)
...

01-06 21:07:16.054 27044 27667 E AppMonitor: app monitor init
01-06 21:07:16.054 27044 27667 D AppMonitor: java.lang.Throwable
01-06 21:07:16.054 27044 27667 D AppMonitor:  at com.alibaba.mtl.appmonitor.AppMonitor.init(AppMonitor.java:79)
01-06 21:07:16.054 27044 27667 D AppMonitor:  at com.taobao.tao.TaobaoInitializer.initBaseSDK(TaobaoInitializer.java:297)
...
```

在TaobaoInitializer异步去初始化AppMonitor之前，AtlasMonitorImpl就已经在主线程里面去拉起AppMonitor，导致TaobaoInitializer异步化失效。

#3.getAppKey性能:
getAppKey是获取App Key函数，启动的时候：

![3](https://raw.githubusercontent.com/hycmanson/AndroidLearning/master/MarkDownImages/Performance3.png)

图三 getAppKey耗时

Dump出Stack信息：

```java
01-06 12:26:03.767 15004 15004 D Init     : get app key start : 0
01-06 12:26:03.768 15004 15004 D Init     : java.lang.Throwable
01-06 12:26:03.768 15004 15004 D Init     :  at com.taobao.tao.util.GetAppKeyFromSecurity.getAppKey(Taobao:25)
01-06 12:26:03.768 15004 15004 D Init     :  at com.taobao.tao.frameworkwrapper.c.<init>(Taobao:26)
01-06 12:26:03.768 15004 15004 D Init     :  at com.taobao.tao.at.onCreate(Taobao:88)
01-06 12:26:03.768 15004 15004 D Init     :  at com.taobao.tao.TaobaoApplication.onCreate(Taobao:68)
01-06 12:26:03.768 15004 15004 D Init     :  at android.app.Instrumentation.callApplicationOnCreate(Instrumentation.java:1011)
01-06 12:26:03.768 15004 15004 D Init     :  at android.taobao.atlas.runtime.InstrumentationHook.callApplicationOnCreate(Taobao:702)
...
01-06 12:26:03.891 15004 15004 D Init     : get app key end , time : 124
```

Atlas在初始化Appmonitor的时候调用了getAppKey。

AppMonitor Init和getAppKey对启动影响情况如下：

|冷启动(ms)|AppMonitor Init(ms)|getAppKey(ms)|AppMonitor Init和getAppKey占比(%)|
|---------|-------------------|-------------|--------------------------------|
|3100     |103                |263          |11.81                           |
|2374     |85                 |190          |11.58                           |
|2410     |86                 |247          |13.82                           |
|2344     |68                 |220          |12.29                           |
|2719     |67                 |221          |10.59                           |
|2797     |67                 |237          |10.87                           |
|2719     |67                 |221          |10.59                           |
|2797     |67                 |237          |10.87                           |
|2605     |105                |231          |12.90                           |
|2781     |74                 |216          |10.43                           |
|2438     |87                 |238          |13.33                           |
|2358     |66                 |215          |11.92                           |
|2394     |71                 |219          |12.11                           |
性能分析如同剥竹笋一般，是抽丝剥茧的过程，有效的性能优化工具，可以达到事半功倍的效果。

There are known knowns. These are things we know that we know.
There are known unknowns. That is to say, there are things that we know we don't know. But there are also unknown unknowns.
There are things we don't know we don't know.
--Donald Rumsfeld
共勉之！
