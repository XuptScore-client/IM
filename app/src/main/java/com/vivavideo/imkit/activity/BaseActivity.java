package com.vivavideo.imkit.activity;

import android.media.AudioManager;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;

//import io.rong.app.R;

public abstract class BaseActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);// 使得音量键控制媒体声音

    }

    protected void onResume() {
        super.onResume();
//        MobclickAgent.onResume(this);

    }

    protected void onPause() {
        super.onPause();
//        MobclickAgent.onPause(this);
    }



}