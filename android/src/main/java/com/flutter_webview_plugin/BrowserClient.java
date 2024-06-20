package com.flutter_webview_plugin;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by lejard_h on 20/12/2017.
 */

public class BrowserClient extends WebViewClient {
    private Pattern invalidUrlPattern = null;

    Map<String, Object> shouldStartUrlHeaders = new HashMap<>();

    public BrowserClient() {
        this(null);
    }

    public BrowserClient(String invalidUrlRegex) {
        super();
        if (invalidUrlRegex != null) {
            invalidUrlPattern = Pattern.compile(invalidUrlRegex);
        }
    }

    public void updateInvalidUrlRegex(String invalidUrlRegex) {
        if (invalidUrlRegex != null) {
            invalidUrlPattern = Pattern.compile(invalidUrlRegex);
        } else {
            invalidUrlPattern = null;
        }
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        Map<String, Object> data = new HashMap<>();
        data.put("url", url);
        if (shouldStartUrlHeaders != null && shouldStartUrlHeaders.get("url") != null && shouldStartUrlHeaders.get("url").equals(url)) {
            data.put("headers", shouldStartUrlHeaders.get("headers"));
        }else if (shouldStartUrlHeaders == null || shouldStartUrlHeaders.get("url") == null || shouldStartUrlHeaders.get("url").toString().isEmpty()) {
            shouldStartUrlHeaders = new HashMap<>();
            shouldStartUrlHeaders.put("url", url);
        }
        data.put("lastStartedHeader", shouldStartUrlHeaders);
        Log.d("onPageStarted", "shouldStartUrlHeaders: "+shouldStartUrlHeaders);

        data.put("type", "startLoad");
        FlutterWebviewPlugin.channel.invokeMethod("onState", data);
    }


    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        Map<String, Object> data = new HashMap<>();
        data.put("url", url);
        if (shouldStartUrlHeaders != null && shouldStartUrlHeaders.get("url") != null && shouldStartUrlHeaders.get("url").equals(url)) {
            data.put("headers", shouldStartUrlHeaders.get("headers"));
        }
        data.put("lastStartedHeader", shouldStartUrlHeaders);
        Log.d("onUrlChanged", "shouldStartUrlHeaders: "+shouldStartUrlHeaders);
        FlutterWebviewPlugin.channel.invokeMethod("onUrlChanged", data);
        data.put("type", "finishLoad");
        FlutterWebviewPlugin.channel.invokeMethod("onState", data);
        shouldStartUrlHeaders = new HashMap<>();

    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        // returning true causes the current WebView to abort loading the URL,
        // while returning false causes the WebView to continue loading the URL as usual.
        String url = request.getUrl().toString();
        boolean isInvalid = checkInvalidUrl(url);
        Map<String, String> headers = request.getRequestHeaders();
        Log.d("shouldOverrideUrl(r)", "request.getRequestHeaders(): "+headers);
        Map<String, Object> data = new HashMap<>();
        data.put("url", url);
        if (headers != null && !headers.isEmpty()) {
            data.put("headers", headers);
        }

        data.put("type", isInvalid ? "abortLoad" : "shouldStart");
        if (!isInvalid) {
            shouldStartUrlHeaders = new HashMap<>();
            shouldStartUrlHeaders.put("url", url);
            if (data.containsKey("headers")) {
                shouldStartUrlHeaders.put("headers", data.get("headers"));
            }
        }
        Log.d("shouldOverrideUrl(r)", "shouldStartUrlHeaders: "+shouldStartUrlHeaders);

        Log.d("shouldOverrideUrl(r)", "data(url): "+data.get("url"));
        Log.d("shouldOverrideUrl(r)", "data has headers? " + (data.containsKey("headers")));
        Log.d("shouldOverrideUrl(r)", "data(type): "+data.get("type"));
        FlutterWebviewPlugin.channel.invokeMethod("onState", data);
        return isInvalid;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        // returning true causes the current WebView to abort loading the URL,
        // while returning false causes the WebView to continue loading the URL as usual.
        boolean isInvalid = checkInvalidUrl(url);
        Map<String, Object> data = new HashMap<>();
        data.put("url", url);
        data.put("type", isInvalid ? "abortLoad" : "shouldStart");

        if (!isInvalid) {
            shouldStartUrlHeaders = new HashMap<>();
            shouldStartUrlHeaders.put("url", url);
        }

        Log.d("shouldOverrideUrl", "data: "+data.get("url"));
        Log.d("shouldOverrideUrl", "data: "+data.get("type"));

        FlutterWebviewPlugin.channel.invokeMethod("onState", data);
        return isInvalid;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
        super.onReceivedHttpError(view, request, errorResponse);
        Map<String, Object> data = new HashMap<>();
        data.put("url", request.getUrl().toString());
        data.put("code", Integer.toString(errorResponse.getStatusCode()));
        FlutterWebviewPlugin.channel.invokeMethod("onHttpError", data);
        shouldStartUrlHeaders = new HashMap<>();
    }

    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        super.onReceivedError(view, errorCode, description, failingUrl);
        view.loadUrl("about:blank");
        Map<String, Object> data = new HashMap<>();
        data.put("url", failingUrl);
        data.put("code", Integer.toString(errorCode));
        FlutterWebviewPlugin.channel.invokeMethod("onHttpError", data);
        shouldStartUrlHeaders = new HashMap<>();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        Log.d("shouldInterceptRequest", "request.headers is empty? "+request.getRequestHeaders().isEmpty());
        Log.d("shouldInterceptRequest", "request.url: "+request.getUrl().toString());

        String thisUrl = request.getUrl().toString();
        if (shouldStartUrlHeaders == null || shouldStartUrlHeaders.get("url") == null || shouldStartUrlHeaders.get("url").toString().isEmpty()) {
            shouldStartUrlHeaders = new HashMap<>();
            shouldStartUrlHeaders.put("url", thisUrl);
        }

        if (thisUrl.equals(shouldStartUrlHeaders.get("url"))){
            shouldStartUrlHeaders.put("headers", request.getRequestHeaders());
        }

        return super.shouldInterceptRequest(view, request);
    }

    private boolean checkInvalidUrl(String url) {
        if (invalidUrlPattern == null) {
            return false;
        } else {
            Matcher matcher = invalidUrlPattern.matcher(url);
            return matcher.lookingAt();
        }
    }

}
