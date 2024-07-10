package com.flutter_webview_plugin;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;

import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;

/**
 * Created by lejard_h on 20/12/2017.
 */

public class BrowserClient extends WebViewClient {
    private Pattern invalidUrlPattern = null;

    Map<String, Map> urlHeaders = new HashMap<>();

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
        data.put("type", "startLoad");

        if (urlHeaders.containsKey(getUrlLink(url))) {
            data.put("headers", urlHeaders.get(getUrlLink(url)));
        }else{
            urlHeaders.put(getUrlLink(url), new HashMap<>());
        }
        data.put("loadedUrlHeaders", urlHeaders);
        FlutterWebviewPlugin.channel.invokeMethod("onState", data);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        Map<String, Object> data = new HashMap<>();
        data.put("url", url);
        if (urlHeaders.containsKey(getUrlLink(url))) {
            data.put("headers", urlHeaders.get(getUrlLink(url)));
        }
        data.put("loadedUrlHeaders", urlHeaders);
        FlutterWebviewPlugin.channel.invokeMethod("onUrlChanged", data);

        data.put("type", "finishLoad");
        FlutterWebviewPlugin.channel.invokeMethod("onState", data);
        resetUrlHeaders();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        // returning true causes the current WebView to abort loading the URL,
        // while returning false causes the WebView to continue loading the URL as usual.
        String url = request.getUrl().toString();
        boolean isInvalid = checkInvalidUrl(url);
        Map<String, Object> data = new HashMap<>();
        data.put("url", url);
        data.put("type", isInvalid ? "abortLoad" : "shouldStart");

        if (urlHeaders.containsKey(getUrlLink(url))) {
            if (request.getRequestHeaders() != null && !request.getRequestHeaders().isEmpty()) {
                urlHeaders.put(getUrlLink(url), request.getRequestHeaders());
            }else {
                urlHeaders.put(getUrlLink(url), new HashMap<>());
            }

        } else {
            urlHeaders.put(getUrlLink(url), request.getRequestHeaders() != null ? request.getRequestHeaders() : new HashMap<>());
        }

        data.put("headers", urlHeaders.get(getUrlLink(url)));
        data.put("loadedUrlHeaders", urlHeaders);

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

        if (!urlHeaders.containsKey(getUrlLink(url))) {
            urlHeaders.put(getUrlLink(url), new HashMap<>());
        }

        data.put("headers", urlHeaders.get(getUrlLink(url)));
        data.put("loadedUrlHeaders", urlHeaders);
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
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {

        if (urlHeaders.containsKey(getUrlLink(request.getUrl().toString()))) {
            urlHeaders.put(getUrlLink(request.getUrl().toString()), request.getRequestHeaders() != null ? request.getRequestHeaders() : new HashMap<>());
        }
        return super.shouldInterceptRequest(view, request);
    }

    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        super.onReceivedError(view, errorCode, description, failingUrl);
        Map<String, Object> data = new HashMap<>();
        data.put("url", failingUrl);
        data.put("code", Integer.toString(errorCode));
        FlutterWebviewPlugin.channel.invokeMethod("onHttpError", data);
    }

    private boolean checkInvalidUrl(String url) {
        if (invalidUrlPattern == null) {
            return false;
        } else {
            Matcher matcher = invalidUrlPattern.matcher(url);
            return matcher.lookingAt();
        }
    }

    String getUrlLink(String url) {
        Uri targetUrl = Uri.parse(url);
        return targetUrl.getScheme() + "://" + targetUrl.getAuthority() + targetUrl.getPath();
    }

    void resetUrlHeaders() {
       urlHeaders = new HashMap<>();
    }

    void insertUrlHeaders(String url, Map<String, String> headers) {
        urlHeaders.put(getUrlLink(url), headers);
    }
}
