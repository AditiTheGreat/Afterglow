package com.aditihomkalas.afterglow;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.Gravity;
import android.webkit.*;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int AUDIO_PERMISSION = 101;
    private static final String SITE =
        "https://afterglow-pi-rouge.vercel.app/";

    private LinearLayout home;
    private TextView status;
    private Button visualizerButton;
    private WebView webView;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver audioReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (webView == null) return;

            String type = intent.getStringExtra("type");
            if ("AUDIO_DATA".equals(type)) {
                float[] bins = intent.getFloatArrayExtra("frequencies");
                if (bins == null) return;

                JSONArray values = new JSONArray();
                for (float value : bins) values.put(value);

                JSONObject data = new JSONObject();
                try {
                    data.put("source", "AFTERGLOW");
                    data.put("type", "AUDIO_DATA");
                    data.put("frequencies", values);
                    data.put("bass", intent.getFloatExtra("bass", 0));
                    data.put("mids", intent.getFloatExtra("mids", 0));
                    data.put("treble", intent.getFloatExtra("treble", 0));
                } catch (Exception ignored) {}

                webView.evaluateJavascript(
                    "window.postMessage(" + data + ", '*');", null
                );
            } else if (type != null) {
                String message = intent.getStringExtra("message");
                JSONObject data = new JSONObject();
                try {
                    data.put("source", "AFTERGLOW");
                    data.put("type", type);
                    data.put("message", message == null ? "" : message);
                } catch (Exception ignored) {}

                webView.evaluateJavascript(
                    "window.postMessage(" + data + ", '*');", null
                );
            }
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(8, 6, 16));
        getWindow().setNavigationBarColor(Color.rgb(8, 6, 16));

        buildHome();
        registerAudioReceiver();
        handleAuthIntent(getIntent());
    }

    private void buildHome() {
        home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setGravity(Gravity.CENTER);
        home.setPadding(dp(28), dp(30), dp(28), dp(30));
        home.setBackgroundColor(Color.rgb(8, 6, 16));

        TextView title = new TextView(this);
        title.setText("AFTERGLOW");
        title.setTextColor(Color.rgb(225, 190, 255));
        title.setTextSize(34);
        title.setTypeface(null, 1);
        title.setGravity(Gravity.CENTER);
        home.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("LET THE MUSIC TAKE SHAPE");
        subtitle.setTextColor(Color.rgb(170, 140, 195));
        subtitle.setTextSize(11);
        subtitle.setLetterSpacing(0.16f);
        subtitle.setGravity(Gravity.CENTER);
        addSpace(home, 8);
        home.addView(subtitle);

        status = new TextView(this);
        status.setText("Let's get your visualiser ready.");
        status.setTextColor(Color.rgb(220, 205, 235));
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        addSpace(home, 42);
        home.addView(status);

        addSpace(home, 24);
        addStep("01", "Connect your Spotify account");
        addStep("02", "Open Spotify Web Player in Chrome");
        addStep("03", "Return here to see the visualiser");

        Button connect = makeButton("CONNECT SPOTIFY");
        home.addView(connect, buttonParams());
        connect.setOnClickListener(v -> NativeSpotifyAuth.start(this));

        addSpace(home, 12);
        Button player = makeButton("OPEN SPOTIFY WEB PLAYER");
        home.addView(player, buttonParams());
        player.setOnClickListener(v -> openSpotifyPlayer());

        addSpace(home, 12);
        visualizerButton = makeButton("OPEN AFTERGLOW");
        home.addView(visualizerButton, buttonParams());
        visualizerButton.setEnabled(
            NativeSpotifyAuth.getAccessToken(this) != null
        );
        visualizerButton.setOnClickListener(v -> openVisualizer());

        addSpace(home, 22);
        TextView note = new TextView(this);
        note.setText(
            "For live audio, play music in Spotify Web Player using Chrome. " +
            "The Spotify Android app is not supported by this capture method."
        );
        note.setTextColor(Color.rgb(135, 120, 150));
        note.setTextSize(11);
        note.setGravity(Gravity.CENTER);
        home.addView(note);

        setContentView(home);
    }

    private void addStep(String number, String text) {
        TextView step = new TextView(this);
        step.setText(number + "     " + text);
        step.setTextColor(Color.rgb(205, 185, 225));
        step.setTextSize(13);
        step.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams p = buttonParams();
        p.height = dp(38);
        home.addView(step, p);
    }

    private Button makeButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(
                Color.rgb(112, 57, 155)
            )
        );
        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        return new LinearLayout.LayoutParams(-1, dp(52));
    }

    private void addSpace(LinearLayout layout, int height) {
        TextView space = new TextView(this);
        layout.addView(space, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void openSpotifyPlayer() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{Manifest.permission.RECORD_AUDIO},
                AUDIO_PERMISSION
            );
            return;
        }

        startAudioService();

        Intent browser = new Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://open.spotify.com/")
        );

        try {
            browser.setPackage("com.android.chrome");
            startActivity(browser);
        } catch (Exception error) {
            browser.setPackage(null);
            startActivity(browser);
        }

        status.setText(
            "Spotify opened. Play a song, then return to AFTERGLOW."
        );
    }

    private void startAudioService() {
        Intent service = new Intent(this, AudioVisualizerService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(service);
            } else {
                startService(service);
            }
            status.setText("Audio capture starting...");
        } catch (Exception error) {
            status.setText("Audio service error: " + error.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(
        int requestCode, String[] permissions, int[] results
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == AUDIO_PERMISSION && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            openSpotifyPlayer();
        } else if (requestCode == AUDIO_PERMISSION) {
            status.setText("Audio permission is needed for live visualisation.");
        }
    }

    private void openVisualizer() {
        String token = NativeSpotifyAuth.getAccessToken(this);
        if (token == null) {
            status.setText("Connect Spotify first.");
            return;
        }

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(8, 6, 16));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                String access = NativeSpotifyAuth.getAccessToken(
                    MainActivity.this
                );
                if (access == null) return;

                long expiry = NativeSpotifyAuth.getExpiresAt(
                    MainActivity.this
                );

                String script =
                    "if(localStorage.getItem('afterglow_access_token') !== "
                    + JSONObject.quote(access) + "){"
                    + "localStorage.setItem('afterglow_access_token',"
                    + JSONObject.quote(access) + ");"
                    + "localStorage.setItem('afterglow_controls_authorized','true');"
                    + "localStorage.setItem('afterglow_token_expiry',"
                    + JSONObject.quote(String.valueOf(expiry)) + ");"
                    + "location.reload();}";

                view.evaluateJavascript(script, null);
            }
        });

        setContentView(webView);
        webView.loadUrl(SITE);
    }

    private void registerAudioReceiver() {
        IntentFilter filter = new IntentFilter(
            AudioVisualizerService.ACTION_AUDIO
        );

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                audioReceiver, filter, Context.RECEIVER_NOT_EXPORTED
            );
        } else {
            registerReceiver(audioReceiver, filter);
        }
        receiverRegistered = true;
    }

    private void handleAuthIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;

        NativeSpotifyAuth.handleRedirect(
            this,
            intent.getData(),
            new NativeSpotifyAuth.Listener() {
                @Override
                public void onSuccess(String token, long expiresAt) {
                    runOnUiThread(() -> {
                        status.setText("Spotify connected successfully!");
                        visualizerButton.setEnabled(true);
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> status.setText(message));
                }
            }
        );
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAuthIntent(intent);
    }

    @Override
    public void onBackPressed() {
        if (webView != null) {
            webView = null;
            setContentView(home);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(audioReceiver);
            receiverRegistered = false;
        }
        super.onDestroy();
    }
}
