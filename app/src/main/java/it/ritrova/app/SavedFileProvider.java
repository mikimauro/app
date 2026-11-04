package it.ritrova.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.UUID;

/** Read-only content provider: concede accesso ai file SOLO via intent esplicito con grant. */
public final class SavedFileProvider extends ContentProvider {
    public static Uri uriFor(SavedItem item) {
        return new Uri.Builder().scheme("content").authority("it.ritrova.app.files")
                .appendPath(item.id).build();
    }
    @Override public boolean onCreate() { return true; }

    private SavedItem resolve(Uri uri) {
        if (uri == null || uri.getPathSegments().size() != 1) return null;
        String id = uri.getLastPathSegment();
        try { UUID.fromString(id); }
        catch (Exception e) { return null; }
        return new ArchiveDb(getContext()).byId(id);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SavedItem it = resolve(uri);
        if (it == null || !"file".equals(it.type)) return null;
        String[] cols = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(cols);
        Object[] row = new Object[cols.length];
        for (int n = 0; n < cols.length; n++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[n])) row[n] = it.fileName;
            else if (OpenableColumns.SIZE.equals(cols[n])) row[n] = it.size;
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override public String getType(Uri uri) {
        SavedItem it = resolve(uri);
        return it == null || it.mime == null || it.mime.isEmpty() ? "application/octet-stream" : it.mime;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Sola lettura");
        SavedItem it = resolve(uri);
        if (it == null || !"file".equals(it.type)) throw new FileNotFoundException("Contenuto non trovato");
        File f = new ArchiveRepository(getContext()).fileFor(it);
        if (!f.isFile()) throw new FileNotFoundException("File non più presente");
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
