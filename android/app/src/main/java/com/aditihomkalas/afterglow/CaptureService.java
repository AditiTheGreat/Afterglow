package com.aditihomkalas.afterglow;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.content.pm.ServiceInfo;

public class CaptureService extends Service {
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";

    private static final String CHANNEL_ID = "afterglow_capture";
    private static final int NOTIFICATION_ID = 42;

    private volatile boolean running = false;
    private AudioRecord recorder;
    private MediaProjection projection;
    private Thread captureThread;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            AudioState.status = "Missing capture permission data";
            stopSelf();
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);

        if (resultData == null) {
            AudioState.status = "Missing capture permission data";
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("AFTERGLOW audio test")
                .setContentText("Checking system playback audio")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();

            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                );
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }

            MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(
                    MEDIA_PROJECTION_SERVICE
                );
            projection = manager.getMediaProjection(resultCode, resultData);

            if (projection == null) {
                throw new IllegalStateException("MediaProjection unavailable");
            }

            projection.registerCallback(
                new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        running = false;
                        AudioState.active = false;
                        AudioState.level = 0;
                        AudioState.status = "Capture permission stopped";
                        stopSelf();
                    }
                },
                new Handler(Looper.getMainLooper())
            );

            startAudioRecord();
        } catch (Exception error) {
            AudioState.status = "Capture error: " + error.getClass().getSimpleName()
                + " - " + error.getMessage();
            AudioState.active = false;
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void startAudioRecord() {
        AudioPlaybackCaptureConfiguration config =
            new AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build();

        AudioFormat format = new AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(48000)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build();

        int minBuffer = AudioRecord.getMinBufferSize(
            48000,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        );

        if (minBuffer <= 0) minBuffer = 8192;

        recorder = new AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(Math.max(minBuffer, 16384))
            .setAudioPlaybackCaptureConfig(config)
            .build();

        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new IllegalStateException("AudioRecord did not initialise");
        }

        recorder.startRecording();
        if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            throw new IllegalStateException("AudioRecord did not start");
        }

        running = true;
        AudioState.active = true;
        AudioState.status = "Listening — play Spotify now";

        captureThread = new Thread(() -> readAudio(), "AfterglowAudioCapture");
        captureThread.start();
    }

    private void readAudio() {
        short[] samples = new short[2048];

        while (running && recorder != null) {
            int count;
            try {
                count = recorder.read(samples, 0, samples.length);
            } catch (Exception error) {
                AudioState.status = "Read error: " + error.getMessage();
                break;
            }

            if (count < 0) {
                AudioState.status = "Audio read failed (code " + count + ")";
                break;
            }
            if (count == 0) continue;

            double sum = 0;
            int peak = 0;

            for (int i = 0; i < count; i++) {
                int value = Math.abs((int) samples[i]);
                peak = Math.max(peak, value);
                sum += (double) samples[i] * samples[i];
            }

            double rms = Math.sqrt(sum / count);
            AudioState.level = (float)Math.min(100, rms / 32768.0 * 100.0);
            AudioState.peak = (float)Math.min(100, peak / 32768.0 * 100.0);

            if (AudioState.level > 0.5f) {
                AudioState.status = "AUDIO DETECTED — live playback received";
            } else {
                AudioState.status = "Listening — no audio signal detected yet";
            }
        }

        AudioState.active = false;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "AFTERGLOW audio capture",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows when AFTERGLOW is testing playback capture");
            NotificationManager manager =
                getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        running = false;

        if (recorder != null) {
            try {
                if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop();
                }
            } catch (Exception ignored) {}
        }

        if (captureThread != null && captureThread.isAlive()) {
            try {
                captureThread.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        if (recorder != null) {
            recorder.release();
            recorder = null;
        }

        if (projection != null) {
            try {
                projection.stop();
            } catch (Exception ignored) {}
            projection = null;
        }

        AudioState.active = false;
        AudioState.level = 0;
        AudioState.peak = 0;

        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
