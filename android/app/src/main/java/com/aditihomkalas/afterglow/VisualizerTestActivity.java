package com.aditihomkalas.afterglow;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.media.audiofx.Visualizer;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class VisualizerTestActivity extends Activity {
    private Visualizer visualizer;
    private TextView status;
    private TextView readings;
    private TextView bars;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(28, 40, 28, 40);
        root.setBackgroundColor(Color.rgb(8, 6, 16));

        TextView title = new TextView(this);
        title.setText("AFTERGLOW");
        title.setTextColor(Color.rgb(225, 190, 255));
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("NATIVE OUTPUT-MIX VISUALIZER TEST");
        subtitle.setTextColor(Color.rgb(157, 135, 180));
        subtitle.setTextSize(11);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);

        status = new TextView(this);
        status.setText("Ready to test");
        status.setTextColor(Color.rgb(220, 190, 255));
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = 50;
        root.addView(status, sp);

        readings = new TextView(this);
        readings.setText("FFT peak: --");
        readings.setTextColor(Color.WHITE);
        readings.setTextSize(14);
        readings.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = 20;
        root.addView(readings, rp);

        bars = new TextView(this);
        bars.setText("▁ ▁ ▁ ▁ ▁ ▁ ▁ ▁ ▁ ▁ ▁ ▁ ▁ ▁ ▁ ▁");
        bars.setTextColor(Color.rgb(190, 105, 255));
        bars.setTextSize(22);
        bars.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = 30;
        root.addView(bars, bp);

        Button start = new Button(this);
        start.setText("TEST SPOTIFY OUTPUT");
        root.addView(start, buttonParams());
        start.setOnClickListener(v -> startTest());

        Button stop = new Button(this);
        stop.setText("STOP TEST");
        root.addView(stop, buttonParams());
        stop.setOnClickListener(v -> stopTest());

        setContentView(root);
    }

    private void startTest() {
        stopTest();

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{Manifest.permission.RECORD_AUDIO}, 77
            );
            return;
        }

        try {
            visualizer = new Visualizer(0);

            int[] range = Visualizer.getCaptureSizeRange();
            int size = range[1];

            int result = visualizer.setCaptureSize(size);
            if (result != Visualizer.SUCCESS) {
                throw new IllegalStateException("Capture size error: " + result);
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
                        int peak = 0;
                        double total = 0;
                        int count = 0;
                        StringBuilder display = new StringBuilder();

                        int buckets = 16;
                        int[] levels = new int[buckets];

                        for (int i = 2; i + 1 < fft.length; i += 2) {
                            int real = fft[i];
                            int imaginary = fft[i + 1];
                            int magnitude = (int) Math.sqrt(
                                real * real + imaginary * imaginary
                            );

                            peak = Math.max(peak, magnitude);
                            total += magnitude;
                            count++;

                            int bucket = Math.min(
                                buckets - 1,
                                (i - 2) * buckets / Math.max(1, fft.length - 2)
                            );
                            levels[bucket] = Math.max(levels[bucket], magnitude);
                        }

                        double average = count == 0 ? 0 : total / count;

                        for (int value : levels) {
                            int height = Math.min(5, value / 8);
                            display.append(new String[]{"▁","▂","▃","▄","▅","▆"}[height])
                                .append(" ");
                        }

                        final int finalPeak = peak;
                        final double finalAverage = average;
                        final String finalBars = display.toString();

                        runOnUiThread(() -> {
                            readings.setText(String.format(
                                Locale.US,
                                "FFT peak: %d    Average: %.1f",
                                finalPeak, finalAverage
                            ));
                            bars.setText(finalBars);

                            status.setText(finalPeak > 8
                                ? "FFT SIGNAL RECEIVED"
                                : "Visualizer active — waiting for audio");
                        });
                    }
                },
                Visualizer.getMaxCaptureRate(),
                false,
                true
            );

            int enabled = visualizer.setEnabled(true);
            if (enabled != Visualizer.SUCCESS) {
                throw new IllegalStateException(
                    "Visualizer enable error: " + enabled
                );
            }

            status.setText("Visualizer started. Play Spotify.");
        } catch (Exception error) {
            stopTest();
            status.setText(
                "NOT AVAILABLE: " + error.getClass().getSimpleName()
                + " — " + error.getMessage()
            );
        }
    }

    private void stopTest() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
            } catch (Exception ignored) {}
            visualizer = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(
        int requestCode, String[] permissions, int[] results
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 77 && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            startTest();
        } else if (requestCode == 77) {
            status.setText("Microphone permission required");
        }
    }

    @Override
    protected void onDestroy() {
        stopTest();
        super.onDestroy();
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, 56);
        p.topMargin = 16;
        return p;
    }
}
