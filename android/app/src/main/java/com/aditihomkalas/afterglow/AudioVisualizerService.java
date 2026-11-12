package com.aditihomkalas.afterglow;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.audiofx.Visualizer;
import android.os.Build;
import android.os.IBinder;

public class AudioVisualizerService extends Service {
    public static final String ACTION_STOP =
        "com.aditihomkalas.afterglow.STOP_VISUALIZER";
    public static final String ACTION_AUDIO =
        "com.aditihomkalas.afterglow.AUDIO_DATA";

    private static final String CHANNEL = "afterglow_live_audio";
    private Visualizer visualizer;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
            CHANNEL,
            "AFTERGLOW live audio",
            NotificationManager.IMPORTANCE_LOW
        ));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopCapture();
            stopSelf();
            return START_NOT_STICKY;
        }

        Notification notification = new Notification.Builder(this, CHANNEL)
            .setContentTitle("AFTERGLOW is listening")
            .setContentText("Switch to Spotify Web Player in Chrome")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                42,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(42, notification);
        }

        try {
            startCapture();
        } catch (Exception error) {
            sendStatus("CAPTURE_ERROR", error.getClass().getSimpleName()
                + ": " + error.getMessage());
            stopCapture();
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void startCapture() {
        stopCapture();

        visualizer = new Visualizer(0);
        int[] range = Visualizer.getCaptureSizeRange();

        if (visualizer.setCaptureSize(range[1]) != Visualizer.SUCCESS) {
            throw new IllegalStateException("Could not set FFT capture size");
        }

        visualizer.setDataCaptureListener(
            new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(
                    Visualizer v, byte[] waveform, int samplingRate
                ) {}

                @Override
                public void onFftDataCapture(
                    Visualizer v, byte[] fft, int samplingRate
                ) {
                    float[] bins = new float[128];
                    int[] counts = new int[128];

                    for (int i = 2; i + 1 < fft.length; i += 2) {
                        int real = fft[i];
                        int imaginary = fft[i + 1];
                        double magnitude = Math.sqrt(
                            real * real + imaginary * imaginary
                        );

                        int bucket = Math.min(
                            127,
                            (i - 2) * 128 / Math.max(1, fft.length - 2)
                        );

                        bins[bucket] = Math.max(
                            bins[bucket],
                            (float) Math.min(255, magnitude * 2.0)
                        );
                        counts[bucket]++;
                    }

                    float bass = average(bins, 0, 12);
                    float mids = average(bins, 12, 48);
                    float treble = average(bins, 48, 128);

                    AudioState.frequencies = bins;
                    AudioState.level = bass;
                    AudioState.active = true;
                    AudioState.status = bass + mids + treble > 1
                        ? "Live audio detected"
                        : "Listening for playback";

                    Intent update = new Intent(ACTION_AUDIO);
                    update.setPackage(getPackageName());
                    update.putExtra("type", "AUDIO_DATA");
                    update.putExtra("frequencies", bins);
                    update.putExtra("bass", bass);
                    update.putExtra("mids", mids);
                    update.putExtra("treble", treble);
                    sendBroadcast(update);
                }
            },
            Visualizer.getMaxCaptureRate(),
            false,
            true
        );

        if (visualizer.setEnabled(true) != Visualizer.SUCCESS) {
            throw new IllegalStateException("Could not enable output visualizer");
        }

        AudioState.active = true;
        AudioState.status = "Visualizer active — open Spotify in Chrome";
        sendStatus("CAPTURE_STARTED", AudioState.status);
    }

    private float average(float[] values, int start, int end) {
        float sum = 0;
        for (int i = start; i < end; i++) sum += values[i];
        return sum / Math.max(1, end - start);
    }

    private void sendStatus(String type, String message) {
        Intent update = new Intent(ACTION_AUDIO);
        update.setPackage(getPackageName());
        update.putExtra("type", type);
        update.putExtra("message", message);
        sendBroadcast(update);
    }

    private void stopCapture() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
            } catch (Exception ignored) {}
            visualizer = null;
        }

        AudioState.active = false;
        AudioState.level = 0;
    }

    @Override
    public void onDestroy() {
        stopCapture();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
