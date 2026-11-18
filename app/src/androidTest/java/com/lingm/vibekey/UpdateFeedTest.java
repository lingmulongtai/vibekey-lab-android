package com.lingm.vibekey;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public final class UpdateFeedTest {
    private static final String SHA256 = "a".repeat(64);

    @Test public void selectsNewestCompatiblePublishedAssetIncludingPrereleases() throws Exception {
        JSONArray releases = new JSONArray()
                .put(release("0.1.5-dev", false, SHA256, "github.com"))
                .put(release("0.1.9", true, SHA256, "github.com"))
                .put(release("0.1.8", false, SHA256, "example.com"))
                .put(release("0.1.7", false, "broken", "github.com"))
                .put(release("0.1.6-dev", false, SHA256, "github.com"))
                .put(release("0.1.3-dev", false, SHA256, "github.com"));

        GitHubUpdates.Release selected = GitHubUpdates.select(releases, "0.1.4-dev");
        assertNotNull(selected);
        assertEquals("0.1.6-dev", selected.version());
        assertEquals(SHA256, selected.sha256());
        assertEquals(800L, selected.size());
        assertNull(GitHubUpdates.select(releases, "0.1.6-dev"));
    }

    @Test public void rejectsMissingAssetsAndZeroSize() throws Exception {
        JSONObject empty = release("0.1.5-dev", false, SHA256, "github.com");
        empty.getJSONArray("assets").getJSONObject(0).put("size", 0);
        assertNull(GitHubUpdates.select(new JSONArray().put(empty).put(JSONObject.NULL), "0.1.4-dev"));
    }

    private JSONObject release(String version, boolean draft, String digest, String host) throws Exception {
        String apk = "VibeKeyLab-" + version + ".apk";
        JSONObject asset = new JSONObject().put("name", apk).put("digest", "sha256:" + digest)
                .put("state", "uploaded").put("size", 800)
                .put("browser_download_url", "https://" + host
                        + "/lingmulongtai/vibekey-lab-android/releases/download/v" + version + "/" + apk);
        return new JSONObject().put("draft", draft).put("prerelease", version.contains("-dev"))
                .put("tag_name", "v" + version).put("assets", new JSONArray().put(asset));
    }
}
