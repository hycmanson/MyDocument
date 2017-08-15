# Handler的泄漏之谜
## 在Activity内的Handler如果是内部类而不带有static属性为什么会造成内存的泄漏
## 背景
之前在开发的时候遇见了在Android Lint给出了的一个提示HandlerLeak: Handler reference leaks。今天来追究一下其中的原因。

## Android Lint给出的提示与解决方案
通过gradle编译会在app\build\outputs下生成一份Lint的html文档，打开部分内容如下：

``` java?linenums
HandlerLeak: Handler reference leaks
../../src/main/java/com/hyc/handlerleakingcontext/LeakingActivity.java:12: This Handler class should be static or leaks might occur (new android.os.Handler(){})
   9  * Created by yichao.hyc on 2015/12/14.
  10  */
  11 public class LeakingActivity extends Activity {
  12     private final Handler mLeakyHandler = new Handler() {

  13         @Override
  14         public void handleMessage(Message msg) {
Priority: 4 / 10
Category: Performance
Severity: Warning
Explanation: Handler reference leaks.
Since this Handler is declared as an inner class, it may prevent the outer class from being garbage collected. If the Handler is using a Looper or MessageQueue for a thread other than the main thread, then there is no issue. If the Handler is using the Looper or MessageQueue of the main thread, you need to fix your Handler declaration, as follows: Declare the Handler as a static class; In the outer class, instantiate a WeakReference to the outer class and pass this object to your Handler when you instantiate the Handler; Make all references to members of the outer class using the WeakReference object.

More info:

To suppress this error, use the issue id "HandlerLeak" as explained in the Suppressing Warnings and Errors section.
```

大致的意思就是如果把Handler定义为内部类，就有可能造成内存泄露。解决它的办法就是把Handler定义为static的类型。

## 为什么static以后就不会泄露了呢？
打个断点看一下，如下图：

![LeakingProblem](https://raw.githubusercontent.com/hycmanson/AndroidLearning/master/MarkDownImages/handler1.png)

Handler居然会有Activity的引用，这个也是内部类的特性之一。那如果Activity在message send之前finish那么gc就无法回收finish的Activity因为还在被handler引用着。那这么问题原因找到了，就可以修改它了。

## 解决方案
思路就是把Handler定义为static如果需要引用外部类可以使用WeakReference的调用方式，在onDestory方法的时候可以把message remove掉。

``` java?linenums
package com.hyc.handlerleakingcontext;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.lang.ref.WeakReference;

public class UnLeakingActivity extends Activity {
    private UnLeakingHandler mUnLeakingHandler;
    private int mMessageWhat = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Post a message and delay its execution for 10 minutes.
        mUnLeakingHandler = new UnLeakingHandler(this);
        mUnLeakingHandler.sendEmptyMessageDelayed(mMessageWhat, 1000 * 60 * 10);
    }

    @Override
    protected void onDestroy() {
        if (mUnLeakingHandler != null) {
            mUnLeakingHandler.removeMessages(mMessageWhat);
        }
        super.onDestroy();
    }

    private final static class UnLeakingHandler extends Handler {
        WeakReference<UnLeakingActivity> mUnLeakingActivity;

        public UnLeakingHandler(UnLeakingActivity unLeakingActivity) {
            super();
            mUnLeakingActivity = new WeakReference<UnLeakingActivity>(unLeakingActivity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            mUnLeakingActivity.get().finish();
        }
    }
}
```

我们在debug一下看看，如下图:

![UnLeaking](https://raw.githubusercontent.com/hycmanson/AndroidLearning/master/MarkDownImages/handler2.png)

果然内部引用没有啦。

[源码见 https://github.com/hycmanson/MyDocument/tree/master/Code/HandlerLeakingContext](https://github.com/hycmanson/MyDocument/tree/master/Code/HandlerLeakingContext)


## PS
如果非static的内部类的生命周期大于Activity，应该避免在Activity内使用。
这里给一个泄漏的栗子：

![4Example](https://raw.githubusercontent.com/hycmanson/AndroidLearning/master/MarkDownImages/4example.png)

``` java
public class SampleActivity extends Activity {
    private final Handler mLeakyHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // ...
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Post a message and delay its execution for 10 minutes.
        mLeakyHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
            }
        }, 60 * 10 * 1000);

        // Go back to the previous Activity.
        finish();
    }
}
```
