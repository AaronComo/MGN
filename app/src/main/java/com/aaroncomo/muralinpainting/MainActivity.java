package com.aaroncomo.muralinpainting;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;

import com.example.muralinpainting.R;

public class MainActivity extends AppCompatActivity {
    private Intent intentPicture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        intentPicture = new Intent(this, Picture.class);  //创建picture页面
        buttonListener();
    }

    public void buttonListener() {
        findViewById(R.id.start).setOnClickListener(v -> startPicture());
    }

    public void startPicture() {
        startActivity(intentPicture);
    }   // 页面跳转

    public void doEnd() {
        finish();
    }
}