/*
 * Copyright (C) 2026 The Soft Braille Keyboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dalton.braillekeyboard;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Locale;

public class QuickStartActivity extends Activity {
    private static final String QUICK_START_ASSET_EN
            = "file:///android_asset/help/quick_start.html";
    private static final String QUICK_START_ASSET_PL
            = "file:///android_asset/help/quick_start_pl.html";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.quick_start_title);

        WebView webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(false);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.getSettings().setSupportZoom(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view,
                    WebResourceRequest request) {
                return openExternal(request == null ? null : request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return openExternal(url == null ? null : Uri.parse(url));
            }

            private boolean openExternal(Uri uri) {
                if (uri == null) {
                    return false;
                }
                String scheme = uri.getScheme();
                if ("file".equalsIgnoreCase(scheme)
                        || "about".equalsIgnoreCase(scheme)) {
                    return false;
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                    return true;
                }
                return false;
            }
        });
        webView.loadUrl(getQuickStartAssetUrl());
        setContentView(webView);
    }

    private String getQuickStartAssetUrl() {
        Locale locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locale = getResources().getConfiguration().getLocales().isEmpty()
                    ? Locale.getDefault()
                    : getResources().getConfiguration().getLocales().get(0);
        } else {
            locale = getResources().getConfiguration().locale;
            if (locale == null) {
                locale = Locale.getDefault();
            }
        }
        return locale != null && "pl".equalsIgnoreCase(locale.getLanguage())
                ? QUICK_START_ASSET_PL
                : QUICK_START_ASSET_EN;
    }
}
