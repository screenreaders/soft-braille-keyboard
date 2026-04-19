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

import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class GitHubReleaseChecker {
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 15000;

    private GitHubReleaseChecker() {
    }

    static final class RepoInfo {
        final String owner;
        final String repo;
        final String htmlUrl;
        final String releasesUrl;

        RepoInfo(String owner, String repo) {
            this.owner = owner;
            this.repo = repo;
            this.htmlUrl = "https://github.com/" + owner + "/" + repo;
            this.releasesUrl = htmlUrl + "/releases";
        }
    }

    static final class ReleaseInfo {
        final String tagName;
        final String name;
        final String body;
        final String htmlUrl;
        final String apkUrl;

        ReleaseInfo(String tagName, String name, String body, String htmlUrl,
                String apkUrl) {
            this.tagName = tagName;
            this.name = name;
            this.body = body;
            this.htmlUrl = htmlUrl;
            this.apkUrl = apkUrl;
        }

        String getDisplayVersion() {
            if (!TextUtils.isEmpty(tagName)) {
                return tagName;
            }
            if (!TextUtils.isEmpty(name)) {
                return name;
            }
            return "";
        }
    }

    static RepoInfo parseRepoInfo(String projectUrl) {
        if (TextUtils.isEmpty(projectUrl)) {
            return null;
        }
        Uri uri = Uri.parse(projectUrl.trim());
        if (uri == null || uri.getHost() == null) {
            return null;
        }
        String host = uri.getHost().toLowerCase(Locale.US);
        if (!"github.com".equals(host) && !"www.github.com".equals(host)) {
            return null;
        }
        List<String> segments = uri.getPathSegments();
        if (segments == null || segments.size() < 2) {
            return null;
        }
        String owner = segments.get(0);
        String repo = segments.get(1);
        if (TextUtils.isEmpty(owner) || TextUtils.isEmpty(repo)) {
            return null;
        }
        if (repo.endsWith(".git")) {
            repo = repo.substring(0, repo.length() - 4);
        }
        if (TextUtils.isEmpty(repo)) {
            return null;
        }
        return new RepoInfo(owner, repo);
    }

    static ReleaseInfo fetchLatestRelease(String projectUrl)
            throws IOException, JSONException {
        RepoInfo repoInfo = parseRepoInfo(projectUrl);
        if (repoInfo == null) {
            throw new IOException("Invalid GitHub repository URL.");
        }
        return fetchLatestRelease(repoInfo);
    }

    static ReleaseInfo fetchLatestRelease(RepoInfo repoInfo)
            throws IOException, JSONException {
        if (repoInfo == null) {
            throw new IOException("Invalid GitHub repository URL.");
        }
        String apiUrl = "https://api.github.com/repos/" + repoInfo.owner + "/"
                + repoInfo.repo + "/releases/latest";
        HttpURLConnection connection = null;
        InputStream stream = null;
        try {
            connection = (HttpURLConnection) new URL(apiUrl).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept",
                    "application/vnd.github+json");
            connection.setRequestProperty("User-Agent",
                    "SoftBrailleKeyboard/" + BuildConfig.VERSION_NAME);
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                return null;
            }
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("GitHub API returned " + responseCode);
            }
            stream = connection.getInputStream();
            JSONObject object = new JSONObject(readFully(stream));
            String tagName = object.optString("tag_name");
            String name = object.optString("name");
            String body = object.optString("body");
            String htmlUrl = object.optString("html_url");
            if (TextUtils.isEmpty(htmlUrl)) {
                htmlUrl = repoInfo.releasesUrl;
            }
            return new ReleaseInfo(tagName, name, body, htmlUrl,
                    findApkUrl(object.optJSONArray("assets")));
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    // Ignore close failure.
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static boolean isNewerThanInstalled(ReleaseInfo releaseInfo,
            String installedVersionName) {
        if (releaseInfo == null) {
            return false;
        }
        return compareVersions(releaseInfo.getDisplayVersion(),
                installedVersionName) > 0;
    }

    static String buildApkFileName(String appName, String versionLabel) {
        String safeName = sanitizeForFilename(appName);
        String safeVersion = sanitizeForFilename(versionLabel);
        if (TextUtils.isEmpty(safeVersion)) {
            safeVersion = "latest";
        }
        return safeName + "-" + safeVersion + ".apk";
    }

    private static String findApkUrl(JSONArray assets) {
        if (assets == null) {
            return null;
        }
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) {
                continue;
            }
            String contentType = asset.optString("content_type");
            String name = asset.optString("name");
            String downloadUrl = asset.optString("browser_download_url");
            boolean looksLikeApk = !TextUtils.isEmpty(name)
                    && name.toLowerCase(Locale.US).endsWith(".apk");
            boolean isApkContentType
                    = "application/vnd.android.package-archive".equals(
                            contentType);
            if ((looksLikeApk || isApkContentType)
                    && !TextUtils.isEmpty(downloadUrl)) {
                return downloadUrl;
            }
        }
        return null;
    }

    private static String readFully(InputStream stream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line).append('\n');
        }
        return builder.toString();
    }

    private static String sanitizeForFilename(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        String sanitized = value.replaceAll("[^A-Za-z0-9._-]+", "-");
        sanitized = sanitized.replaceAll("-{2,}", "-");
        return sanitized.replaceAll("(^[-.]+|[-.]+$)", "");
    }

    private static int compareVersions(String left, String right) {
        List<String> leftTokens = tokenizeVersion(left);
        List<String> rightTokens = tokenizeVersion(right);
        int max = Math.max(leftTokens.size(), rightTokens.size());
        for (int i = 0; i < max; i++) {
            String leftToken = i < leftTokens.size() ? leftTokens.get(i) : null;
            String rightToken = i < rightTokens.size() ? rightTokens.get(i) : null;
            if (TextUtils.equals(leftToken, rightToken)) {
                continue;
            }
            if (leftToken == null) {
                return remainingTokenBias(rightTokens, i) * -1;
            }
            if (rightToken == null) {
                return remainingTokenBias(leftTokens, i);
            }
            boolean leftNumeric = isNumeric(leftToken);
            boolean rightNumeric = isNumeric(rightToken);
            if (leftNumeric && rightNumeric) {
                long leftValue = Long.parseLong(leftToken);
                long rightValue = Long.parseLong(rightToken);
                if (leftValue != rightValue) {
                    return leftValue > rightValue ? 1 : -1;
                }
                continue;
            }
            if (leftNumeric != rightNumeric) {
                return leftNumeric ? 1 : -1;
            }
            int lexical = leftToken.compareToIgnoreCase(rightToken);
            if (lexical != 0) {
                return lexical > 0 ? 1 : -1;
            }
        }
        return 0;
    }

    private static List<String> tokenizeVersion(String version) {
        List<String> tokens = new ArrayList<String>();
        if (TextUtils.isEmpty(version)) {
            return tokens;
        }
        String normalized = version.trim();
        while (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        String[] rawTokens = normalized.split("[^A-Za-z0-9]+");
        for (String token : rawTokens) {
            if (!TextUtils.isEmpty(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static boolean isNumeric(String token) {
        return !TextUtils.isEmpty(token) && token.matches("\\d+");
    }

    private static int remainingTokenBias(List<String> tokens, int startIndex) {
        boolean sawText = false;
        for (int i = startIndex; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (TextUtils.isEmpty(token)) {
                continue;
            }
            if (isNumeric(token)) {
                if (Long.parseLong(token) > 0) {
                    return 1;
                }
            } else {
                sawText = true;
            }
        }
        return sawText ? -1 : 0;
    }
}
