# Android代码书写与优化
## 正确的使用语句块
### 什么是语句块
有着相同的变量作用域的相关一组语句的集合，看起来就是应该用{}括起来的，比如控制结构中的逻辑。我认为最关键的一点就是变量作用域，也就是说，如果能用同一个局部变量，那么就是程序意义上的语句块。来看个例子：

``` java?linenums
public void onClick(View v) {
    int id = v.getId();
    switch (id) {
        case R.id.btn1:
            Intent i = new Intent();
            i.setClass(this, Test1Activity.class);
            startActivity(i);
            break;
        case R.id.btn2:
            Intent i = new Intent(); // Variable i is already defined in the scope
            i.setClass(this, Test1Activity.class);
            startActivity(i);
            break;
        default:
            break;
    }
}
```

语句块的应用

``` java?linenums
public void onClick(View v) {
    int id = v.getId();
    switch (id) {
        case R.id.btn1: {
            Intent i = new Intent();
            i.setClass(this, Test1Activity.class);
            startActivity(i);
            break;
            }
        case R.id.btn2: {
            Intent i = new Intent(); // Variable i is already defined in the scope
            i.setClass(this, Test1Activity.class);
            startActivity(i);
            break;
            }
        default:
            break;
    }
}
```

## 关于单例模式的使用

![4Example](https://raw.githubusercontent.com/hycmanson/MyDocument/master/CommonImages/4example.png)

``` java?linenums
class DBHelper extends SQLiteOpenHelper {
    private static DBHelper mDbHelper;

    public static DBHelper getDBHelperInstance(Context context) {
        if (mDbHelper == null) {
            mDbHelper = new DBHelper(context);
        }
        Util.LogI(DBHelper.class, "getDBHelperInstance() start");
        return mDbHelper;
    }

    private DBHelper(Context context) {
        super(context, context.getDatabasePath(CommonVariable.DB_WEATHER).getAbsolutePath(), null, 2);
        Util.LogI(DBHelper.class, "DBHelper() start");
    }

    public String getCityCodeByName(String cityName) {
        Util.LogI(DBHelper.class, "getCityCodeByName() start");
        String cityCode = null;
        SQLiteDatabase db = null;
        Cursor cursor = null;
        ...
    }
}
```

少了同步锁多线程就有了不同步的隐患，想办法改进一下。

``` java?linenums
class DBHelper extends SQLiteOpenHelper {
    private static DBHelper mDbHelper;

    public static synchronized DBHelper getDBHelperInstance(Context context) {
        if (mDbHelper == null) {
            mDbHelper = new DBHelper(context);
        }
        Util.LogI(DBHelper.class, "getDBHelperInstance() start");
        return mDbHelper;
    }

    private DBHelper(Context context) {
        super(context, context.getDatabasePath(CommonVariable.DB_WEATHER).getAbsolutePath(), null, 2);
        Util.LogI(DBHelper.class, "DBHelper() start");
    }

    public synchronized String getCityCodeByName(String cityName) {
        Util.LogI(DBHelper.class, "getCityCodeByName() start");
        String cityCode = null;
        SQLiteDatabase db = null;
        Cursor cursor = null;
        ...
    }
}
```

### 1、饿汉模式创建单例

``` java?linenums
class HungrySingleton {
    private static HungrySingleton instance = new HungrySingleton();

    private HungrySingleton() {}

    public static HungrySingleton getInstance() {
        return instance;
    }
}
```

### 2、懒汉模式

``` java?linenums
class LazySingleton {
    private static LazySingleton instance;

    private LazySingleton() {}

    public static synchronized LazySingleton getInstance() {
        if (instance == null) {
            instance = new LazySingleton();
        }
        return instance;
    }
}
```

### 3、使用双重检查锁的懒汉模式

``` java?linenums
class DoubleCheckSingleton {
    private volatile static DoubleCheckSingleton instance;

    private DoubleCheckSingleton() {}

    public static DoubleCheckSingleton getInstance() {
        if (instance == null) {
            synchronized (DoubleCheckSingleton.class) {
                if (instance == null) {
                    instance = new DoubleCheckSingleton();
                }
            }
        }
        return instance;
    }
}
```

### 4、静态内部类

``` java?linenums
class StaticInnerClassSingleton {
    private StaticInnerClassSingleton() {}

    public static StaticInnerClassSingleton getInstance() {
        return SingletonHolder.instance;
    }

    private static class SingletonHolder {
        private static StaticInnerClassSingleton instance = new StaticInnerClassSingleton();
    }
}
```

### volatile与synchronized关键字
在Java中,为了保证多线程读写数据时保证数据的一致性,可以采用两种方式:

#### 同步
如用synchronized关键字,或者使用锁对象.

#### volatile
使用volatile关键字
用一句话概括volatile,它能够使变量在值发生改变时能尽快地让其他线程知道.

#### volatile详解
首先我们要先意识到有这样的现象,编译器为了加快程序运行的速度,对一些变量的写操作会先在寄存器或者是CPU缓存上进行,最后才写入内存.
而在这个过程,变量的新值对其他线程是不可见的.而volatile的作用就是使它修饰的变量的读写操作都必须在内存中进行!

#### volatile与synchronized
volatile本质是在告诉jvm当前变量在寄存器中的值是不确定的,需要从主存中读取,synchronized则是锁定当前变量,只有当前线程可以访问该变量,其他线程被阻塞住.
volatile仅能使用在变量级别,synchronized则可以使用在变量,方法.
volatile仅能实现变量的修改可见性,但不具备原子特性,而synchronized则可以保证变量的修改可见性和原子性.
volatile不会造成线程的阻塞,而synchronized可能会造成线程的阻塞.
volatile标记的变量不会被编译器优化,而synchronized标记的变量可以被编译器优化.

因此，在使用volatile关键字时要慎重，并不是只要简单类型变量使用volatile修饰，对这个变量的所有操作都是原来操作，当变量的值由自身的上一个决定时，如n=n+1、n\+\+等，volatile关键字将失效，只有当变量的值和自身上一个值无关时对该变量的操作才是原子级别的，如n = m + 1，这个就是原子级别的。所以在使用volatile关键时一定要谨慎，如果自己没有把握，可以使用synchronized来代替volatile。
总结：volatile本质是在告诉JVM当前变量在寄存器中的值是不确定的，需要从主存中读取。可以实现synchronized的部分效果，但当n=n+1,n++等时，volatile关键字将失效，不能起到像synchronized一样的线程同步的效果。

## 如何查看Android apk 堆导出文件中的Bitmap
[如何查看Android apk 堆导出文件中的Bitmap](http://www.atatech.org/articles/28164)

## 多用用obtain()方法

```java?linenums
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
                m.flags = 0; // clear in-use flag
                sPoolSize--;
                return m;
            }
        }
        return new Message();
    }
```

grep了一下frameworks的代码，列出以下几个比较常见的类。

``` bash?linenums
./android/view/MotionEvent.java:1387:    static private MotionEvent obtain() {}
./android/view/KeyEvent.java:1858:    private static KeyEvent obtain() {}
./android/os/Message.java:106:    public static Message obtain() {}
./android/os/Parcel.java:290:    public static Parcel obtain() {}
```