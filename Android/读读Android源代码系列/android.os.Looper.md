# 读读Android源代码 android.os.Looper

## Looper不会停止的消息处理机
[Reference 4 Looper](https://developer.android.com/reference/android/os/Looper.html)
[Source 4 Looper](http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.2.2_r1/android/os/Looper.java)

从字面上了解是“循环者”，也就是在不停的循环状态。所谓Looper线程就是循环工作的线程。在程序开发中我们经常会需要一个线程不断循环，一旦有新任务则执行，执行完继续等待下一个任务，这就是Looper。
这里请不要把Looper与线程之间的概念相混淆，Looper其实可以看作线程的一个功能。一个普通的线程是没有Looper的功能的。通过在当前线程执行Looper.prepare()之后，当前线程就有了Looper的功能。使当前线程成为一个Looper线程。

## prepare()
在一个普通线程会调用prepare()方法
``` java?linenums
public class Looper {
    ...
    // sThreadLocal.get() will return null unless you've called prepare().
    static final ThreadLocal<Looper> sThreadLocal = new ThreadLocal<Looper>();
     /** Initialize the current thread as a looper.
      * This gives you a chance to create handlers that then reference
      * this looper, before actually starting the loop. Be sure to call
      * {@link #loop()} after calling this method, and end it by calling
      * {@link #quit()}.
      */
    public static void prepare() {
        prepare(true);
    }

    private static void prepare(boolean quitAllowed) {
        if (sThreadLocal.get() != null) {
            throw new RuntimeException("Only one Looper may be created per thread");
        }
        sThreadLocal.set(new Looper(quitAllowed));
    }

    private Looper(boolean quitAllowed) {
        mQueue = new MessageQueue(quitAllowed);
        mRun = true;
        mThread = Thread.currentThread();
    }
    ...
}
```
这里有一个不太好理解的地方。
每个线程需要初始化Looper时，都需要调用prepare()方法（主线程除外，后面会写到）。

那么根据prepare()方法执行第一个线程就会执行到sThreadLocal.set(new Looper(quitAllowed));这一行。
而第二个线程执行prepare的时候就会执行到throw new RuntimeException("Only one Looper may be created per thread");，注意看static final ThreadLocal<Looper> sThreadLocal是这样定义的。static表示共用内存引用的不是么？

然而事实并非如此，我们都知道每个线程可以拥有一个Looper而且仅能只有一个Looper。那么在一个应用里面多个线程拥有多个Looper也是很正常的事情。

那么为什么会与我们推理分析的不一样呢？原因就在于ThreadLocal的实现。
[Source 4 ThreadLocal](http://grepcode.com/file/repository.grepcode.com/java/root/jdk/openjdk/6-b27/java/lang/ThreadLocal.java)
``` java?linenums
public class ThreadLocal<T> {
    /* Thanks to Josh Bloch and Doug Lea for code reviews and impl advice. */

    /**
     * Creates a new thread-local variable.
     */
    public ThreadLocal() {}

    /**
     * Returns the value of this variable for the current thread. If an entry
     * doesn't yet exist for this variable on this thread, this method will
     * create an entry, populating the value with the result of
     * {@link #initialValue()}.
     *
     * @return the current value of the variable for the calling thread.
     */
    @SuppressWarnings("unchecked")
    public T get() {
        // Optimized for the fast path.
        Thread currentThread = Thread.currentThread();
        Values values = values(currentThread);
        if (values != null) {
            Object[] table = values.table;
            int index = hash & values.mask;
            if (this.reference == table[index]) {
                return (T) table[index + 1];
            }
        } else {
            values = initializeValues(currentThread);
        }
        return (T) values.getAfterMiss(this);
    }

    /**
     * Sets the value of this variable for the current thread. If set to
     * {@code null}, the value will be set to null and the underlying entry will
     * still be present.
     *
     * @param value the new value of the variable for the caller thread.
     */
    public void set(T value) {
        Thread currentThread = Thread.currentThread();
        Values values = values(currentThread);
        if (values == null) {
            values = initializeValues(currentThread);
        }
        values.put(this, value);
    }
    ...
}
```

从它的get、set方法的实现不难看出ThreadLocal中存储的T（泛型）是与所在线程有关系的。再回到Looper看不同的线程通过sThreadLocal.get()方法得到的Looper对象都是不一样的。ThreadLocal<Looper> sThreadLocal之所以定义为static的类型是为了让所有线程的Looper集中统一管理。Looper.prepare()方法就保证了Looper在这个线程存在的唯一性。

## loop()
这个方法就是Looper的主要功能了。
``` java?linenums
public class Looper {
    ...
    /**
     * Run the message queue in this thread. Be sure to call
     * {@link #quit()} to end the loop.
     */
    public static void loop() {
        final Looper me = myLooper();
        if (me == null) {
            throw new RuntimeException("No Looper; Looper.prepare() wasn't called on this thread.");
        }
        final MessageQueue queue = me.mQueue;

        // Make sure the identity of this thread is that of the local process,
        // and keep track of what that identity token actually is.
        Binder.clearCallingIdentity();
        final long ident = Binder.clearCallingIdentity();

        for (;;) {
            Message msg = queue.next(); // might block
            if (msg == null) {
                // No message indicates that the message queue is quitting.
                return;
            }

            // This must be in a local variable, in case a UI event sets the logger
            Printer logging = me.mLogging;
            if (logging != null) {
                logging.println(">>>>> Dispatching to " + msg.target + " " +
                        msg.callback + ": " + msg.what);
            }

            msg.target.dispatchMessage(msg);

            if (logging != null) {
                logging.println("<<<<< Finished to " + msg.target + " " + msg.callback);
            }

            // Make sure that during the course of dispatching the
            // identity of the thread wasn't corrupted.
            final long newIdent = Binder.clearCallingIdentity();
            if (ident != newIdent) {
                Log.wtf(TAG, "Thread identity changed from 0x"
                        + Long.toHexString(ident) + " to 0x"
                        + Long.toHexString(newIdent) + " while dispatching to "
                        + msg.target.getClass().getName() + " "
                        + msg.callback + " what=" + msg.what);
            }

            msg.recycle();
        }
    }
    ...
｝
```
注意看一下上面的代码19行，消息循环的逻辑开始。
第33行通过Message的成员变量target分发Message，也需有些读者已经猜到了，这个target就是一个Handler对象。
[See Message Source](http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.2.2_r1/android/os/Message.java/#89)
进入这个loop方法就无限的循环起来了，直到MessageQueue.next()返回的Message为null以后这个循环就结束了。跟踪一下这个方法能让它返回为空就是在以下的代码块。mQuiting的控制请参考下面quit()方法。
``` java?linenums
public class MessageQueue {
    final Message next() {
    ...
            synchronized (this) {
                if (mQuiting) {
                    return null;
                }
    ...
    }
}
```
[See MessageQueue Source](http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.2.2_r1/android/os/MessageQueue.java?av=f#127)

## quit()
``` java
public class Looper {
    ...    
    /**
     * Quits the looper.
     *
     * Causes the {@link #loop} method to terminate as soon as possible.
     */
    public void quit() {
        mQueue.quit();
    }
    ...
}
```

``` java?linenums
public class MessageQueue {
    ...
    final void quit() {
        if (!mQuitAllowed) {
            throw new RuntimeException("Main thread not allowed to quit.");
        }

        synchronized (this) {
            if (mQuiting) {
                return;
            }
            mQuiting = true;
        }
        nativeWake(mPtr);
    }
    ...
}
```
这一步就是设置mQuiting标志，让Looper退出。
[See MessageQueue Source](http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.2.2_r1/android/os/MessageQueue.java?av=f#127)

## prepareMainLooper()
这个就是主线程创建Looper时使用的方法。
``` java?linenums
public class Looper {
    ...
    private static Looper sMainLooper;  // guarded by Looper.class
    /**
     * Initialize the current thread as a looper, marking it as an
     * application's main looper. The main looper for your application
     * is created by the Android environment, so you should never need
     * to call this function yourself.  See also: {@link #prepare()}
     */
    public static void prepareMainLooper() {
        prepare(false);
        synchronized (Looper.class) {
            if (sMainLooper != null) {
                throw new IllegalStateException("The main Looper has already been prepared.");
            }
            sMainLooper = myLooper();
        }
    }

    /**
     * Return the Looper object associated with the current thread.  Returns
     * null if the calling thread is not associated with a Looper.
     */
    public static Looper myLooper() {
        return sThreadLocal.get();
    }
    ...
}
```
可以看出这个与一般线程创建的区别就在于传入prepare的参数，而参数名字也描述的非常清楚quitAllowed，是否允许退出。

这里比较特别的一点就是主线程的Looper虽然也是与一般线程一样存储到了sThreadLocal这个对象里，但是Looper.class里面定义了一个static的属性sMainLooper为了守护主线程的Looper对象。

下面就是调用prepareMainLooper()方法的地方，可以看出它在初始化Activity主线程时也初始化的Looper同时，也让Looper loop起来。
[Usages of prepareMainLooper()](http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.2.2_r1/android/app/ActivityThread.java#5025)
``` java?linenums
public final class ActivityThread {
    ...
    public static void main(String[] args) {
        SamplingProfilerIntegration.start();
        // CloseGuard defaults to true and can be quite spammy.  We
        // disable it here, but selectively enable it later (via
        // StrictMode) on debug builds, but using DropBox, not logs.
        CloseGuard.setEnabled(false);
        Environment.initForCurrentUser();
        // Set the reporter for event logging in libcore
        EventLogger.setReporter(new EventLoggingReporter());
        Process.setArgV0("<pre-initialized>");
        Looper.prepareMainLooper();
        ActivityThread thread = new ActivityThread();
        thread.attach(false);
        if (sMainThreadHandler == null) {
            sMainThreadHandler = thread.getHandler();
        }
        AsyncTask.init();
        if (false) {
            Looper.myLooper().setMessageLogging(new
                    LogPrinter(Log.DEBUG, "ActivityThread"));
        }
        Looper.loop();
        throw new RuntimeException("Main thread loop unexpectedly exited");
    }
    ...
}
```
## dump()
``` java?linenums
public class Looper {
    ...
    public void dump((Printer pw, String prefix) {
        pw = PrefixPrinter.create(pw, prefix);
        pw.println(this.toString());
        pw.println("mRun=" + mRun);
        pw.println("mThread=" + mThread);
        pw.println("mQueue=" + ((mQueue != null) ? mQueue : "(null"));
        if (mQueue != null) {
            synchronized (mQueue) {
                long now = SystemClock.uptimeMillis();
                Message msg = mQueue.mMessages;
                int n = 0;
                while (msg != null) {
                    pw.println("  Message " + n + ": " + msg.toString(now));
                    n++;
                    msg = msg.next;
                }
                pw.println("(Total messages: " + n + ")");
            }
        }
    }
    ...
}
```
把目前Looper含有的成员打印出来。
