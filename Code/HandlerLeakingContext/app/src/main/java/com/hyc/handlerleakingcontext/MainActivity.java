package com.hyc.handlerleakingcontext;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class MainActivity extends Activity implements OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button leakingBtn = (Button) findViewById(R.id.leaking_test);
        Button unLeakingBtn = (Button) findViewById(R.id.unleaking_test);
        leakingBtn.setOnClickListener(this);
        unLeakingBtn.setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        Intent intent = null;
        switch (id) {
            case R.id.leaking_test:
                intent = new Intent(this, LeakingActivity.class);
                break;
            case R.id.unleaking_test:
                intent = new Intent(this, UnLeakingActivity.class);
                break;
        }
        if (intent != null) {
            startActivity(intent);
        }
    }
}
