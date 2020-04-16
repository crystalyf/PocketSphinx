package com.zxc.pocketsphinx;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.zxc.pocketsphinx.util.AudioRecord.AudioRecordButton;
import com.zxc.pocketsphinx.util.PocketSphinxUtil;

import edu.cmu.pocketsphinx.demo.RecognitionListener;

public class MainActivity extends Activity implements RecognitionListener {

    private AudioRecordButton imgbtn_say;
    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        PocketSphinxUtil.get(this).setListener(this);
        context = MainActivity.this;
        imgbtn_say = (AudioRecordButton) findViewById(R.id.imgbtn_say);
        imgbtn_say.setAudioRecordFinishListener(new MyAudioRecordFinishListener());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onPartialResults(String b) {
        //一次识别之后，停止语音引擎，下次长按再开启
        PocketSphinxUtil.get(context).stop();
        if (b.contains("下一个界面") || b.contains("下一个页面")) {
            Intent intent = new Intent(context, NextActivity.class);
            startActivity(intent);
        } else if (b.contains("设置界面") || b.contains("设置页面")) {
            Intent intent = new Intent(context, SettingActivity.class);
            startActivity(intent);
        }
    }

    @Override
    public void onResults(String b) {
    }

    @Override
    public void onError(int err) {
    }


    class MyAudioRecordFinishListener implements AudioRecordButton.AudioRecordFinishListener {
        @Override
        public void onFinish() {
            // TODO Auto-generated method stub
        }
    }


}
