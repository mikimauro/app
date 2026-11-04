package it.ritrova.app;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public final class SavedItem {
    public String id = UUID.randomUUID().toString();
    public String type = "note"; // link, note, file
    public String title = "";
    public String body = "";
    public String url = "";
    public String fileName = "";
    public String mime = "";
    public String folder = "Da organizzare";
    public long size = 0;
    public long createdAt = System.currentTimeMillis();

    public JSONObject toJson() throws JSONException {
        JSONObject j = new JSONObject();
        j.put("id", id);
        j.put("type", type);
        j.put("title", title);
        j.put("body", body);
        j.put("url", url);
        j.put("fileName", fileName);
        j.put("mime", mime);
        j.put("folder", folder);
        j.put("size", size);
        j.put("createdAt", createdAt);
        return j;
    }

    public static SavedItem fromJson(JSONObject j) {
        SavedItem it = new SavedItem();
        it.id = j.optString("id", it.id);
        it.type = j.optString("type", "note");
        it.title = j.optString("title", "");
        it.body = j.optString("body", "");
        it.url = j.optString("url", "");
        it.fileName = j.optString("fileName", "");
        it.mime = j.optString("mime", "");
        it.folder = j.optString("folder", "Da organizzare");
        it.size = j.optLong("size", 0);
        it.createdAt = j.optLong("createdAt", System.currentTimeMillis());
        return it;
    }
}
