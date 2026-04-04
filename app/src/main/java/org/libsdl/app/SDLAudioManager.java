package org.libsdl.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import java.util.Arrays;

public class SDLAudioManager {
    protected static final String TAG = "SDLAudio";

    protected static AudioTrack mAudioTrack;
    protected static AudioRecord mAudioRecord;
    protected static Context mContext;

    private static final int[] NO_DEVICES = {};

    private static AudioDeviceCallback mAudioDeviceCallback;

    public static void initialize() {
        mAudioTrack = null;
        mAudioRecord = null;
        mAudioDeviceCallback = null;

        if(Build.VERSION.SDK_INT >= 24 /* Android 7.0 (N) */)
        {
            mAudioDeviceCallback = new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    Arrays.stream(addedDevices).forEach(deviceInfo -> addAudioDevice(deviceInfo.isSink(), deviceInfo.getId()));
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    Arrays.stream(removedDevices).forEach(deviceInfo -> removeAudioDevice(deviceInfo.isSink(), deviceInfo.getId()));
                }
            };
        }
    }

    public static void setContext(Context context) {
        mContext = context;
        if (context != null) {
            registerAudioDeviceCallback();
        }
    }

    public static void release(Context context) {
        unregisterAudioDeviceCallback(context);
    }

    protected static String getAudioFormatString(int audioFormat) {
        switch (audioFormat) {
            case AudioFormat.ENCODING_PCM_8BIT:
                return "8-bit";
            case AudioFormat.ENCODING_PCM_16BIT:
                return "16-bit";
            case AudioFormat.ENCODING_PCM_FLOAT:
                return "float (32-bit Studio Quality)";
            default:
                return Integer.toString(audioFormat);
        }
    }

    protected static int[] open(boolean isCapture, int sampleRate, int audioFormat, int desiredChannels, int desiredFrames, int deviceId) {
        int channelConfig;
        int sampleSize;
        int frameSize;

        // =========================================================
        // [极客优化] 运行时双轨制：架构侦测 (32-bit 维稳 vs 64-bit 激进)
        // =========================================================
        boolean is64Bit = false;
        if (Build.VERSION.SDK_INT >= 23 /* Android 6.0 */) {
            is64Bit = android.os.Process.is64Bit();
        }

        if (is64Bit && !isCapture) {
            Log.v(TAG, "Arch: 64-bit detected. Enabling Aggressive Audio Mode.");
            if (Build.VERSION.SDK_INT >= 21) {
                audioFormat = AudioFormat.ENCODING_PCM_FLOAT;
            }
            if (desiredChannels < 2) desiredChannels = 2;
        } else if (!isCapture) {
            Log.v(TAG, "Arch: 32-bit detected. Enabling Stable Safe Mode.");
            audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            if (desiredChannels > 2) desiredChannels = 2;
        }

        switch (audioFormat)
        {
        case AudioFormat.ENCODING_PCM_8BIT:
            sampleSize = 1;
            break;
        case AudioFormat.ENCODING_PCM_16BIT:
            sampleSize = 2;
            break;
        case AudioFormat.ENCODING_PCM_FLOAT:
            sampleSize = 4;
            break;
        default:
            audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            sampleSize = 2;
            break;
        }

        if (isCapture) {
            channelConfig = (desiredChannels == 2) ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
        } else {
            switch (desiredChannels) {
            case 1:
                channelConfig = AudioFormat.CHANNEL_OUT_MONO;
                break;
            case 2:
                channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
                break;
            case 3:
                channelConfig = AudioFormat.CHANNEL_OUT_STEREO | AudioFormat.CHANNEL_OUT_FRONT_CENTER;
                break;
            case 4:
                channelConfig = AudioFormat.CHANNEL_OUT_QUAD;
                break;
            case 5:
                channelConfig = AudioFormat.CHANNEL_OUT_QUAD | AudioFormat.CHANNEL_OUT_FRONT_CENTER;
                break;
            case 6:
                channelConfig = AudioFormat.CHANNEL_OUT_5POINT1;
                break;
            case 7:
                channelConfig = AudioFormat.CHANNEL_OUT_5POINT1 | AudioFormat.CHANNEL_OUT_BACK_CENTER;
                break;
            case 8:
                if (Build.VERSION.SDK_INT >= 23 /* Android 6.0 (M) */) {
                    channelConfig = AudioFormat.CHANNEL_OUT_7POINT1_SURROUND;
                } else {
                    desiredChannels = 6;
                    channelConfig = AudioFormat.CHANNEL_OUT_5POINT1;
                }
                break;
            default:
                desiredChannels = 2;
                channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
                break;
            }
        }
        frameSize = (sampleSize * desiredChannels);

        // =========================================================
        // [极客优化] 缓冲区策略分化 (延迟 vs 稳定)
        // =========================================================
        int minBufferSize;
        if (isCapture) {
            minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        } else {
            minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        }

        if (!isCapture) {
            if (is64Bit && Build.VERSION.SDK_INT >= 17) {
                AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                String framesPerBuffer = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
                if (framesPerBuffer != null) {
                    int hardwareFrames = Integer.parseInt(framesPerBuffer);
                    minBufferSize = hardwareFrames * frameSize * 2;
                    Log.v(TAG, "Aggressive Profile: Fast Track Audio Enabled. Buffer size forced to: " + minBufferSize);
                }
            } else {
                minBufferSize = minBufferSize * 2;
                Log.v(TAG, "Stable Profile: Double Buffering Enabled. Buffer size expanded to: " + minBufferSize);
            }
        }

        desiredFrames = Math.max(desiredFrames, (minBufferSize + frameSize - 1) / frameSize);

        int[] results = new int[4];

        if (isCapture) {
            if (mAudioRecord == null) {
                mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRate,
                        channelConfig, audioFormat, desiredFrames * frameSize);

                if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "Failed during initialization of AudioRecord");
                    mAudioRecord.release();
                    mAudioRecord = null;
                    return null;
                }

                if (Build.VERSION.SDK_INT >= 24 && deviceId != 0) {
                    mAudioRecord.setPreferredDevice(getOutputAudioDeviceInfo(deviceId));
                }

                mAudioRecord.startRecording();
            }

            results[0] = mAudioRecord.getSampleRate();
            results[1] = mAudioRecord.getAudioFormat();
            results[2] = mAudioRecord.getChannelCount();

        } else {
            if (mAudioTrack == null) {
                // =========================================================
                // [极客优化] 针对格斗游戏的 Low Latency (低延迟) 音频构造
                // =========================================================
                if (Build.VERSION.SDK_INT >= 26) {
                    AudioAttributes attributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME) // 声明为游戏音频
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION) // 强化打击音效优先级
                            .build();
                    AudioFormat format = new AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .setEncoding(audioFormat)
                            .build();
                    mAudioTrack = new AudioTrack.Builder()
                            .setAudioAttributes(attributes)
                            .setAudioFormat(format)
                            .setBufferSizeInBytes(desiredFrames * frameSize)
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) // 核心：请求底层极低延迟
                            .build();
                } else if (Build.VERSION.SDK_INT >= 21) {
                    AudioAttributes attributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build();
                    AudioFormat format = new AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .setEncoding(audioFormat)
                            .build();
                    mAudioTrack = new AudioTrack(attributes, format, desiredFrames * frameSize, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
                } else {
                    mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfig, audioFormat, desiredFrames * frameSize, AudioTrack.MODE_STREAM);
                }

                if (mAudioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                    Log.e(TAG, "Failed during initialization of Audio Track");
                    mAudioTrack.release();
                    mAudioTrack = null;
                    return null;
                }

                if (Build.VERSION.SDK_INT >= 24 && deviceId != 0) {
                    mAudioTrack.setPreferredDevice(getInputAudioDeviceInfo(deviceId));
                }

                mAudioTrack.play();
            }

            results[0] = mAudioTrack.getSampleRate();
            results[1] = mAudioTrack.getAudioFormat();
            results[2] = mAudioTrack.getChannelCount();
        }
        results[3] = desiredFrames;

        Log.v(TAG, "Opening " + (isCapture ? "capture" : "playback") + ", got " + results[3] + " frames of " + results[2] + " channel " + getAudioFormatString(results[1]) + " audio at " + results[0] + " Hz");

        return results;
    }

    private static AudioDeviceInfo getInputAudioDeviceInfo(int deviceId) {
        if (Build.VERSION.SDK_INT >= 24) {
            AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            return Arrays.stream(audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS))
                    .filter(deviceInfo -> deviceInfo.getId() == deviceId)
                    .findFirst()
                    .orElse(null);
        } else {
            return null;
        }
    }

    private static AudioDeviceInfo getOutputAudioDeviceInfo(int deviceId) {
        if (Build.VERSION.SDK_INT >= 24) {
            AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            return Arrays.stream(audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
                    .filter(deviceInfo -> deviceInfo.getId() == deviceId)
                    .findFirst()
                    .orElse(null);
        } else {
            return null;
        }
    }

    private static void registerAudioDeviceCallback() {
        if (Build.VERSION.SDK_INT >= 24) {
            AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            audioManager.registerAudioDeviceCallback(mAudioDeviceCallback, null);
        }
    }

    private static void unregisterAudioDeviceCallback(Context context) {
        if (Build.VERSION.SDK_INT >= 24) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            audioManager.unregisterAudioDeviceCallback(mAudioDeviceCallback);
        }
    }

    public static int[] getAudioOutputDevices() {
        if (Build.VERSION.SDK_INT >= 24) {
            AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            return Arrays.stream(audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).mapToInt(AudioDeviceInfo::getId).toArray();
        } else {
            return NO_DEVICES;
        }
    }

    public static int[] getAudioInputDevices() {
        if (Build.VERSION.SDK_INT >= 24) {
            AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            return Arrays.stream(audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)).mapToInt(AudioDeviceInfo::getId).toArray();
        } else {
            return NO_DEVICES;
        }
    }

    public static int[] audioOpen(int sampleRate, int audioFormat, int desiredChannels, int desiredFrames, int deviceId) {
        return open(false, sampleRate, audioFormat, desiredChannels, desiredFrames, deviceId);
    }

    public static void audioWriteFloatBuffer(float[] buffer) {
        if (mAudioTrack == null) return;
        if (android.os.Build.VERSION.SDK_INT < 21) return;

        for (int i = 0; i < buffer.length;) {
            int result = mAudioTrack.write(buffer, i, buffer.length - i, AudioTrack.WRITE_BLOCKING);
            if (result > 0) {
                i += result;
            } else if (result == 0) {
                try { Thread.sleep(1); } catch(InterruptedException e) {}
            } else {
                return;
            }
        }
    }

    public static void audioWriteShortBuffer(short[] buffer) {
        if (mAudioTrack == null) return;

        for (int i = 0; i < buffer.length;) {
            int result = mAudioTrack.write(buffer, i, buffer.length - i);
            if (result > 0) {
                i += result;
            } else if (result == 0) {
                try { Thread.sleep(1); } catch(InterruptedException e) {}
            } else {
                return;
            }
        }
    }

    public static void audioWriteByteBuffer(byte[] buffer) {
        if (mAudioTrack == null) return;

        for (int i = 0; i < buffer.length; ) {
            int result = mAudioTrack.write(buffer, i, buffer.length - i);
            if (result > 0) {
                i += result;
            } else if (result == 0) {
                try { Thread.sleep(1); } catch(InterruptedException e) {}
            } else {
                return;
            }
        }
    }

    public static int[] captureOpen(int sampleRate, int audioFormat, int desiredChannels, int desiredFrames, int deviceId) {
        return open(true, sampleRate, audioFormat, desiredChannels, desiredFrames, deviceId);
    }

    public static int captureReadFloatBuffer(float[] buffer, boolean blocking) {
        if (Build.VERSION.SDK_INT < 23) return 0;
        return mAudioRecord.read(buffer, 0, buffer.length, blocking ? AudioRecord.READ_BLOCKING : AudioRecord.READ_NON_BLOCKING);
    }

    public static int captureReadShortBuffer(short[] buffer, boolean blocking) {
        if (Build.VERSION.SDK_INT < 23) return mAudioRecord.read(buffer, 0, buffer.length);
        return mAudioRecord.read(buffer, 0, buffer.length, blocking ? AudioRecord.READ_BLOCKING : AudioRecord.READ_NON_BLOCKING);
    }

    public static int captureReadByteBuffer(byte[] buffer, boolean blocking) {
        if (Build.VERSION.SDK_INT < 23) return mAudioRecord.read(buffer, 0, buffer.length);
        return mAudioRecord.read(buffer, 0, buffer.length, blocking ? AudioRecord.READ_BLOCKING : AudioRecord.READ_NON_BLOCKING);
    }

    public static void audioClose() {
        if (mAudioTrack != null) {
            mAudioTrack.stop();
            mAudioTrack.release();
            mAudioTrack = null;
        }
    }

    public static void captureClose() {
        if (mAudioRecord != null) {
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
        }
    }

    public static void audioSetThreadPriority(boolean iscapture, int device_id) {
        try {
            if (iscapture) {
                Thread.currentThread().setName("SDLAudioC" + device_id);
            } else {
                Thread.currentThread().setName("SDLAudioP" + device_id);
            }
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        } catch (Exception e) {
            Log.v(TAG, "modify thread properties failed " + e.toString());
        }
    }

    public static native int nativeSetupJNI();
    public static native void removeAudioDevice(boolean isCapture, int deviceId);
    public static native void addAudioDevice(boolean isCapture, int deviceId);
}
