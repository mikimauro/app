package it.ritrova.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ArchiveDb extends SQLiteOpenHelper {
    private static final String NAME = "ritrova.db";
    private static final int VERSION = 1;
    private static final String[] COLS = {"id","type","title","body","url","file_name","mime","folder","bytes","created_at"};

    public ArchiveDb(Context context) { super(context, NAME, null, VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE items (id TEXT PRIMARY KEY NOT NULL, type TEXT NOT NULL, title TEXT NOT NULL, body TEXT, url TEXT, file_name TEXT, mime TEXT, folder TEXT NOT NULL, bytes INTEGER NOT NULL, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE folders (name TEXT PRIMARY KEY NOT NULL)");
        db.execSQL("CREATE INDEX ix_items_folder ON items(folder, created_at DESC)");
        db.execSQL("CREATE INDEX ix_items_date ON items(created_at DESC)");
        for (String s : Arrays.asList("Da organizzare", "Personale", "Famiglia", "Ricette", "Documenti", "Lavoro")) {
            ContentValues cv = new ContentValues(); cv.put("name", s);
            db.insertWithOnConflict("folders", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    private static ContentValues values(SavedItem item) {
        ContentValues cv = new ContentValues();
        cv.put("id", item.id); cv.put("type", item.type); cv.put("title", item.title);
        cv.put("body", item.body); cv.put("url", item.url); cv.put("file_name", item.fileName);
        cv.put("mime", item.mime); cv.put("folder", item.folder); cv.put("bytes", item.size);
        cv.put("created_at", item.createdAt);
        return cv;
    }

    private static SavedItem item(Cursor c) {
        SavedItem i = new SavedItem();
        i.id = c.getString(0); i.type = c.getString(1); i.title = c.getString(2);
        i.body = c.getString(3); i.url = c.getString(4); i.fileName = c.getString(5);
        i.mime = c.getString(6); i.folder = c.getString(7); i.size = c.getLong(8);
        i.createdAt = c.getLong(9);
        return i;
    }

    public synchronized void insert(SavedItem item) {
        addFolder(item.folder);
        getWritableDatabase().insertOrThrow("items", null, values(item));
    }

    public synchronized boolean exists(String id) {
        try (Cursor c = getReadableDatabase().query("items", new String[]{"id"}, "id=?", new String[]{id}, null, null, null, "1")) {
            return c.moveToFirst();
        }
    }

    public synchronized SavedItem byId(String id) {
        try (Cursor c = getReadableDatabase().query("items", COLS, "id=?", new String[]{id}, null, null, null, "1")) {
            return c.moveToFirst() ? item(c) : null;
        }
    }

    public synchronized List<SavedItem> all() { return list(null, null, null, 0); }

    public synchronized List<SavedItem> list(String folder, String search, String type, int limit) {
        StringBuilder where = new StringBuilder("1=1");
        ArrayList<String> args = new ArrayList<>();
        if (folder != null && !folder.equals("Tutte")) { where.append(" AND folder=?"); args.add(folder); }
        if (type != null && !type.equals("Tutti")) { where.append(" AND type=?"); args.add(type); }
        if (search != null && !search.trim().isEmpty()) {
            where.append(" AND (title LIKE ? OR body LIKE ? OR url LIKE ? OR folder LIKE ? OR file_name LIKE ?)");
            String q = "%" + search.trim() + "%";
            for (int n=0; n<5; n++) args.add(q);
        }
        ArrayList<SavedItem> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("items", COLS, where.toString(), args.toArray(new String[0]), null, null, "created_at DESC", limit > 0 ? String.valueOf(limit) : null)) {
            while (c.moveToNext()) result.add(item(c));
        }
        return result;
    }

    public synchronized void update(SavedItem item) {
        addFolder(item.folder);
        getWritableDatabase().update("items", values(item), "id=?", new String[]{item.id});
    }

    public synchronized void delete(String id) {
        getWritableDatabase().delete("items", "id=?", new String[]{id});
    }

    public synchronized void addFolder(String name) {
        if (name == null || name.trim().isEmpty()) return;
        ContentValues cv = new ContentValues(); cv.put("name", name.trim());
        getWritableDatabase().insertWithOnConflict("folders", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public synchronized List<String> folders() {
        ArrayList<String> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("folders", new String[]{"name"}, null, null, null, null, "name COLLATE NOCASE")) {
            while (c.moveToNext()) result.add(c.getString(0));
        }
        return result;
    }

    public synchronized int count() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM items", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }
}
