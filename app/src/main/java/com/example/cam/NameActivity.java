package com.example.cam;

import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

public class NameActivity extends Activity {
    public final static String EXTRA_MESSAGE = "com.example.cam.MESSAGE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_name);
    }

    public void nameClick(View view){
        Intent intent = new Intent(this,LearnActivity.class);
        EditText editText = (EditText) findViewById(R.id.nameText);
        String message = editText.getText().toString();
        intent.putExtra(EXTRA_MESSAGE, message);
        startActivity(intent);
    }
}
