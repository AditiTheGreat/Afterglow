package com.aditihomkalas.afterglow;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_AUDIO = 101;
    private static final int REQUEST_CAPTURE = 102;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView statusText;
    private TextView levelText;
    private MeterView meter;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            if (statusText != null) {
                statusText.setText(AudioState.status);
                levelText.setText(String.format(
                    Locale.US,
                    "INPUT  %.1f%%       PEAK  %.1f%%",
                    AudioState.level,
                    AudioState.peak
                ));
                meter.setLevel(AudioState.level);
                meter.invalidate();
            }
            handler.postDelayed(this, 100);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
        handler.post(refresh);
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(32), dp(28), dp(32));
        root.setBackgroundColor(Color.rgb(8, 6, 16));

        TextView title = new TextView(this);
        title.setText("AFTERGLOW");
        title.setTextColor(Color.rgb(225, 190, 255));
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("ANDROID AUDIO CAPTURE TEST");
        subtitle.setTextColor(Color.rgb(157, 135, 180));
        subtitle.setTextSize(11);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams subParams = matchWrap();
        subParams.topMargin = dp(8);
        root.addView(subtitle, subParams);

        meter = new MeterView(this);
        LinearLayout.LayoutParams meterParams =
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(100)
            );
        meterParams.topMargin = dp(64);
        meterParams.bottomMargin = dp(24);
        root.addView(meter, meterParams);

        levelText = new TextView(this);
        levelText.setText("INPUT  0.0%       PEAK  0.0%");
        levelText.setTextColor(Color.rgb(221, 197, 244));
        levelText.setTextSize(13);
        levelText.setGravity(Gravity.CENTER);
        root.addView(levelText, matchWrap());

        statusText = new TextView(this);
        statusText.setText("Ready");
        statusText.setTextColor(Color.rgb(190, 160, 220));
        statusText.setTextSize(14);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(22);
        statusParams.bottomMargin = dp(34);
        root.addView(statusText, statusParams);

        Button start = new Button(this);
        start.setText("START AUDIO TEST");
        start.setTextColor(Color.WHITE);
        start.setAllCaps(false);
        start.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(Color.rgb(133, 65, 185))
        );
        root.addView(start, buttonParams());
        start.setOnClickListener(v -> beginCapture());

        Button stop = new Button(this);
        stop.setText("STOP CAPTURE");
        stop.setTextColor(Color.rgb(225, 195, 245));
        stop.setAllCaps(false);
        stop.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(Color.rgb(38, 27, 52))
        );
        LinearLayout.LayoutParams stopParams = buttonParams();
        stopParams.topMargin = dp(12);
        root.addView(stop, stopParams);
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, CaptureService.class));
            AudioState.status = "Stopped by user";
            AudioState.active = false;
            AudioState.level = 0;
            AudioState.peak = 0;
        });

        TextView note = new TextView(this);
        note.setText(
            "Play Spotify after granting capture permission. " +
            "A moving meter means Android is receiving playback audio."
        );
        note.setTextColor(Color.rgb(125, 111, 145));
        note.setTextSize(12);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = matchWrap();
        noteParams.topMargin = dp(36);
        root.addView(note, noteParams);

        setContentView(root);
    }

    private void beginCapture() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{Manifest.permission.RECORD_AUDIO},
                REQUEST_AUDIO
            );
            return;
        }
        requestCaptureConsent();
    }

    private void requestCaptureConsent() {
        MediaProjectionManager manager =
            (MediaProjectionManager) getSystemService(
                Context.MEDIA_PROJECTION_SERVICE
            );
        startActivityForResult(
            manager.createScreenCaptureIntent(),
            REQUEST_CAPTURE
        );
    }

    @Override
    public void onRequestPermissionsResult(
        int requestCode,
        String[] permissions,
        int[] results
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_AUDIO) {
            if (results.length > 0 &&
                    results[0] == PackageManager.PERMISSION_GRANTED) {
                requestCaptureConsent();
            } else {
                AudioState.status = "Microphone/audio permission denied";
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_CAPTURE) return;

        if (resultCode != RESULT_OK || data == null) {
            AudioState.status = "Screen/audio capture permission declined";
            return;
        }

        AudioState.status = "Starting playback capture...";
        Intent service = new Intent(this, CaptureService.class);
        service.putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode);
        service.putExtra(CaptureService.EXTRA_RESULT_DATA, data);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service);
            } else {
                startService(service);
            }
        } catch (Exception error) {
            AudioState.status = "Could not start: " + error.getMessage();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refresh);
        super.onDestroy();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(54)
        );
        return params;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class MeterView extends View {
        private final android.graphics.Paint paint =
            new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private float level = 0;

        MeterView(Context context) {
            super(context);
        }

        void setLevel(float value) {
            level = Math.max(0, Math.min(100, value));
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);

            int count = 32;
            float gap = 7f * getResources().getDisplayMetrics().density;
            float barWidth = (getWidth() - gap * (count - 1)) / count;
            float centerY = getHeight() / 2f;
            float maxHeight = getHeight() * 0.82f;

            for (int i = 0; i < count; i++) {
                float fraction = level / 100f;
                float shape = 0.35f + 0.65f *
                    (float)Math.abs(Math.sin(i * 0.72));
                float barHeight = Math.max(
                    dp(3),
                    maxHeight * fraction * shape
                );

                paint.setColor(
                    fraction > 0.02f
                        ? Color.rgb(190, 105, 255)
                        : Color.rgb(49, 35, 64)
                );
                paint.setAlpha(fraction > 0.02f ? 230 : 180);

                float left = i * (barWidth + gap);
                canvas.drawRoundRect(
                    left,
                    centerY - barHeight / 2,
                    left + barWidth,
                    centerY + barHeight / 2,
                    barWidth / 2,
                    barWidth / 2,
                    paint
                );
            }
        }

        private float dp(float value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }
}
