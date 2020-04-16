package com.zxc.pocketsphinx.util.AudioRecord;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

//不保存MP3
public class AudioRecordMp3 {
    
	private static AudioRecordMp3 mInstance;

	private AudioRecordMp3() {
    }
	
	public static AudioRecordMp3 getInst()
	{
		if( mInstance == null )
			mInstance = new AudioRecordMp3();
		return mInstance;
	}
	
	public void start( OnAudioRecorderListener listener )
	{
		if( !isRecording )
		{
			//开说话线程，为了改变声音大小的效果
			thread = new AudioRecordMp3Thread();
			thread.setOnAudioRecorderListener(listener);
			thread.setTime( 0 );
			thread.start();
		}
		isRecording = true;
	}
	
	public void stop( int time )
	{
		AudioRecordMp3Thread.isKeepRunning = false;
		thread.setTime( time );
		isRecording = false;
	}

	private boolean isRecording = false;
	private AudioRecordMp3Thread thread = null;
	
	
	
}

//说话效果的线程，只为了改变声音大小的动画效果
class AudioRecordMp3Thread extends Thread {
	
	public void setTime( int time )
	{
		this.time = time;
	}
	
	private int time = 0;
	
	
	public void run()
    {
		
	  DataOutputStream output = null;
      try
      {
    	  	 init();
             audioRecord.startRecording() ;
             while(isKeepRunning)
             {            	 
        		 int readsize = audioRecord.read(minBuffer, 0, minBuffer.length);
        		 for (int i = 0; i < readsize; i++) {
                     output.writeShort(minBuffer[i]);
                 }
        		 if( listener != null )
        			 listener.onDecibelsChange( getDecibel ( minBuffer , readsize ) );
        		 
             }         
    	  	 
             if( listener != null )
	    	 {
	    		 // listener.onSuccess(  );
	    	 }
      }
      catch(Exception e)
      {
    	  e.printStackTrace();
      }
      
    }
	
	public double getDecibel( short[] minBuffer , int r )
    {
    	int v = 0;  
		for (int i = 0; i < minBuffer.length; i++) {  
            v += minBuffer[i] * minBuffer[i];  
        }  
		double mean = v / (double) r;  
		double volume = 10 * Math.log10(mean);
        return volume;
    }

    public void setOnAudioRecorderListener( OnAudioRecorderListener listener )
    {
    	this.listener = listener;
    	
    }
    
    public void init()
    {
    	 if(audioRecord == null)
    	 {
    		 
    		 minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
    	                AudioFormat.ENCODING_PCM_16BIT);
    		 minBuffer = new short[minBufferSize];
    		 audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
    	                AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
    	 }	    	
    	 isKeepRunning = true ;	 
    }
   
    public void free()
	{
    	isKeepRunning = false ;  
    	
	}

	protected AudioRecord audioRecord = null ;
    protected static int  minBufferSize ;
    protected static short []  minBuffer ;
    protected static boolean   isKeepRunning ;
    private OnAudioRecorderListener listener;
    public static final int SAMPLE_RATE = 8000;

    
}


