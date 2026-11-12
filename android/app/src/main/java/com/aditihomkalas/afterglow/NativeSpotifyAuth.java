package com.aditihomkalas.afterglow;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class NativeSpotifyAuth {
    public static final String CLIENT_ID = "51e71430160f4341a7ce8f6c645e745f";
    public static final String REDIRECT_URI = "afterglow://callback";
    public static final String SCOPES =
        "user-read-currently-playing user-read-playback-state user-modify-playback-state";

    private static final String PREFS = "afterglow_spotify";
    private static final ExecutorService executor =
        Executors.newSingleThreadExecutor();

    public interface Listener {
        void onSuccess(String accessToken, long expiresAt);
        void onError(String message);
    }

    private NativeSpotifyAuth() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String randomValue(int bytes) {
        byte[] data = new byte[bytes];
        new SecureRandom().nextBytes(data);
        return Base64.encodeToString(
            data,
            Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING
        );
    }

    private static String challenge(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.getBytes(StandardCharsets.UTF_8));

        return Base64.encodeToString(
            digest,
            Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING
        );
    }

    public static void start(Activity activity) {
        try {
            String verifier = randomValue(64);
            String state = randomValue(24);

            prefs(activity).edit()
                .putString("pending_verifier", verifier)
                .putString("pending_state", state)
                .apply();

            Uri authUri = Uri.parse("https://accounts.spotify.com/authorize")
                .buildUpon()
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("scope", SCOPES)
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("code_challenge", challenge(verifier))
                .build();

            activity.startActivity(
                new Intent(Intent.ACTION_VIEW, authUri)
            );
        } catch (Exception error) {
            error.printStackTrace();
        }
    }

    public static void handleRedirect(
        Context context,
        Uri uri,
        Listener listener
    ) {
        if (uri == null || !"afterglow".equals(uri.getScheme())
                || !"callback".equals(uri.getHost())) {
            return;
        }

        String code = uri.getQueryParameter("code");
        String returnedState = uri.getQueryParameter("state");
        String error = uri.getQueryParameter("error");

        SharedPreferences store = prefs(context);
        String expectedState = store.getString("pending_state", null);
        String verifier = store.getString("pending_verifier", null);

        if (error != null) {
            clearPending(store);
            listener.onError("Spotify authorization: " + error);
            return;
        }

        if (code == null || returnedState == null
                || expectedState == null || verifier == null
                || !MessageDigest.isEqual(
                    expectedState.getBytes(StandardCharsets.UTF_8),
                    returnedState.getBytes(StandardCharsets.UTF_8)
                )) {
            clearPending(store);
            listener.onError("Spotify login verification failed.");
            return;
        }

        clearPending(store);

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL("https://accounts.spotify.com/api/token");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty(
                    "Content-Type",
                    "application/x-www-form-urlencoded"
                );

                String body =
                    "client_id=" + enc(CLIENT_ID)
                    + "&grant_type=authorization_code"
                    + "&code=" + enc(code)
                    + "&redirect_uri=" + enc(REDIRECT_URI)
                    + "&code_verifier=" + enc(verifier);

                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body.getBytes(StandardCharsets.UTF_8));
                }

                int status = connection.getResponseCode();
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                        status >= 200 && status < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream(),
                        StandardCharsets.UTF_8
                    )
                );

                StringBuilder responseText = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseText.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(responseText.toString());

                if (status < 200 || status >= 300) {
                    throw new Exception(
                        json.optString("error_description", "Token exchange failed")
                    );
                }

                String accessToken = json.getString("access_token");
                long expiresAt = System.currentTimeMillis()
                    + json.getLong("expires_in") * 1000L;

                SharedPreferences.Editor edit = store.edit()
                    .putString("access_token", accessToken)
                    .putLong("expires_at", expiresAt);

                if (json.has("refresh_token")) {
                    edit.putString(
                        "refresh_token",
                        json.getString("refresh_token")
                    );
                }

                edit.apply();

                listener.onSuccess(accessToken, expiresAt);

            } catch (Exception exception) {
                listener.onError(
                    exception.getMessage() == null
                        ? "Spotify token exchange failed."
                        : exception.getMessage()
                );
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    public static long getExpiresAt(Context context) {\n        return prefs(context).getLong("expires_at", 0L);\n    }\n\n    public static String getAccessToken(Context context) {
        return prefs(context).getString("access_token", null);
    }

    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static void clearPending(SharedPreferences store) {
        store.edit()
            .remove("pending_verifier")
            .remove("pending_state")
            .apply();
    }
}
