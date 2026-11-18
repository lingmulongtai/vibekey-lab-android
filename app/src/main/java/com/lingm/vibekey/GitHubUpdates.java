package com.lingm.vibekey;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import javax.net.ssl.HttpsURLConnection;

final class GitHubUpdates {
    private static final String REPO = "lingmulongtai/vibekey-lab-android";
    private static final URI FEED = URI.create("https://api.github.com/repos/" + REPO + "/releases?per_page=30");
    private static final String ASSETS = "https://github.com/" + REPO + "/releases/download/";
    private static final Set<String> DOWNLOAD_HOSTS = Set.of("github.com", "release-assets.githubusercontent.com",
            "objects.githubusercontent.com", "github-releases.githubusercontent.com");
    private static final int MAX_FEED_SIZE = 2 * 1024 * 1024;
    private static final long MAX_APK_SIZE = 100L * 1024 * 1024;

    record Release(String version, URI url, String sha256, long size) {}

    static Release check(String currentVersion) throws Exception {
        HttpsURLConnection connection = open(FEED, true);
        try (InputStream stream = connection.getInputStream()) {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int read; (read = stream.read(buffer)) != -1; ) {
                if (data.size() + read > MAX_FEED_SIZE) throw new IOException("Release feed too large");
                data.write(buffer, 0, read);
            }
            return select(new JSONArray(data.toString(StandardCharsets.UTF_8.name())), currentVersion);
        } finally {
            connection.disconnect();
        }
    }

    static Release select(JSONArray releases, String currentVersion) {
        ReleaseVersion newest = ReleaseVersion.parse(currentVersion);
        if (newest == null) throw new IllegalArgumentException("Unknown installed version");
        Release selected = null;
        for (int index = 0; index < releases.length(); index++) {
            JSONObject item = releases.optJSONObject(index);
            if (item == null || item.optBoolean("draft", true)) continue;
            String tag = item.optString("tag_name");
            if (!tag.startsWith("v")) continue;
            String versionName = tag.substring(1);
            ReleaseVersion version = ReleaseVersion.parse(versionName);
            if (version == null || version.compareTo(newest) <= 0) continue;

            JSONArray assets = item.optJSONArray("assets");
            if (assets == null) continue;
            String name = "VibeKeyLab-" + versionName + ".apk";
            String url = ASSETS + tag + "/" + name;
            for (int assetIndex = 0; assetIndex < assets.length(); assetIndex++) {
                JSONObject asset = assets.optJSONObject(assetIndex);
                if (asset == null || !name.equals(asset.optString("name"))
                        || !"uploaded".equals(asset.optString("state"))
                        || !url.equals(asset.optString("browser_download_url"))) continue;
                String digest = asset.optString("digest");
                long size = asset.optLong("size", -1);
                if (!digest.matches("sha256:[0-9a-f]{64}") || size <= 0 || size > MAX_APK_SIZE) continue;
                selected = new Release(versionName, URI.create(url), digest.substring(7), size);
                newest = version;
                break;
            }
        }
        return selected;
    }

    static void download(Release release, File output) throws Exception {
        HttpsURLConnection connection = open(release.url(), false);
        try (InputStream input = connection.getInputStream();
             FileOutputStream file = new FileOutputStream(output)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[32 * 1024];
            long size = 0;
            for (int read; (read = input.read(buffer)) != -1; ) {
                size += read;
                if (size > release.size()) throw new IOException("APK exceeds expected size");
                digest.update(buffer, 0, read);
                file.write(buffer, 0, read);
            }
            if (size != release.size() || !release.sha256().equals(hex(digest.digest()))) {
                throw new IOException("APK integrity check failed");
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value & 0xff) >>> 4, 16));
            result.append(Character.forDigit(value & 0xf, 16));
        }
        return result.toString();
    }

    private static HttpsURLConnection open(URI url, boolean feed) throws IOException {
        for (int redirect = 0; redirect <= 5; redirect++) {
            String host = url.getHost();
            if (!"https".equals(url.getScheme()) || url.getUserInfo() != null || host == null
                    || (url.getPort() != -1 && url.getPort() != 443)
                    || !(feed ? "api.github.com".equals(host) : DOWNLOAD_HOSTS.contains(host))) {
                throw new IOException("Unexpected GitHub endpoint");
            }
            HttpsURLConnection connection = (HttpsURLConnection) url.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(20_000);
            connection.setRequestProperty("User-Agent", "VibeKeyLab/" + BuildConfig.VERSION_NAME);
            connection.setRequestProperty("Accept", feed ? "application/vnd.github+json" : "application/octet-stream");
            if (feed) connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            int status;
            try {
                status = connection.getResponseCode();
            } catch (IOException exception) {
                connection.disconnect();
                throw exception;
            }
            if (status == 200) return connection;
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (location == null || (status != 301 && status != 302 && status != 303 && status != 307 && status != 308)) {
                throw new IOException("GitHub HTTP " + status);
            }
            url = url.resolve(location);
        }
        throw new IOException("Too many GitHub redirects");
    }
}
