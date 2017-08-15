在Android进行性能分析，系统提供了TraceView及Systrace两种工具。TraceView可对VM的所有Java Method进行Profile，能有效的发现APP性能问题。Systrace是Android 4.1中新增加的系统性能分析工具，主要针对Framework的性能，比如SurfaceFlinger、Audio、View、CPU Load、workqueue等系统模块，尤其擅长Profile Graphic系统(Systrace源自于Project Butter)。熟练的使用TraceView和Systrace性能分析工具，在解决性能问题时，可以达到事半功倍的效果，然而TraceView和Systrace都存在各自的缺点：

1. TraceView会Trace VM的所有Jave Method，会禁止Dalvik的JIT，性能开销也可达几倍甚至几十倍，性能开销太大，而且对不同类型的函数，开销影响不一致，偏离了原始性能，导致数据的可性度下降，而且对所有的Jave Method进行Trace，Trace的函数太多，导致分析trace文件过于繁杂。
2. Systrace很好的Trace了系统性能，比如Vsync的到来、SurfacFling耗时，甚至是OpenGL Command及CPU运行的work，这些对系统分析来说，是非常详尽的信息，对性能的影响也微乎其微。然而在分析APP的性能上，Systrace显的力不从心，就从Systrace最擅长的卡顿分析来说，也只能知道有Jank的存在，而没法对APP的性能进行下一步的Profile。

简而言之，TraceView的缺点在于Trace的函数过多，导致过多的性能开销以及分析复杂，而Systrace恰恰相反，有很好的性能表现，却只因其设计初衷是系统性能分析工具，对APP的性能分析帮助有限。能否有一种方式，兼有两者的优点，在有Systrace的性能同时，也能对APP的性能进行有效的分析？

TraceView会Trace VM的所有Jave Method，也就是说，Framework的类也会毫无保留的被Trace。显然，在进行APP开发的时候，我们更关心的是APP本身的代码性能表现。因此，如果可以只Trace APP的代码，不仅一目了然的知道APP代码的性能，并且大大减少Trace的数据量，从而达到减少开销的效果。在Trace APP的代码同时，我们也希望知道系统关键模块的Performance信息，而Systrace为我们提供了足够多的Android系统模块的性能信息。如果能够使用Systrace分析APP的性能，那并有两者的优点。

Systrace只对系统模块的Key Point进行跟踪，比如ViewRootImpl：

```java
    private void performMeasure(int childWidthMeasureSpec, int childHeightMeasureSpec) {
        Trace.traceBegin(Trace.TRACE_TAG_VIEW, "measure");
        try {
            mView.measure(childWidthMeasureSpec, childHeightMeasureSpec);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_VIEW);
        }
    }
```

对View的Measure进行Trace。我们更希望的是在APP的代码里面也能添加这样的Trace代码。在Android 4.3极其以上版本中，Android我APP提供了Trace.beginSection和Trace.endSection接口，因此，只要在APP的函数出入口添加相应的代码便可以实现。然而事实确是，手淘总共有超过20多万的方法存在，人为的为每一个方法添加Trace代码，工作繁重而且未必能确保毫无遗漏。如果可以自动的完成这项工作，那将会从这种繁重的工作中解放出来。

我们都知道Android运行的是dex文件是由class文件转换而来，可在class转换成dex的时候，用ASM进行AOP，在Method的 出入口注入Trace.beginSection及Trace.endSection代码，可完成对APP所有的代码进行Trace的工作。

整个过程的操作如下：
1. find ~/.m2/repository/ -name *.dex | xargs rm –rf（删除本地maven的dex缓存）。
2. 打开main_build的pom的agent-maven-plugin并修改版本为1.0.1.3-SNAPSHOT：

```xml
<plugin>
    <groupId>com.taobao.android</groupId>
    <artifactId>agent-maven-plugin</artifactId>
    <version>1.0.1.3-SNAPSHOT</version>
    <executions>
        <execution>
            <goals>
                <goal>instrument</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

并打包，安装APK。
3. python systrace.py --app=com.taobao.taobao -b 40960（指定app的名字，设置Ftrace的BufferQueue的大小，可以添加其他Systrace选项）, 也可以在Eclipse或者Android Studio里面完成。
4. sed -ig 's/name in parentNames/true/g' trace.html(Trace不做begin跟end的检测)。
5. 分析trace.html文件。

可得到如下效果的Trace文件：

![1](https://raw.githubusercontent.com/hycmanson/AndroidLearning/master/MarkDownImages/Trace4Performance1.png)

图一 Trace效果图

用这种方案分析手淘的启动性能：

![2](https://raw.githubusercontent.com/hycmanson/AndroidLearning/master/MarkDownImages/Trace4Performance2.png)

TBLocationClient在UI Thread监听手淘的启动，并且进行了耗时的JSON解析。

![3](https://raw.githubusercontent.com/hycmanson/AndroidLearning/master/MarkDownImages/Trace4Performance3.png)

在MainActivity3 onCreate的时候，与CoordTask #1存在Lock Content，而CoordTask #1运作状态为：

![4](https://raw.githubusercontent.com/hycmanson/AndroidLearning/master/MarkDownImages/Trace4Performance4.png)

CoordTask #1与UI Thread在进行inflate，inflate是有同步锁的，此时可提高MainActivity3 onCreate优先级别来获取更多的资源，提供启动的性能。

通过使用这种自动注入Android Trace代码分析性能方案，有以下效果：
1. 性能开销几乎可以忽略。依赖于Systrace，而Systrace Framework是Atrace，Kernel是Ftrace。Ftrace的性能，在Kernel经过多年的验证，而且完全抛弃了TraceView笨重的实现。
2. 在Trace APP代码的同时，可充分利用Systrace System功能分析APP的性能。比如多Lock Content、Jank等复杂问题，依旧清晰可见，一目了然。

参考文献：
http://developer.android.com/intl/zh-cn/tools/help/systrace.html
http://developer.android.com/intl/zh-cn/tools/debugging/systrace.html
http://developer.android.com/intl/zh-cn/tools/debugging/debugging-tracing.html
http://blog.csdn.net/innost/article/details/9008691
