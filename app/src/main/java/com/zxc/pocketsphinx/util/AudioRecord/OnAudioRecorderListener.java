package com.zxc.pocketsphinx.util.AudioRecord;

public interface OnAudioRecorderListener {
	
	//按键说话完成
	 void onSuccess();
	 void onDecibelsChange(double level);
}
