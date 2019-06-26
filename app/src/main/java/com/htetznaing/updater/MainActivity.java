package com.htetznaing.updater;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

import com.htetznaing.app_updater.AppUpdater;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AppUpdater appUpdater = new AppUpdater(this, "https://myappupdateserver.blogspot.com/2019/06/zfont.html");
        appUpdater.check();
    }
}
