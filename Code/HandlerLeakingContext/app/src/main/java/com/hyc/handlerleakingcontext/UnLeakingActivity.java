package com.hyc.handlerleakingcontext;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.lang.ref.WeakReference;

/**
 * Created by yichao.hyc on 2015/12/14.
 */
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

        private UnLeakingHandler(UnLeakingActivity unLeakingActivity) {
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
