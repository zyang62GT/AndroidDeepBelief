package com.example.cam;

import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.View;

public class StartActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
    }

    public void learnClick(View view){
        Intent intent = new Intent(this, NameActivity.class);
        startActivity(intent);
    }

    public void predictClick(View view){
        Intent intent = new Intent(this,PredictActivity.class);
        startActivity(intent);

    }


}
