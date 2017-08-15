# 读读Android源代码 android.os.Handler

## 接收指令（Message）才会干活的Handler机器人

[Reference 4 Handler](https://developer.android.com/reference/android/os/Handler.html)

[Source 4 Handler](http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.2.2_r1/android/os/Handler.java)

Google给出了Handler的两大用处：
1.计划让Message或是Runable在未来的某个时间执行。
2.安排某个动作在其他线程执行。
There are two main uses for a Handler: (1) to schedule messages and runnables to be executed as some point in the future; and (2) to enqueue an action to be performed on a different thread than your own.

Handler的一般使用方式：
1.在主线程创建一个Handler的对象。
2.在其他线程调用这个Handler对象的post、sendMessage方法
3.Handler的handleMessage收到Message处理。

## Handler()
我们先来看看构造器。
``` java?linenums
public class Handler {
    ...
    /**
     * Default constructor associates this handler with the {@link Looper} for the
     * current thread.
     *
     * If this thread does not have a looper, this handler won't be able to receive messages
     * so an exception is thrown.
     */
    public Handler() {
        this(null, false);
    }

    /**
     * Constructor associates this handler with the {@link Looper} for the
     * current thread and takes a callback interface in which you can handle
     * messages.
     *
     * If this thread does not have a looper, this handler won't be able to receive messages
     * so an exception is thrown.
     *
     * @param callback The callback interface in which to handle messages, or null.
     */
    public Handler(Callback callback) {
        this(callback, false);
    }

    /**
     * Use the provided {@link Looper} instead of the default one.
     *
     * @param looper The looper, must not be null.
     */
    public Handler(Looper looper) {
        this(looper, null, false);
    }

    /**
     * Use the provided {@link Looper} instead of the default one and take a callback
     * interface in which to handle messages.
     *
     * @param looper The looper, must not be null.
     * @param callback The callback interface in which to handle messages, or null.
     */
    public Handler(Looper looper, Callback callback) {
        this(looper, callback, false);
    }
    ...
}
```
### 可以看出对外公开的接口有这四个：
+ Handler()
+ Handler(Handler.Callback callback)
+ Handler(Looper looper)
+ Handler(Looper looper, Handler.Callback callback)

#### Handler()
这个方法会自动获取当前线程的Looper，如果没有Looper则会丢出异常。（如果在主线程初始化，主线程在初始化时会创建一个Looper。如果在其他线程就需要先初始化一个Looper再创建Handler对象。）

![4Example](https://raw.githubusercontent.com/hycmanson/MyDocument/master/MarkDownImages/4example.png)
``` java?linenums
public class LooperThread extends Thread {
    @Override
    public void run() {
        // 将当前线程初始化为Looper线程
        Looper.prepare();
        // 实例化handler
        ...
        // 开始循环处理消息队列
        Looper.loop();
    }
}
```

#### Handler(Handler.Callback callback)
使用传入的Callback函数。

#### Handler(Looper looper)
使用传入的Looper。

#### Handler(Looper looper, Handler.Callback callback)
使用传入的Looper与Callback函数。

### 还有三个@hide的方法
+ Handler(boolean async)
+ Handler(Callback callback, boolean async)
+ Handler(Looper looper, Callback callback, boolean async)

``` java?linenums
public class Handler {
    /*
     * Set this flag to true to detect anonymous, local or member classes
     * that extend this Handler class and that are not static. These kind
     * of classes can potentially create leaks.
     */
    private static final boolean FIND_POTENTIAL_LEAKS = false;
    // 这个变量就是用来检测非static Handler导致内存泄漏问题的。
    
    final MessageQueue mQueue;
    final Looper mLooper;
    final Callback mCallback;
    final boolean mAsynchronous;
    ...
    /**
     * Use the {@link Looper} for the current thread
     * and set whether the handler should be asynchronous.
     *
     * Handlers are synchronous by default unless this constructor is used to make
     * one that is strictly asynchronous.
     *
     * Asynchronous messages represent interrupts or events that do not require global ordering
     * with represent to synchronous messages.  Asynchronous messages are not subject to
     * the synchronization barriers introduced by {@link MessageQueue#enqueueSyncBarrier(long)}.
     *
     * @param async If true, the handler calls {@link Message#setAsynchronous(boolean)} for
     * each {@link Message} that is sent to it or {@link Runnable} that is posted to it.
     *
     * @hide
     */
    public Handler(boolean async) {
        this(null, async);
    }

    /**
     * Use the {@link Looper} for the current thread with the specified callback interface
     * and set whether the handler should be asynchronous.
     *
     * Handlers are synchronous by default unless this constructor is used to make
     * one that is strictly asynchronous.
     *
     * Asynchronous messages represent interrupts or events that do not require global ordering
     * with represent to synchronous messages.  Asynchronous messages are not subject to
     * the synchronization barriers introduced by {@link MessageQueue#enqueueSyncBarrier(long)}.
     *
     * @param callback The callback interface in which to handle messages, or null.
     * @param async If true, the handler calls {@link Message#setAsynchronous(boolean)} for
     * each {@link Message} that is sent to it or {@link Runnable} that is posted to it.
     *
     * @hide
     */
    public Handler(Callback callback, boolean async) {
        if (FIND_POTENTIAL_LEAKS) {
            final Class<? extends Handler> klass = getClass();
            if ((klass.isAnonymousClass() || klass.isMemberClass() || klass.isLocalClass()) &&
                    (klass.getModifiers() & Modifier.STATIC) == 0) {
                Log.w(TAG, "The following Handler class should be static or leaks might occur: " +
                    klass.getCanonicalName());
            }
        }

        mLooper = Looper.myLooper();
        if (mLooper == null) {
            throw new RuntimeException(
                "Can't create handler inside thread that has not called Looper.prepare()");
        }
        mQueue = mLooper.mQueue;
        mCallback = callback;
        mAsynchronous = async;
    }

    /**
     * Use the provided {@link Looper} instead of the default one and take a callback
     * interface in which to handle messages.  Also set whether the handler
     * should be asynchronous.
     *
     * Handlers are synchronous by default unless this constructor is used to make
     * one that is strictly asynchronous.
     *
     * Asynchronous messages represent interrupts or events that do not require global ordering
     * with represent to synchronous messages.  Asynchronous messages are not subject to
     * the synchronization barriers introduced by {@link MessageQueue#enqueueSyncBarrier(long)}.
     *
     * @param looper The looper, must not be null.
     * @param callback The callback interface in which to handle messages, or null.
     * @param async If true, the handler calls {@link Message#setAsynchronous(boolean)} for
     * each {@link Message} that is sent to it or {@link Runnable} that is posted to it.
     *
     * @hide
     */
    public Handler(Looper looper, Callback callback, boolean async) {
        mLooper = looper;
        mQueue = looper.mQueue;
        mCallback = callback;
        mAsynchronous = async;
    }
    ...
}
```
#### Handler(Callback callback, boolean async)
我们关注一下这个构造器的代码，看看它都做了什么。
1.if语句判断是否打开检测Handler可疑内存泄漏的检测开关。
2.判断这个Handler是否是匿名类或内部类或是本地定义的类，并且是否是static的。如果不是打印警告信息。
3.获取当前线程的Looper。
4.如果该线程没有Looper则丢出异常。
5.获取到Looper的MessageQueue。
6.设置Callback和是否同步。

## sendMessage()
先从sendMessage()开始看起，这是我们使用Handler中最多的方法。
``` java?linenums
public class Handler {
    ...
    /**
     * Pushes a message onto the end of the message queue after all pending messages
     * before the current time. It will be received in {@link #handleMessage},
     * in the thread attached to this handler.
     *  
     * @return Returns true if the message was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.
     */
    public final boolean sendMessage(Message msg)
    {
        return sendMessageDelayed(msg, 0);
    }

    /**
     * Sends a Message containing only the what value.
     *  
     * @return Returns true if the message was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.
     */
    public final boolean sendEmptyMessage(int what)
    {
        return sendEmptyMessageDelayed(what, 0);
    }

    /**
     * Sends a Message containing only the what value, to be delivered
     * after the specified amount of time elapses.
     * @see #sendMessageDelayed(android.os.Message, long) 
     * 
     * @return Returns true if the message was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.
     */
    public final boolean sendEmptyMessageDelayed(int what, long delayMillis) {
        Message msg = Message.obtain();
        msg.what = what;
        return sendMessageDelayed(msg, delayMillis);
    }

    /**
     * Sends a Message containing only the what value, to be delivered 
     * at a specific time.
     * @see #sendMessageAtTime(android.os.Message, long)
     *  
     * @return Returns true if the message was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.
     */

    public final boolean sendEmptyMessageAtTime(int what, long uptimeMillis) {
        Message msg = Message.obtain();
        msg.what = what;
        return sendMessageAtTime(msg, uptimeMillis);
    }

    /**
     * Enqueue a message into the message queue after all pending messages
     * before (current time + delayMillis). You will receive it in
     * {@link #handleMessage}, in the thread attached to this handler.
     *  
     * @return Returns true if the message was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.  Note that a
     *         result of true does not mean the message will be processed -- if
     *         the looper is quit before the delivery time of the message
     *         occurs then the message will be dropped.
     */
    public final boolean sendMessageDelayed(Message msg, long delayMillis)
    {
        if (delayMillis < 0) {
            delayMillis = 0;
        }
        return sendMessageAtTime(msg, SystemClock.uptimeMillis() + delayMillis);
    }

    /**
     * Enqueue a message into the message queue after all pending messages
     * before the absolute time (in milliseconds) <var>uptimeMillis</var>.
     * <b>The time-base is {@link android.os.SystemClock#uptimeMillis}.</b>
     * You will receive it in {@link #handleMessage}, in the thread attached
     * to this handler.
     * 
     * @param uptimeMillis The absolute time at which the message should be
     *         delivered, using the
     *         {@link android.os.SystemClock#uptimeMillis} time-base.
     *         
     * @return Returns true if the message was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.  Note that a
     *         result of true does not mean the message will be processed -- if
     *         the looper is quit before the delivery time of the message
     *         occurs then the message will be dropped.
     */
    public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
        MessageQueue queue = mQueue;
        if (queue == null) {
            RuntimeException e = new RuntimeException(
                    this + " sendMessageAtTime() called with no mQueue");
            Log.w("Looper", e.getMessage(), e);
            return false;
        }
        return enqueueMessage(queue, msg, uptimeMillis);
    }

    /**
     * Enqueue a message at the front of the message queue, to be processed on
     * the next iteration of the message loop.  You will receive it in
     * {@link #handleMessage}, in the thread attached to this handler.
     * <b>This method is only for use in very special circumstances -- it
     * can easily starve the message queue, cause ordering problems, or have
     * other unexpected side-effects.</b>
     *  
     * @return Returns true if the message was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.
     */
    public final boolean sendMessageAtFrontOfQueue(Message msg) {
        MessageQueue queue = mQueue;
        if (queue == null) {
            RuntimeException e = new RuntimeException(
                this + " sendMessageAtTime() called with no mQueue");
            Log.w("Looper", e.getMessage(), e);
            return false;
        }
        return enqueueMessage(queue, msg, 0);
    }
    ...
}
```
+ public final boolean sendMessage(Message msg)
+ public final boolean sendEmptyMessage(int what)
+ public final boolean sendEmptyMessageDelayed(int what, long delayMillis)
+ public final boolean sendEmptyMessageAtTime(int what, long uptimeMillis)
+ public final boolean sendMessageDelayed(Message msg, long delayMillis)
+ public boolean sendMessageAtTime(Message msg, long uptimeMillis)
+ public final boolean sendMessageAtFrontOfQueue(Message msg)

### sendEmptyMessageDelayed()
1.通过Message.obtain()方法从Message pool获取到一个Message。
2.赋值what，调用sendMessageDelayed方法。
``` java?linenums
public final class Message implements Parcelable {
    ...
    private static final Object sPoolSync = new Object();
    private static Message sPool;
    private static int sPoolSize = 0;
    /**
     * Return a new Message instance from the global pool. Allows us to
     * avoid allocating new objects in many cases.
     */
    public static Message obtain() {
        synchronized (sPoolSync) {
            if (sPool != null) {
                Message m = sPool;
                sPool = m.next;
                m.next = null;
                sPoolSize--;
                return m;
            }
        }
        return new Message();
    }
    ...
}
```

[See Message Source](http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.2.2_r1/android/os/Message.java?av=f#106)

这里值得一提的是，Message我们可以不使用new来创建对象。使用Message.obtain()方法Message提供一种回收机制，结束使命的Message会回到Message pool里面等待下次使用，如果Message pool为空会返回一个new Message对象。这样就会减少new和回收时的资源消耗。

### sendEmptyMessageAtTime()
同sendEmptyMessageDelayed()方法，调用sendMessageAtTime();

### sendMessageDelayed()
实际也是调用sendMessageAtTime()，只是把时间包装了一下，使得使用方便。

### sendMessageAtTime()与sendMessageAtFrontOfQueue()
这两个方法基本一致，通过方法名字也可以分别出来其中的作用。
判断一下mQueue这个成员是否为空。跟踪一下这个成员是在Looper的构造器里初始化的。
``` java?linenums
public class Looper {
    ...
    final MessageQueue mQueue;
    final Thread mThread;
    volatile boolean mRun;
    private Looper(boolean quitAllowed) {
        mQueue = new MessageQueue(quitAllowed);
        mRun = true;
        mThread = Thread.currentThread();
    }
    ...
}
```

[See Looper Source](http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.2.2_r1/android/os/Looper.java?av=f#188)

MessageQueue为空就丢异常出来了。
后面继续执行enqueueMessage()方法。

### enqueueMessage()

``` java?linenums
public class Handler {
    ...
    final boolean mAsynchronous;
    private boolean enqueueMessage(MessageQueue queue, Message msg, long uptimeMillis) {
        msg.target = this;
        if (mAsynchronous) {
            msg.setAsynchronous(true);
        }
        return queue.enqueueMessage(msg, uptimeMillis);
    }
    ...
}
```
1.给Message赋值当前的Handler对象到Message的target成员。
2.判断是否需要设置Message同步。
``` java?linenums
public class Message {
    ...
    /** If set message is asynchronous */
    /*package*/ static final int FLAG_ASYNCHRONOUS = 1 << 1; // Resolved value: 2
    /*package*/ int flags;
    /**
     * Sets whether the message is asynchronous.
     *
     * Asynchronous messages represent interrupts or events that do not require global ordering
     * with represent to synchronous messages.  Asynchronous messages are not subject to
     * the synchronization barriers introduced by {@link MessageQueue#enqueueSyncBarrier(long)}.
     *
     * @param async True if the message is asynchronous.
     *
     * @see #isAsynchronous()
     * @see MessageQueue#enqueueSyncBarrier(long)
     * @see MessageQueue#removeSyncBarrier(int)
     *
     * @hide
     */
    public void setAsynchronous(boolean async) {
        if (async) {
            flags |= FLAG_ASYNCHRONOUS;
        } else {
            flags &= ~FLAG_ASYNCHRONOUS;
        }
    }
    ...
}
```

[See Message Source](http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.2.2_r1/android/os/Message.java#Message.setAsynchronous%28boolean%29)

3.执行MessageQueue.enqueueMessage()方法。
``` java?linenums
public class MessageQueue {
    ...
    @SuppressWarnings("unused")
    private int mPtr; // used by native code
    Message mMessages;
    private boolean mQuiting;
    // Indicates whether next() is blocked waiting in pollOnce() with a non-zero timeout.
    private boolean mBlocked;
    final boolean enqueueMessage(Message msg, long when) {
        if (msg.isInUse()) {
            throw new AndroidRuntimeException(msg + " This message is already in use.");
        }
        if (msg.target == null) {
            throw new AndroidRuntimeException("Message must have a target.");
        }

        boolean needWake;
        synchronized (this) {
            if (mQuiting) {
                RuntimeException e = new RuntimeException(
                        msg.target + " sending message to a Handler on a dead thread");
                Log.w("MessageQueue", e.getMessage(), e);
                return false;
            }

            msg.when = when;
            Message p = mMessages;
            if (p == null || when == 0 || when < p.when) {
                // New head, wake up the event queue if blocked.
                msg.next = p;
                mMessages = msg;
                needWake = mBlocked;
            } else {
                // Inserted within the middle of the queue.  Usually we don't have to wake
                // up the event queue unless there is a barrier at the head of the queue
                // and the message is the earliest asynchronous message in the queue.
                needWake = mBlocked && p.target == null && msg.isAsynchronous();
                Message prev;
                for (;;) {
                    prev = p;
                    p = p.next;
                    if (p == null || when < p.when) {
                        break;
                    }
                    if (needWake && p.isAsynchronous()) {
                        needWake = false;
                    }
                }
                msg.next = p; // invariant: p == prev.next
                prev.next = msg;
            }
        }
        if (needWake) {
            nativeWake(mPtr);
        }
        return true;
    }
    ...
}
```

[See MessageQueue Source](http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.2.2_r1/android/os/MessageQueue.java#MessageQueue.enqueueMessage%28android.os.Message%2Clong%29)

1.判断Message有没有正在使用，Message是否含有一个Handler。
2.如果队列没有退出则下一步
3.将Message加入队列，如果没有队列则创建。
4.唤醒nativePollOnce的等待。

## dispatchMessage()
``` java?linenums
public class Handler {
    ...
    /**
     * Handle system messages here.
     */
    public void dispatchMessage(Message msg) {
        if (msg.callback != null) {
            handleCallback(msg);
        } else {
            if (mCallback != null) {
                if (mCallback.handleMessage(msg)) {
                    return;
                }
            }
            handleMessage(msg);
        }
    }
    /**
     * Subclasses must implement this to receive messages.
     */
    public void handleMessage(Message msg) {
    }
    private static void handleCallback(Message message) {
        message.callback.run();
    }
    ...
}
```
[Usages of dispatchMessage()](http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.2.2_r1/android/os/Looper.java#137)
这里可以看到，sendMessage()方法加入到MessageQueue中的消息，在Looper的loop方法中得到了处理，而且系统默认把handleMessage()方法留空就是为了给开发者重写来处理异步的事件。


## post()
``` java?linenums
public class Handler {
    ...
    /**
     * Causes the Runnable r to be added to the message queue.
     * The runnable will be run on the thread to which this handler is 
     * attached. 
     *  
     * @param r The Runnable that will be executed.
     * 
     * @return Returns true if the Runnable was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.
     */
    public final boolean post(Runnable r)
    {
       return  sendMessageDelayed(getPostMessage(r), 0);
    }
    
    /**
     * Causes the Runnable r to be added to the message queue, to be run
     * at a specific time given by <var>uptimeMillis</var>.
     * <b>The time-base is {@link android.os.SystemClock#uptimeMillis}.</b>
     * The runnable will be run on the thread to which this handler is attached.
     *
     * @param r The Runnable that will be executed.
     * @param uptimeMillis The absolute time at which the callback should run,
     *         using the {@link android.os.SystemClock#uptimeMillis} time-base.
     *  
     * @return Returns true if the Runnable was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.  Note that a
     *         result of true does not mean the Runnable will be processed -- if
     *         the looper is quit before the delivery time of the message
     *         occurs then the message will be dropped.
     */
    public final boolean postAtTime(Runnable r, long uptimeMillis)
    {
        return sendMessageAtTime(getPostMessage(r), uptimeMillis);
    }
    
    /**
     * Causes the Runnable r to be added to the message queue, to be run
     * at a specific time given by <var>uptimeMillis</var>.
     * <b>The time-base is {@link android.os.SystemClock#uptimeMillis}.</b>
     * The runnable will be run on the thread to which this handler is attached.
     *
     * @param r The Runnable that will be executed.
     * @param uptimeMillis The absolute time at which the callback should run,
     *         using the {@link android.os.SystemClock#uptimeMillis} time-base.
     * 
     * @return Returns true if the Runnable was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.  Note that a
     *         result of true does not mean the Runnable will be processed -- if
     *         the looper is quit before the delivery time of the message
     *         occurs then the message will be dropped.
     *         
     * @see android.os.SystemClock#uptimeMillis
     */
    public final boolean postAtTime(Runnable r, Object token, long uptimeMillis)
    {
        return sendMessageAtTime(getPostMessage(r, token), uptimeMillis);
    }
    
    /**
     * Causes the Runnable r to be added to the message queue, to be run
     * after the specified amount of time elapses.
     * The runnable will be run on the thread to which this handler
     * is attached.
     *  
     * @param r The Runnable that will be executed.
     * @param delayMillis The delay (in milliseconds) until the Runnable
     *        will be executed.
     *        
     * @return Returns true if the Runnable was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.  Note that a
     *         result of true does not mean the Runnable will be processed --
     *         if the looper is quit before the delivery time of the message
     *         occurs then the message will be dropped.
     */
    public final boolean postDelayed(Runnable r, long delayMillis)
    {
        return sendMessageDelayed(getPostMessage(r), delayMillis);
    }
    
    /**
     * Posts a message to an object that implements Runnable.
     * Causes the Runnable r to executed on the next iteration through the
     * message queue. The runnable will be run on the thread to which this
     * handler is attached.
     * <b>This method is only for use in very special circumstances -- it
     * can easily starve the message queue, cause ordering problems, or have
     * other unexpected side-effects.</b>
     *  
     * @param r The Runnable that will be executed.
     * 
     * @return Returns true if the message was successfully placed in to the 
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.
     */
    public final boolean postAtFrontOfQueue(Runnable r)
    {
        return sendMessageAtFrontOfQueue(getPostMessage(r));
    }

    private static Message getPostMessage(Runnable r) {
        Message m = Message.obtain();
        m.callback = r;
        return m;
    }

    private static Message getPostMessage(Runnable r, Object token) {
        Message m = Message.obtain();
        m.obj = token;
        m.callback = r;
        return m;
    }
    ...
}
```
其实可以看出来就是把Runable对象包装成Message的Callback成员，成为Message继续调用sendMessage方法。

## obtainMessage()
``` java?linenums
public class Handler {
    ...
        /**
     * Returns a new {@link android.os.Message Message} from the global message pool. More efficient than
     * creating and allocating new instances. The retrieved message has its handler set to this instance (Message.target == this).
     *  If you don't want that facility, just call Message.obtain() instead.
     */
    public final Message obtainMessage()
    {
        return Message.obtain(this);
    }

    /**
     * Same as {@link #obtainMessage()}, except that it also sets the what member of the returned Message.
     * 
     * @param what Value to assign to the returned Message.what field.
     * @return A Message from the global message pool.
     */
    public final Message obtainMessage(int what)
    {
        return Message.obtain(this, what);
    }
    
    /**
     * 
     * Same as {@link #obtainMessage()}, except that it also sets the what and obj members 
     * of the returned Message.
     * 
     * @param what Value to assign to the returned Message.what field.
     * @param obj Value to assign to the returned Message.obj field.
     * @return A Message from the global message pool.
     */
    public final Message obtainMessage(int what, Object obj)
    {
        return Message.obtain(this, what, obj);
    }

    /**
     * 
     * Same as {@link #obtainMessage()}, except that it also sets the what, arg1 and arg2 members of the returned
     * Message.
     * @param what Value to assign to the returned Message.what field.
     * @param arg1 Value to assign to the returned Message.arg1 field.
     * @param arg2 Value to assign to the returned Message.arg2 field.
     * @return A Message from the global message pool.
     */
    public final Message obtainMessage(int what, int arg1, int arg2)
    {
        return Message.obtain(this, what, arg1, arg2);
    }
    
    /**
     * 
     * Same as {@link #obtainMessage()}, except that it also sets the what, obj, arg1,and arg2 values on the 
     * returned Message.
     * @param what Value to assign to the returned Message.what field.
     * @param arg1 Value to assign to the returned Message.arg1 field.
     * @param arg2 Value to assign to the returned Message.arg2 field.
     * @param obj Value to assign to the returned Message.obj field.
     * @return A Message from the global message pool.
     */
    public final Message obtainMessage(int what, int arg1, int arg2, Object obj)
    {
        return Message.obtain(this, what, arg1, arg2, obj);
    }
    ...
}
```
实际上是调用Message.obtain()方法。
## hasxxx()与removexxx()
``` java?linenums
public class Handler {
    ...
    /**
     * Check if there are any pending posts of messages with code 'what' in
     * the message queue.
     */
    public final boolean hasMessages(int what) {
        return mQueue.hasMessages(this, what, null);
    }
    /**
     * Check if there are any pending posts of messages with code 'what' and
     * whose obj is 'object' in the message queue.
     */
    public final boolean hasMessages(int what, Object object) {
        return mQueue.hasMessages(this, what, object);
    }
    /**
     * Check if there are any pending posts of messages with callback r in
     * the message queue.
     * 
     * @hide
     */
    public final boolean hasCallbacks(Runnable r) {
        return mQueue.hasMessages(this, r, null);
    }
    /**
     * Remove any pending posts of Runnable r that are in the message queue.
     */
    public final void removeCallbacks(Runnable r)
    {
        mQueue.removeMessages(this, r, null);
    }
    /**
     * Remove any pending posts of Runnable <var>r</var> with Object
     * <var>token</var> that are in the message queue.  If <var>token</var> is null,
     * all callbacks will be removed.
     */
    public final void removeCallbacks(Runnable r, Object token)
    {
        mQueue.removeMessages(this, r, token);
    }
    /**
     * Remove any pending posts of messages with code 'what' that are in the
     * message queue.
     */
    public final void removeMessages(int what) {
        mQueue.removeMessages(this, what, null);
    }
    /**
     * Remove any pending posts of messages with code 'what' and whose obj is
     * 'object' that are in the message queue.  If <var>object</var> is null,
     * all messages will be removed.
     */
    public final void removeMessages(int what, Object object) {
        mQueue.removeMessages(this, what, object);
    }
    /**
     * Remove any pending posts of callbacks and sent messages whose
     * <var>obj</var> is <var>token</var>.  If <var>token</var> is null,
     * all callbacks and messages will be removed.
     */
    public final void removeCallbacksAndMessages(Object token) {
        mQueue.removeCallbacksAndMessages(this, token);
    }
    ...
}
```
除了hasCallbacks(Runnable r)方法以外，其他都是公开接口。通过方法名字也能看出其中的作用，这里就不再阐述。

## dump()
``` java?linenums
public class Handler {
    ...
    public final void dump(Printer pw, String prefix) {
        pw.println(prefix + this + " @ " + SystemClock.uptimeMillis());
        if (mLooper == null) {
            pw.println(prefix + "looper uninitialized");
        } else {
            mLooper.dump(pw, prefix + "  ");
        }
    }
    ...
}
```
dump()方法实际上调用了Looper.dump()方法。
``` java?linenums
public class Looper {
    ...
    final MessageQueue mQueue;
    final Thread mThread;
    volatile boolean mRun;
    public void dump(Printer pw, String prefix) {
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

[See Looper Source](http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.2.2_r1/android/os/Looper.java#257)。

## BlockingRunnable.class与runWithScissors()
``` java?linenums
public class Handler {
    ...
    final Looper mLooper;
    /**
     * Runs the specified task synchronously.
     *
     * If the current thread is the same as the handler thread, then the runnable
     * runs immediately without being enqueued.  Otherwise, posts the runnable
     * to the handler and waits for it to complete before returning.
     *
     * This method is dangerous!  Improper use can result in deadlocks.
     * Never call this method while any locks are held or use it in a
     * possibly re-entrant manner.
     *
     * This method is occasionally useful in situations where a background thread
     * must synchronously await completion of a task that must run on the
     * handler's thread.  However, this problem is often a symptom of bad design.
     * Consider improving the design (if possible) before resorting to this method.
     *
     * One example of where you might want to use this method is when you just
     * set up a Handler thread and need to perform some initialization steps on
     * it before continuing execution.
     *
     * If timeout occurs then this method returns <code>false</code> but the runnable
     * will remain posted on the handler and may already be in progress or
     * complete at a later time.
     *
     * @param r The Runnable that will be executed synchronously.
     * @param timeout The timeout in milliseconds, or 0 to wait indefinitely.
     *
     * @return Returns true if the Runnable was successfully executed.
     *         Returns false on failure, usually because the
     *         looper processing the message queue is exiting.
     *
     * @hide This method is prone to abuse and should probably not be in the API.
     * If we ever do make it part of the API, we might want to rename it to something
     * less funny like runUnsafe().
     */
    public final boolean runWithScissors(final Runnable r, long timeout) {
        if (r == null) {
            throw new IllegalArgumentException("runnable must not be null");
        }
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must be non-negative");
        }

        if (Looper.myLooper() == mLooper) {
            r.run();
            return true;
        }

        BlockingRunnable br = new BlockingRunnable(r);
        return br.postAndWait(this, timeout);
    }

    private static final class BlockingRunnable implements Runnable {
        private final Runnable mTask;
        private boolean mDone;

        public BlockingRunnable(Runnable task) {
            mTask = task;
        }

        @Override
        public void run() {
            try {
                mTask.run();
            } finally {
                synchronized (this) {
                    mDone = true;
                    notifyAll();
                }
            }
        }

        public boolean postAndWait(Handler handler, long timeout) {
            if (!handler.post(this)) {
                return false;
            }

            synchronized (this) {
                if (timeout > 0) {
                    final long expirationTime = SystemClock.uptimeMillis() + timeout;
                    while (!mDone) {
                        long delay = expirationTime - SystemClock.uptimeMillis();
                        if (delay <= 0) {
                            return false; // timeout
                        }
                        try {
                            wait(delay);
                        } catch (InterruptedException ex) {
                        }
                    }
                } else {
                    while (!mDone) {
                        try {
                            wait();
                        } catch (InterruptedException ex) {
                        }
                    }
                }
            }
            return true;
        }
    }
    ...
}
```
这个方法对是不对外公开的，注释里面写到这个方法是非常危险的，可能会引起死锁问题，如果有其他的锁请不要使用这个方法。这个方法适用于一个后台线程必须同步等待一个Handler事件完成，如果一定要使用这个方法，说明代码设计存在缺陷，前先看看能否重新架构（在考虑使用这个方法）。
1.这个方法先判断当前的线程是否为Handler线程，是否含有Looper，如果有则执行Callback返回。
2.如果不是则创建一个BlockingRunnable对象，调用postAndWait将这个Message加入到MessageQueue里，等待上面的run方法被调用以后，唤醒这个wait。

## getIMessenger()
``` java?linenums
public class Handler {
    ...
    final IMessenger getIMessenger() {
        synchronized (mQueue) {
            if (mMessenger != null) {
                return mMessenger;
            }
            mMessenger = new MessengerImpl();
            return mMessenger;
        }
    }

    private final class MessengerImpl extends IMessenger.Stub {
        public void send(Message msg) {
            Handler.this.sendMessage(msg);
        }
    }
    ...
}
```
这个远程调用接口就不再赘述了，大家有兴趣自己研究一下吧。
