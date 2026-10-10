package app.commentdesk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView web;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String u = request.getUrl().toString();
                if (u.startsWith("file:///android_asset/")) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
                } catch (Exception ignored) { }
                return true;
            }
        });
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        return sb.toString();
    }

    private class Bridge {
        @JavascriptInterface
        public void copy(String text) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("reply", text));
        }

        @JavascriptInterface
        public void call(final int id, final String method, final String path, final String key, final String body) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    int code = 0;
                    String out = "";
                    try {
                        if (path == null || !path.startsWith("/v1beta/") || path.contains("..")) throw new Exception("bad path");
                        URL u = new URL("https://generativelanguage.googleapis.com" + path);
                        HttpURLConnection c = (HttpURLConnection) u.openConnection();
                        boolean post = "POST".equals(method);
                        c.setRequestMethod(post ? "POST" : "GET");
                        c.setConnectTimeout(10000);
                        c.setReadTimeout(30000);
                        c.setRequestProperty("x-goog-api-key", key);
                        if (post) {
                            c.setDoOutput(true);
                            c.setRequestProperty("Content-Type", "application/json");
                            OutputStream os = c.getOutputStream();
                            os.write(String.valueOf(body).getBytes(StandardCharsets.UTF_8));
                            os.close();
                        }
                        code = c.getResponseCode();
                        out = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
                    } catch (Exception e) {
                        code = -1;
                        out = String.valueOf(e.getMessage());
                    }
                    final int fcode = code;
                    final String fout = out;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            web.evaluateJavascript("window.__gem(" + id + "," + fcode + "," + JSONObject.quote(fout) + ")", null);
                        }
                    });
                }
            }).start();
        }

        @JavascriptInterface
        public void gemini(final int id, final String model, final String key, final String body) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    int code = 0;
                    String out = "";
                    try {
                        URL u = new URL("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent");
                        HttpURLConnection c = (HttpURLConnection) u.openConnection();
                        c.setRequestMethod("POST");
                        c.setConnectTimeout(10000);
                        c.setReadTimeout(30000);
                        c.setDoOutput(true);
                        c.setRequestProperty("Content-Type", "application/json");
                        c.setRequestProperty("x-goog-api-key", key);
                        OutputStream os = c.getOutputStream();
                        os.write(body.getBytes(StandardCharsets.UTF_8));
                        os.close();
                        code = c.getResponseCode();
                        out = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
                    } catch (Exception e) {
                        code = -1;
                        out = String.valueOf(e.getMessage());
                    }
                    final int fcode = code;
                    final String fout = out;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            web.evaluateJavascript("window.__gem(" + id + "," + fcode + "," + JSONObject.quote(fout) + ")", null);
                        }
                    });
                }
            }).start();
        }
    }
}
