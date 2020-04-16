package com.zxc.pocketsphinx.util.AudioRecord;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.widget.AppCompatButton;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.zxc.pocketsphinx.util.PocketSphinxUtil;


public class AudioRecordButton extends AppCompatButton implements OnAudioRecorderListener {
	private static final int STATE_NORMAL = 1;
	private static final int STATE_RECORDING = 2;
	private static final int STATE_WANT_CANCEL = 3;
	private static final int DISTANCE_CANCEL_Y = 50;

	private int currentState = STATE_NORMAL;
	//是否正在说话
	private boolean isSpeaking = false;
	private AudioRecordDialog dialogManager;

	private int mTime;
	// 是否触发LongClick
	private boolean isReady = false;

	public AudioRecordButton(Context context) {
		this(context, null);
	}

	public AudioRecordButton(final Context context, AttributeSet attrs) {
		super(context, attrs);
		dialogManager = new AudioRecordDialog(getContext());

		setOnLongClickListener(new OnLongClickListener() {

			@Override
			public boolean onLongClick(View v) {
			    //开启语音识别引擎
                PocketSphinxUtil.get(context).start();
				isReady = true;
				dialogManager.showDialog();
				isSpeaking = true;
				return false;
			}
		});
	}

	@Override
	public void onSuccess() {
         audioRecordFinishListener.onFinish();
	}


	@Override
	public void onDecibelsChange(final double level) {
		Handler handler = new Handler(Looper.getMainLooper());
		handler.post(new Runnable() {
			public void run() {
				if (level >= 80) {
					dialogManager.updateVolumeLevel(5);
				} else if (level >= 70 && level < 80) {
					dialogManager.updateVolumeLevel(4);
				} else if (level >= 60 && level < 70) {
					dialogManager.updateVolumeLevel(3);
				} else if (level >= 50 && level < 60) {
					dialogManager.updateVolumeLevel(2);
				} else if (level < 50) {
					dialogManager.updateVolumeLevel(1);
				}

			}
		});
	}


	private static final int MSG_AUDIO_PREPARED = 0x110;
	private static final int MSG_VOLUME_CHAMGED = 0x111;
	private static final int MSG_DIALOG_DISMISS = 0x112;

	private Handler mHanlder = new Handler() {
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
				case MSG_AUDIO_PREPARED:
					dialogManager.showDialog();
					isSpeaking = true;

					break;
				case MSG_DIALOG_DISMISS:
					dialogManager.dismissDialog();

					break;

				default:
					break;
			}
		}
	};


	/**
	 * 录音完成后的回调
	 *
	 */
	public interface AudioRecordFinishListener {
		void onFinish();
	}

	private AudioRecordFinishListener audioRecordFinishListener;

	public void setAudioRecordFinishListener(AudioRecordFinishListener listener) {
		audioRecordFinishListener = listener;
	}


	@Override
	public boolean onTouchEvent(MotionEvent event) {

		int action = event.getAction();
		int x = (int) event.getX();
		int y = (int) event.getY();
		switch (action) {
			case MotionEvent.ACTION_DOWN:
				changeState(STATE_RECORDING);
				break;
			case MotionEvent.ACTION_MOVE:

				// 已经开始录音
				if (isSpeaking) {
					// 根据X，Y的坐标判断是否想要取消
					if (wantCancel(x, y)) {
						changeState(STATE_WANT_CANCEL);
						dialogManager.stateWantCancel();
					} else {
						changeState(STATE_RECORDING);
						dialogManager.stateRecording();
					}
				}

				break;

			case MotionEvent.ACTION_UP:
				// 没有触发longClick
				if (!isReady) {
					resetState();
					return super.onTouchEvent(event);
				}
				// prepare未完成就up,录音时间过短
				if (!isSpeaking) {
					dialogManager.stateLengthShort();
					mHanlder.sendEmptyMessageDelayed(MSG_DIALOG_DISMISS, 1300);
				} else if (currentState == STATE_RECORDING) { // 正常录制结束
					dialogManager.dismissDialog();

				} else if (currentState == STATE_WANT_CANCEL) {
					dialogManager.dismissDialog();
				}
				resetState();
				break;

			default:
				break;
		}
		return super.onTouchEvent(event);
	}

	/**
	 * 恢复标志位
	 */
	private void resetState() {

		isSpeaking = false;
		isReady = false;
		changeState(STATE_NORMAL);
		mTime = 0;
	}

	private boolean wantCancel(int x, int y) {
		if (x < 0 || x > getWidth()) {
			return true;
		}
		// 零点在左下角？
		if (y < -DISTANCE_CANCEL_Y || y > getHeight() + DISTANCE_CANCEL_Y) {
			return true;
		}
		return false;
	}

	private void changeState(int state) {

		if (currentState != state) {
			currentState = state;
			switch (state) {
				case STATE_NORMAL:

					break;
				case STATE_RECORDING:

					if (isSpeaking) {
						dialogManager.stateRecording();
					}
					break;
				case STATE_WANT_CANCEL:

					dialogManager.stateWantCancel();
					break;

				default:
					break;
			}
		}
	}

}
