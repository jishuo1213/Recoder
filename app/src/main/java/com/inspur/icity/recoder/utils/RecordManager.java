package com.inspur.icity.recoder.utils;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Handler;
import android.util.Log;

import com.inspur.icity.recoder.model.RecordInfoBean;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import okhttp3.internal.Util;

/**
 * Created by fan on 17-3-13.
 */

public class RecordManager {
    private static final String TAG = "RecordManagerFan";

    private static final int RECORD_TIME_OUT = 0xFF;

    private static final RecordManager ourInstance = new RecordManager();

    private static final int RECORDER_SAMPLERATE = 44100;

    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;

    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord audioRecorder;

    private boolean isRecord;

    private String filePath;

    private String fileName;

    private MediaCodec aacEncoder;

    private MediaPlayer mediaPlayer;

    private long duration;

    private long startTime;

    private String playingPath;

    private Handler eventHandler;

    private int bufferSizeInBytes;

    private String playingId;

    private RecordEventListener listener;


    public static RecordManager getInstance() {
        return ourInstance;
    }

    private RecordManager() {
    }

    public interface RecordEventListener {
        void onPlayingEnd(String id);

        void updateVolume(int volume);
    }

    public void init(Handler handler, RecordEventListener listener) {
//        mediaPlayer = new MediaPlayer();
        eventHandler = handler;
        this.listener = listener;
    }

    public void clean() {
        isRecord = false;
        duration = 0;
        filePath = null;
        playingPath = null;
        fileName = null;
        listener = null;
        if (audioRecorder != null) {
            audioRecorder.stop();
            audioRecorder.release();
            audioRecorder = null;
        }
        if (aacEncoder != null) {
            aacEncoder.stop();
            aacEncoder.release();
            aacEncoder = null;
        }
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    /**
     * 开始录音,多次调用
     */
    public void startRecord() {
        startTime = System.currentTimeMillis();
        audioRecorder.startRecording();
        isRecord = true;
        ThreadPool.exec(recordRunnable);
        Log.i(TAG, "startRecord: " + (60 * 1000 - duration) + "duration" + duration);
//        eventHandler.sendEmptyMessageDelayed(RECORD_TIME_OUT, (60 * 1000 - duration));
    }

    private byte[] short2byte(short[] sData, int count) {
        byte[] bytes = new byte[count * 2];
        double sum = 0;
        short data;
        for (int i = 0; i < count; i++) {
            data = sData[i];
            sum += data ^ 2;
            bytes[i * 2] = (byte) (data & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (data >> 8);
            sData[i] = 0;
        }
        final double amplitude = sum / count;
        if (listener != null)
            listener.updateVolume((int) Math.sqrt(amplitude));
        return bytes;
    }


    /**
     * 创建录音，只调用一次
     *
     * @param path
     */
    public void createRecord(String path, String fileName) {
        Log.d(TAG, "createRecord() called with: path = [" + path + "], fileName = [" + fileName + "]");
        this.filePath = path;
        this.fileName = fileName;
        isRecord = false;
        duration = 0;
        if (audioRecorder == null) {
//            int audioSource = MediaRecorder.AudioSource.MIC;
            int audioSource = MediaRecorder.AudioSource.DEFAULT;
//            int bytesPerElement = 2;
            bufferSizeInBytes = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
                    RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
            audioRecorder = new AudioRecord(audioSource, RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                    RECORDER_AUDIO_ENCODING, bufferSizeInBytes);
            try {
                aacEncoder = MediaCodec.createEncoderByType("audio/mp4a-latm");
            } catch (IOException e) {
                e.printStackTrace();
            }

            MediaFormat format = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 1);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 64000);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096);
            aacEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            aacEncoder.start();
        }

        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.reset();
                if (listener != null) {
                    listener.onPlayingEnd(playingId);
                }
            }
        }

    }

//    private Runnable timeOutRunnable = new Runnable() {
//        @Override
//        public void run() {
////            completeRecord();
//
//        }
//    };

    private Runnable recordRunnable = new Runnable() {
        @Override
        public void run() {
            int bufferElements2Rec = bufferSizeInBytes / 2;
            short sData[] = new short[bufferElements2Rec];
            File file = new File(filePath);
            FileOutputStream stream = null;
            try {
                stream = new FileOutputStream(file, true);
                while (isRecord) {
                    int readSize = audioRecorder.read(sData, 0, bufferElements2Rec);
                    if (AudioRecord.ERROR_INVALID_OPERATION != readSize) {
                        byte[] buffer = short2byte(sData, readSize);
                        offerEncoder(buffer, stream);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            } finally {
                if (stream != null)
                    Util.closeQuietly(stream);
            }
        }
    };


    public void startPlaying(String path, String id) {
        Log.i(TAG, "startPlaying: " + path);
        if (!path.equals(playingPath)) {
            if (playingPath != null) {
                Log.i(TAG, "startPlaying: stop");
                if (mediaPlayer.isPlaying()) {
//                    mediaPlayer.pause();
                    if (listener != null) {
                        listener.onPlayingEnd(playingId);
                    }
                    mediaPlayer.reset();
                } else {
                    mediaPlayer.reset();
                }
            }
            playingPath = path;
            playingId = id;
            startPlay(path, playingId);
        } else {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.reset();
                if (listener != null) {
                    listener.onPlayingEnd(playingId);
                }
//                mediaPlayer.stop();
            } else {
//                mediaPlayer.start();
                startPlay(playingPath, playingId);
            }
        }
    }

    private void startPlay(String path, final String id) {
        try {
            mediaPlayer.setDataSource(path);
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mediaPlayer.start();
                }
            });
//            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
//                @Override
//                public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
//                    Log.i(TAG, "onError: " + i + "==========" + i1);
//                    return false;
//                }
//            });
//            mediaPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {
//                @Override
//                public boolean onInfo(MediaPlayer mediaPlayer, int i, int i1) {
//                    Log.i(TAG, "onInfo: " + i + "==============" + i1);
//                    return false;
//                }
//            });
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mp.reset();
                    if (listener != null) {
                        listener.onPlayingEnd(id);
                    }
                }
            });
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * 结束录音,多次调用
     */
    public void stopRecord() {
        eventHandler.removeMessages(RECORD_TIME_OUT);
        duration += System.currentTimeMillis() - startTime;
        isRecord = false;
        audioRecorder.stop();
    }

    /**
     * 取消录音，调用一次
     */
    public void cancelRecord() {
        stopRecord();
//        aacEncoder.stop();
//        aacEncoder.release();
        deleteRecordFile();
//        clean();
    }

    private void deleteRecordFile() {
        File file = new File(filePath);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    /**
     * 完成录音，调用一次
     */
    public RecordInfoBean completeRecord() {
        isRecord = false;
        stopRecord();
//        aacEncoder.stop();
//        int result = recordEncoder.pcm_to_acc(filePath, filePath.replace("pcm", "aac"));
//        Log.i(TAG, "completeRecord: " + filePath + ".acc");
//        Log.i(TAG, "completeRecord: " + result);
//        String path = filePath;
        RecordInfoBean bean = new RecordInfoBean();
        if (duration < 1000) {
            deleteRecordFile();
//            clean();
            return null;
        }
        bean.duration = (int) (duration / 1000);
        bean.recordFilePath = filePath;
        bean.fileName = fileName;
//        clean();
        return bean;
    }

    @SuppressWarnings("deprecation")
    private void offerEncoder(byte[] input, OutputStream outputStream) {

        ByteBuffer[] inputBuffers = aacEncoder.getInputBuffers();
        ByteBuffer[] outputBuffers = aacEncoder.getOutputBuffers();
        int inputBufferIndex = aacEncoder.dequeueInputBuffer(-1);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();


            inputBuffer.put(input);


            aacEncoder.queueInputBuffer(inputBufferIndex, 0, input.length, 0, 0);
        }


        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = aacEncoder.dequeueOutputBuffer(bufferInfo, 0);


        while (outputBufferIndex >= 0) {
            int outBitsSize = bufferInfo.size;
            int outPacketSize = outBitsSize + 7; // 7 is ADTS size
            ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];


            outputBuffer.position(bufferInfo.offset);
            outputBuffer.limit(bufferInfo.offset + outBitsSize);


            byte[] outData = new byte[outPacketSize];
            addADTStoPacket(outData, outPacketSize);


            outputBuffer.get(outData, 7, outBitsSize);
            outputBuffer.position(bufferInfo.offset);


            try {
                outputStream.write(outData, 0, outData.length);
            } catch (IOException e) {
                e.printStackTrace();
            }

            aacEncoder.releaseOutputBuffer(outputBufferIndex, false);
            outputBufferIndex = aacEncoder.dequeueOutputBuffer(bufferInfo, 0);

        }
    }


    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2; // AAC LC
        int freqIdx = 4; // 44.1KHz
        int chanCfg = 1; // CPE


        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }
}
