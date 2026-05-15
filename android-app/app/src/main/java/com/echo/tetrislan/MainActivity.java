package com.echo.tetrislan;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(true);
        }
        setContentView(new TetrisView(this));
    }
}
