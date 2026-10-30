package com.jajua.gts;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Checks the project's latest GitHub release (only when the user taps "Buscar actualizaciones").
 * Reads the public releases API; nothing about the device is sent.
 */
final class GitHubUpdate {
    /** owner/repo of the published launcher; forks change this. */
    static final String REPO = "jorgejbz/GTS-Launcher";
    static final String PAGE = "https://github.com/" + REPO + "/releases";

    static final class Release {
        final String version;
        final String apkUrl;

        Release(String version, String apkUrl) {
            this.version = version;
            this.apkUrl = apkUrl;
        }
    }

    private static final Handler main = new Handler(Looper.getMainLooper());

    private GitHubUpdate() {}

    /** onResult gets null when GitHub cannot be reached or the release has no APK. */
    static void latest(Consumer<Release> onResult) {
        new Thread(() -> {
            Release r = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(
                        "https://api.github.com/repos/" + REPO + "/releases/latest").openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(10000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                if (c.getResponseCode() == 200) {
                    JSONObject json = new JSONObject(read(c.getInputStream()));
                    String tag = json.optString("tag_name", "").replaceFirst("^[vV]", "");
                    JSONArray assets = json.optJSONArray("assets");
                    String apk = null;
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            String url = assets.getJSONObject(i).optString("browser_download_url", "");
                            if (url.endsWith(".apk")) {
                                apk = url;
                                break;
                            }
                        }
                    }
                    if (!tag.isEmpty() && apk != null) r = new Release(tag, apk);
                }
                c.disconnect();
            } catch (Exception ignored) {
                r = null;
            }
            Release result = r;
            main.post(() -> onResult.accept(result));
        }, "github-update").start();
    }

    private static String read(InputStream in) throws java.io.IOException {
        try (InputStream s = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = s.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
