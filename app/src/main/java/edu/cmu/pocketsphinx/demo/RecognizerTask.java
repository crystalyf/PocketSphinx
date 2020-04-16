package edu.cmu.pocketsphinx.demo;

import android.content.Context;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;

import edu.cmu.pocketsphinx.Config;
import edu.cmu.pocketsphinx.Decoder;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.pocketsphinx;

/**
 * Speech recognition task, which runs in a worker thread.
 * <p>
 * This class implements speech recognition for this demo application. It takes
 * the form of a long-running task which accepts requests to start and stop
 * listening, and emits recognition results to a listener.
 *
 * @author David Huggins-Daines <dhuggins@cs.cmu.edu>
 */
public class RecognizerTask implements Runnable {
    /**
     * Audio recording task.
     * <p>
     * This class implements a task which pulls blocks of audio from the system
     * audio input and places them on a queue.
     *
     * @author David Huggins-Daines <dhuggins@cs.cmu.edu>
     */
    class AudioTask implements Runnable {
        /**
         * Queue on which audio blocks are placed.
         */
        LinkedBlockingQueue<short[]> q;
        AudioRecord rec;
        int block_size;
        boolean done;

        static final int DEFAULT_BLOCK_SIZE = 512;

        AudioTask() {
            this.init(new LinkedBlockingQueue<short[]>(), DEFAULT_BLOCK_SIZE);
        }

        AudioTask(LinkedBlockingQueue<short[]> q) {
            this.init(q, DEFAULT_BLOCK_SIZE);
        }

        AudioTask(LinkedBlockingQueue<short[]> q, int block_size) {
            this.init(q, block_size);
        }

        void init(LinkedBlockingQueue<short[]> q, int block_size) {
            this.done = false;
            this.q = q;
            this.block_size = block_size;
            this.rec = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, 8000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, 8192);
        }

        public int getBlockSize() {
            return block_size;
        }

        public void setBlockSize(int block_size) {
            this.block_size = block_size;
        }

        public LinkedBlockingQueue<short[]> getQueue() {
            return q;
        }

        public void stop() {
            this.done = true;
        }

        public void run() {
            this.rec.startRecording();
            while (!this.done) {
                int nshorts = this.readBlock();
                if (nshorts <= 0)
                    break;
            }
            this.rec.stop();
            this.rec.release();
        }

        int readBlock() {
            short[] buf = new short[this.block_size];
            int nshorts = this.rec.read(buf, 0, buf.length);
            if (nshorts > 0) {
                Log.d(getClass().getName(), "Posting " + nshorts
                        + " samples to queue");
                this.q.add(buf);
            }
            return nshorts;
        }
    }

    /**
     * PocketSphinx native decoder object.
     */
    Decoder ps;
    /**
     * Audio recording task.
     */
    AudioTask audio;
    /**
     * Thread associated with recording task.
     */
    Thread audio_thread;
    /**
     * Queue of audio buffers.
     */
    LinkedBlockingQueue<short[]> audioq;
    /**
     * Listener for recognition results.
     */
    RecognitionListener rl;
    /**
     * Whether to report partial results.
     */
    boolean use_partials;

    /**
     * State of the main loop.
     */
    enum State {
        IDLE, LISTENING
    }

    ;

    /**
     * Events for main loop.
     */
    enum Event {
        NONE, START, STOP, SHUTDOWN
    }

    ;

    /**
     * Current event.
     */
    Event mailbox;

    public RecognitionListener getRecognitionListener() {
        return rl;
    }

    public void setRecognitionListener(RecognitionListener rl) {
        this.rl = rl;
    }

    public void setUsePartials(boolean use_partials) {
        this.use_partials = use_partials;
    }

    public boolean getUsePartials() {
        return this.use_partials;
    }

    /**
     * copy assets to releaseDir with full path
     *
     * @param context
     * @param assetsDir
     * @param releaseDir
     * @author brian
     */
    public void releaseAssets(Context context, String assetsDir,
                              String releaseDir) {

        if (TextUtils.isEmpty(releaseDir)) {
            return;
        } else if (releaseDir.endsWith("/")) {
            releaseDir = releaseDir.substring(0, releaseDir.length() - 1);
        }

        if (TextUtils.isEmpty(assetsDir) || assetsDir.equals("/")) {
            assetsDir = "";
        } else if (assetsDir.endsWith("/")) {
            assetsDir = assetsDir.substring(0, assetsDir.length() - 1);
        }

        AssetManager assets = context.getAssets();
        try {
            String[] fileNames = assets.list(assetsDir);//只能获取到文件(夹)名,所以还得判断是文件夹还是文件
            if (fileNames.length > 0) {// is dir
                for (String name : fileNames) {
                    if (!TextUtils.isEmpty(assetsDir)) {
                        name = assetsDir + "/" + name;//补全assets资源路径
                    }
//                    Log.i("", "brian name=" + name);
                    String[] childNames = assets.list(name);//判断是文件还是文件夹
                    if (!TextUtils.isEmpty(name) && childNames.length > 0) {
                        releaseAssets(context, name, releaseDir);//递归, 因为资源都是带着全路径,
                        //所以不需要在递归是设置目标文件夹的路径
                    } else {
                        InputStream is = assets.open(name);

                        String outPath = releaseDir + "/" + name;
                        if (outPath.contains("voice")) {
                            writeFile(outPath, is);
                        }

                    }
                }
            } else {// is file
                InputStream is = assets.open(assetsDir);
                // 写入文件前, 需要提前级联创建好路径, 下面有代码贴出
                String outPath = releaseDir + "/" + assetsDir;
                if (outPath.contains("voice")) {
                    writeFile(outPath, is);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void writeFile(String outPath, InputStream is) throws IOException {
        FileOutputStream outputStream = new FileOutputStream(outPath);
        byte[] b = new byte[1024];
        int l = -1;
        while ((l = is.read(b)) != -1) {
            outputStream.write(b, 0, l);
        }
        outputStream.flush();
        outputStream.close();
        is.close();
    }

    private boolean isZh(Context context) {
        Locale locale = context.getResources().getConfiguration().locale;
        String language = locale.getLanguage();
        if (language.endsWith("zh"))
            return true;
        else
            return false;
    }

    private Handler handler;

    public RecognizerTask(Context context) {
        handler = new Handler(context.getMainLooper());
        String dataPath = context.getFilesDir().getAbsolutePath();
        File zhPath = new File(dataPath + "/voice/zh");
        if (!zhPath.exists()) {
            zhPath.mkdirs();
        }


        pocketsphinx
                .setLogfile(dataPath + "/voice/pocketsphinx.log");

        String rootPath = zhPath.getPath() ;
        String dicPath = rootPath + "/text.dic";
        String imPath = rootPath + "/text.lm";
        if (!new File(dicPath).exists()) {
            releaseAssets(context, "/", dataPath);
        }

        Config c = new Config();
        c.setString("-hmm", rootPath);
        c.setString("-dict", dicPath);
        c.setString("-lm", imPath);

        c.setFloat("-samprate", 8000.0);
        c.setInt("-maxhmmpf", 2000);
        c.setInt("-maxwpf", 10);
        c.setInt("-pl_window", 2);
        c.setBoolean("-backtrace", true);
        c.setBoolean("-bestpath", false);
        this.ps = new Decoder(c);
        this.audio = null;
        this.audioq = new LinkedBlockingQueue<short[]>();
        this.use_partials = false;
        this.mailbox = Event.NONE;
    }

    public void run() {
        /* Main loop for this thread. */
        boolean done = false;
        /* State of the main loop. */
        State state = State.IDLE;
		/* Previous partial hypothesis. */
        String partial_hyp = null;

        while (!done) {
			/* Read the mail. */
            Event todo = Event.NONE;
            synchronized (this.mailbox) {
                todo = this.mailbox;
				/* If we're idle then wait for something to happen. */
                if (state == State.IDLE && todo == Event.NONE) {
                    try {
                        Log.d(getClass().getName(), "waiting");
                        this.mailbox.wait();
                        todo = this.mailbox;
                        Log.d(getClass().getName(), "got" + todo);
                    } catch (InterruptedException e) {
						/* Quit main loop. */
                        Log.e(getClass().getName(),
                                "Interrupted waiting for mailbox, shutting down");
                        todo = Event.SHUTDOWN;
                    }
                }
				/* Reset the mailbox before releasing, to avoid race condition. */
                this.mailbox = Event.NONE;
            }
			/* Do whatever the mail says to do. */
            switch (todo) {
                case NONE:
                    if (state == State.IDLE)
                        Log.e(getClass().getName(),
                                "Received NONE in mailbox when IDLE, threading error?");
                    break;
                case START:
                    if (state == State.IDLE) {
                        Log.d(getClass().getName(), "START");
                        this.audio = new AudioTask(this.audioq, 1024);
                        this.audio_thread = new Thread(this.audio);
                        this.ps.startUtt();
                        this.audio_thread.start();
                        state = State.LISTENING;
                    } else
                        Log.e(getClass().getName(),
                                "Received START in mailbox when LISTENING");
                    break;
                case STOP:
                    if (state == State.IDLE)
                        Log.e(getClass().getName(),
                                "Received STOP in mailbox when IDLE");
                    else {
                        Log.d(getClass().getName(), "STOP");
                        assert this.audio != null;
                        this.audio.stop();
                        try {
                            this.audio_thread.join();
                        } catch (InterruptedException e) {
                            Log.e(getClass().getName(),
                                    "Interrupted waiting for audio thread, shutting down");
                            done = true;
                        }
					/* Drain the audio queue. */
                        short[] buf;
                        while ((buf = this.audioq.poll()) != null) {
                            Log.d(getClass().getName(), "Reading " + buf.length
                                    + " samples from queue");
                            this.ps.processRaw(buf, buf.length, false, false);
                        }
                        this.ps.endUtt();
                        this.audio = null;
                        this.audio_thread = null;

                        final Hypothesis hyp = this.ps.getHyp();
                        if (this.rl != null) {
                            if (hyp == null) {
                                Log.d(getClass().getName(), "Recognition failure");
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        rl.onError(-1);
                                    }
                                });

                            } else {

                                final String hypstr = hyp.getHypstr();
                                if (hypstr != null && !hypstr.equals(partial_hyp)) {
                                    if (this.rl != null) {
                                        handler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                String make = make(hyp.getHypstr());
                                                if (make != null) {
                                                    rl.onResults(make);
                                                }
                                            }
                                        });

                                    }
                                }
                                partial_hyp = hypstr;

                            }
                        }
                        state = State.IDLE;
                    }
                    break;
                case SHUTDOWN:
                    Log.d(getClass().getName(), "SHUTDOWN");
                    if (this.audio != null) {
                        this.audio.stop();
                        assert this.audio_thread != null;
                        try {
                            this.audio_thread.join();
                        } catch (InterruptedException e) {
						/* We don't care! */
                        }
                    }
                    this.ps.endUtt();
                    this.audio = null;
                    this.audio_thread = null;
                    state = State.IDLE;
                    done = true;
                    break;
            }
			/*
			 * Do whatever's appropriate for the current state. Actually this
			 * just means processing audio if possible.
			 */
            if (state == State.LISTENING) {
                assert this.audio != null;
                try {
                    short[] buf = this.audioq.take();
                    Log.d(getClass().getName(), "Reading " + buf.length
                            + " samples from queue");
                    this.ps.processRaw(buf, buf.length, false, false);
                    final Hypothesis hyp = this.ps.getHyp();
                    if (hyp != null) {
                        final String hypstr = hyp.getHypstr();
                        if (hypstr != null && !hypstr.equals(partial_hyp)) {
                            if (this.rl != null) {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        String make = make(hyp.getHypstr());
                                        if (make != null) {
                                            rl.onPartialResults(make);
                                        }
                                    }
                                });

                            }
                        }
                        partial_hyp = hypstr;
                    }
                } catch (InterruptedException e) {
                    Log.d(getClass().getName(), "Interrupted in audioq.take");
                }
            }
        }
    }

    private String make(String hypstr) {
        if (!TextUtils.isEmpty(hypstr)) {
            String[] split = hypstr.split(" ");
            return split[split.length - 1];
        }
        return null;
    }

    public void start() {
        Log.d(getClass().getName(), "signalling START");
        synchronized (this.mailbox) {
            this.mailbox.notifyAll();
            Log.d(getClass().getName(), "signalled START");
            this.mailbox = Event.START;
        }
    }

    public void stop() {
        Log.d(getClass().getName(), "signalling STOP");
        synchronized (this.mailbox) {
            this.mailbox.notifyAll();
            Log.d(getClass().getName(), "signalled STOP");
            this.mailbox = Event.STOP;
        }
    }

    public void shutdown() {
        Log.d(getClass().getName(), "signalling SHUTDOWN");
        synchronized (this.mailbox) {
            this.mailbox.notifyAll();
            Log.d(getClass().getName(), "signalled SHUTDOWN");
            this.mailbox = Event.SHUTDOWN;
        }
    }
}