package it.ritrova.app;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ArchiveRepository {
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);
    private static final long MAX_FILE = 512L * 1024 * 1024;
    public final ArchiveDb db;
    private final Context context;

    public ArchiveRepository(Context ctx) { context = ctx.getApplicationContext(); db = new ArchiveDb(context); }

    public File archiveDir() {
        File folder = new File(context.getFilesDir(), "archive");
        if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Impossibile creare archivio locale");
        return folder;
    }

    public File fileFor(SavedItem i) { return new File(archiveDir(), i.id + ".bin"); }

    public SavedItem saveText(String text, String preferredTitle) {
        String raw = text == null ? "" : text.trim();
        if (raw.isEmpty()) throw new IllegalArgumentException("Il contenuto è vuoto");
        SavedItem item = new SavedItem();
        Matcher matcher = URL.matcher(raw);
        if (matcher.find()) {
            String found = matcher.group().replaceAll("[.,;:!?)]+$", "");
            item.type = "link";
            item.url = found;
            try { item.title = new URI(found).getHost().replaceFirst("^www\\.", ""); }
            catch (Exception e) { item.title = "Collegamento salvato"; }
        } else {
            item.type = "note";
            item.title = raw.length() > 58 ? raw.substring(0, 58) + "…" : raw;
        }
        item.body = raw;
        if (preferredTitle != null && !preferredTitle.trim().isEmpty()) item.title = preferredTitle.trim();
        db.insert(item);
        return item;
    }

    public SavedItem saveFile(Uri uri, String sharedMime, String caption) throws IOException {
        if (uri == null) throw new IOException("Nessun file ricevuto");
        ContentResolver resolver = context.getContentResolver();
        String name = "file_condiviso";
        try (Cursor c = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) name = c.getString(0);
        } catch (RuntimeException ignored) { }
        if (name == null || name.trim().isEmpty()) name = "file_condiviso";
        if (name.length() > 200) name = name.substring(0, 200);
        String mime = resolver.getType(uri);
        if (mime == null || mime.isEmpty()) mime = sharedMime == null ? "application/octet-stream" : sharedMime;
        if (mime.equals("*/*")) mime = "application/octet-stream";
        SavedItem item = new SavedItem();
        item.type = "file"; item.title = name; item.fileName = name; item.mime = mime;
        item.body = caption == null ? "" : caption;
        File target = fileFor(item);
        long total = 0;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("Impossibile aprire il file condiviso");
            try (FileOutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[65536]; int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_FILE) throw new IOException("File superiore al limite di 512 MB per singolo salvataggio");
                    out.write(buffer, 0, read);
                }
            }
            if (total == 0) throw new IOException("Il file condiviso è vuoto");
            item.size = total;
            if (mime.startsWith("text/") && total < 1024 * 1024) {
                // Indicizzazione testuale dei piccoli file .txt, incluse chat esportate.
                byte[] bytes = java.nio.file.Files.readAllBytes(target.toPath());
                item.body = (caption == null ? "" : caption + "\n") + new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            }
            db.insert(item);
            return item;
        } catch (Exception ex) {
            // Nessun contenuto parziale viene lasciato dopo un errore.
            if (target.exists()) target.delete();
            if (ex instanceof IOException) throw (IOException) ex;
            throw new IOException("Impossibile salvare il file: " + ex.getMessage(), ex);
        }
    }

    public void delete(SavedItem item) {
        db.delete(item.id);
        if ("file".equals(item.type)) fileFor(item).delete();
    }

    public String kindLabel(SavedItem item) {
        if ("link".equals(item.type)) return "SOLO LINK · internet richiesto";
        if ("file".equals(item.type)) return "FILE LOCALE · disponibile offline";
        return "NOTA · disponibile offline";
    }
}
