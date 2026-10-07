package com.quietly.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;

import java.util.Arrays;
import java.util.List;

public class YouTubeActivity extends AppCompatActivity {

    private static final String PREFS_SETTINGS = "quietly_settings";
    private static final String KEY_API_KEY = "groq_api_key";
    private static final String KEY_MODEL = "groq_model";

    private WebView webView;
    private ProgressBar progressBar;
    private TextView tvBadge;
    private GroqClient groqClient;

    private String apiKey = "";
    private String model = "qwen/qwen3.8-27b";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_youtube);

        SharedPreferences prefs = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        apiKey = prefs.getString(KEY_API_KEY, "");
        model = prefs.getString(KEY_MODEL, "auto-smart");
        String cached = prefs.getString("groq_eligible_models", "");
        if (!cached.isEmpty()) {
            GroqClient.setCachedEligibleModels(Arrays.asList(cached.split(",")));
        }

        groqClient = new GroqClient();

        webView = findViewById(R.id.yt_webview);
        progressBar = findViewById(R.id.yt_progress);
        tvBadge = findViewById(R.id.tv_yt_badge);

        findViewById(R.id.btn_yt_back).setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
            else finish();
        });

        findViewById(R.id.btn_yt_refresh).setOnClickListener(v -> webView.reload());

        setupWebView();
        webView.loadUrl("https://m.youtube.com");
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);

        webView.addJavascriptInterface(new QuietlyBridge(), "QuietlyBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                injectQuietlyStyles(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                injectQuietlyStyles(view);
                injectQuietlyScript(view);
            }
        });
    }

    private void injectQuietlyStyles(WebView view) {
        // CSS to immediately drop Shorts shelves, reels, and marked junk videos
        String css = "ytm-reel-shelf-renderer, ytm-shorts-lockup-view-model-v2, "
                + "grid-shelf-view-model:has(ytm-shorts-lockup-view-model-v2), "
                + "ytm-pivot-bar-item-renderer:nth-child(2), " // Shorts tab in bottom bar
                + ":has(a[href*='/shorts/']), "
                + "a[href^='/shorts/'], "
                + "[aria-label*='Shorts' i], "
                + "[title*='Shorts' i], "
                + "[data-wa-junk='true'] { display: none !important; }";

        String js = "(function() {"
                + "  var style = document.getElementById('quietly-css');"
                + "  if (!style) {"
                + "    style = document.createElement('style');"
                + "    style.id = 'quietly-css';"
                + "    style.textContent = '" + css + "';"
                + "    document.documentElement.appendChild(style);"
                + "  }"
                + "})();";
        view.evaluateJavascript(js, null);
    }

    private void injectQuietlyScript(WebView view) {
        String js = "(function() {"
                + "  if (window._quietlyLoaded) return;"
                + "  window._quietlyLoaded = true;"
                + "  var seen = new Set();"
                + "  var queue = [];"
                + "  var timer = null;"
                + ""
                + "  function harvest() {"
                + "    var tiles = document.querySelectorAll('ytm-video-with-context-renderer, ytm-compact-video-renderer, ytd-rich-item-renderer, yt-lockup-view-model');"
                + "    tiles.forEach(function(tile) {"
                + "      if (tile.querySelector('a[href*=\"/shorts/\"]')) {"
                + "        tile.setAttribute('data-wa-junk', 'true');"
                + "        return;"
                + "      }"
                + "      var a = tile.querySelector('a[href*=\"watch?v=\"]');"
                + "      if (!a) return;"
                + "      var match = a.href.match(/v=([\\w-]{11})/);"
                + "      var id = match ? match[1] : '';"
                + "      if (!id || seen.has(id)) return;"
                + "      seen.add(id);"
                + "      tile.setAttribute('data-wa-id', id);"
                + "      var titleEl = tile.querySelector('h3, .media-item-headline, #video-title');"
                + "      var chanEl = tile.querySelector('.ytm-badge-and-byline-item, .ytd-channel-name, a[href^=\"/@\"]');"
                + "      var title = titleEl ? titleEl.textContent.trim() : '';"
                + "      var channel = chanEl ? chanEl.textContent.trim() : '';"
                + "      if (title) {"
                + "        queue.push({ id: id, title: title, channel: channel });"
                + "      }"
                + "    });"
                + "    if (queue.length > 0 && !timer) {"
                + "      timer = setTimeout(sendBatch, 250);"
                + "    }"
                + "  }"
                + ""
                + "  function sendBatch() {"
                + "    timer = null;"
                + "    if (queue.length === 0) return;"
                + "    var batch = queue.splice(0, 30);"
                + "    window.QuietlyBridge.triage(JSON.stringify(batch));"
                + "  }"
                + ""
                + "  window.QuietlyApplyVerdicts = function(keptIdsJson) {"
                + "    var kept = new Set(JSON.parse(keptIdsJson || '[]'));"
                + "    document.querySelectorAll('[data-wa-id]').forEach(function(tile) {"
                + "      var id = tile.getAttribute('data-wa-id');"
                + "      if (id && !kept.has(id)) {"
                + "        tile.setAttribute('data-wa-junk', 'true');"
                + "      }"
                + "    });"
                + "  };"
                + ""
                + "  new MutationObserver(harvest).observe(document.body, { childList: true, subtree: true });"
                + "  harvest();"
                + "})();";

        view.evaluateJavascript(js, null);
    }

    public class QuietlyBridge {
        @JavascriptInterface
        public void triage(String json) {
            try {
                JSONArray videos = new JSONArray(json);
                if (videos.length() == 0) return;

                groqClient.triageVideos(apiKey, model, videos, new GroqClient.Callback() {
                    @Override
                    public void onSuccess(List<String> keptIds) {
                        JSONArray arr = new JSONArray(keptIds);
                        final String keptJson = arr.toString();
                        runOnUiThread(() -> {
                            if (webView != null) {
                                webView.evaluateJavascript("window.QuietlyApplyVerdicts('" + keptJson + "')", null);
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            tvBadge.setText("⚠ Triage error");
                        });
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
