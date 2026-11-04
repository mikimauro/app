package it.ritrova.app;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Backup portabile con password. Non richiede Internet o servizi cloud. */
public final class BackupCodec {
    private static final long MAX_IMPORT = 4L * 1024 * 1024 * 1024;
    private static final long MAX_FILE = 512L * 1024 * 1024;
    private final Context ctx;
    private final ArchiveRepository repo;

    public BackupCodec(Context ctx, ArchiveRepository repo) { this.ctx=ctx.getApplicationContext(); this.repo=repo; }

    public void exportTo(Uri destination, char[] password) throws Exception {
        List<SavedItem> items = repo.db.all();
        JSONArray arr = new JSONArray();
        for (SavedItem item : items) arr.put(item.toJson());
        JSONObject index = new JSONObject();
        index.put("format", 1); index.put("items", arr);
        JSONArray folders = new JSONArray();
        for (String f : repo.db.folders()) folders.put(f);
        index.put("folders", folders);
        OutputStream raw = ctx.getContentResolver().openOutputStream(destination, "w");
        if (raw == null) throw new IOException("Impossibile aprire la destinazione");
        try (OutputStream buffered = new BufferedOutputStream(raw);
             ZipOutputStream zip = new ZipOutputStream(PortableCrypto.encrypted(buffered, password))) {
            zip.putNextEntry(new ZipEntry("index.json"));
            zip.write(index.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            byte[] buf = new byte[65536];
            for (SavedItem i : items) {
                if (!"file".equals(i.type)) continue;
                File f = repo.fileFor(i);
                if (!f.isFile()) throw new IOException("File non trovato: " + i.fileName);
                zip.putNextEntry(new ZipEntry("files/" + i.id + ".bin"));
                try (InputStream in = new BufferedInputStream(new FileInputStream(f))) { copy(in, zip, buf, MAX_FILE); }
                zip.closeEntry();
            }
        }
    }

    /** Ripristino non distruttivo: mantiene i contenuti esistenti e salta gli ID già presenti. */
    public int importFrom(Uri source, char[] password) throws Exception {
        File temp = File.createTempFile("ritrova-restore-", ".zip", ctx.getCacheDir());
        try {
            InputStream raw = ctx.getContentResolver().openInputStream(source);
            if (raw == null) throw new IOException("Backup non accessibile");
            try (InputStream encrypted = new BufferedInputStream(raw);
                 InputStream plaintext = PortableCrypto.decrypted(encrypted, password);
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(temp))) {
                copy(plaintext, out, new byte[65536], MAX_IMPORT);
            } catch (Exception e) {
                throw new IOException("Password errata oppure backup danneggiato", e);
            }
            try (ZipFile zip = new ZipFile(temp)) {
                ZipEntry entry = zip.getEntry("index.json");
                if (entry == null) throw new IOException("Indice backup mancante");
                byte[] bytes;
                try (InputStream index = zip.getInputStream(entry); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    copy(index, out, new byte[8192], 24L * 1024 * 1024);
                    bytes = out.toByteArray();
                }
                JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                if (root.optInt("format") != 1) throw new IOException("Formato backup non supportato");
                JSONArray arr = root.getJSONArray("items");
                if (arr.length() > 20000) throw new IOException("Backup troppo grande: oltre 20.000 elementi");
                List<SavedItem> pending = new ArrayList<>();
                for (int n=0; n<arr.length(); n++) {
                    SavedItem item = SavedItem.fromJson(arr.getJSONObject(n));
                    if (!validUuid(item.id) || !(item.type.equals("link") || item.type.equals("note") || item.type.equals("file"))) {
                        throw new IOException("Elemento non valido nel backup");
                    }
                    if ("file".equals(item.type)) {
                        ZipEntry media = zip.getEntry("files/" + item.id + ".bin");
                        if (media == null || media.isDirectory() || media.getSize() > MAX_FILE) {
                            throw new IOException("File mancante o troppo grande nel backup");
                        }
                    }
                    pending.add(item);
                }
                int imported = 0;
                JSONArray folders = root.optJSONArray("folders");
                if (folders != null) for (int n=0;n<folders.length();n++) repo.db.addFolder(folders.optString(n));
                for (SavedItem item : pending) {
                    if (repo.db.exists(item.id)) continue;
                    File copied = null;
                    try {
                        if ("file".equals(item.type)) {
                            copied = repo.fileFor(item);
                            try (InputStream in = zip.getInputStream(zip.getEntry("files/" + item.id + ".bin"));
                                 FileOutputStream out = new FileOutputStream(copied)) {
                                long actual = copy(in, out, new byte[65536], MAX_FILE);
                                item.size = actual;
                            }
                        }
                        repo.db.insert(item);
                        imported++;
                    } catch (Exception err) {
                        if (copied != null) copied.delete();
                        throw err;
                    }
                }
                return imported;
            }
        } finally {
            if (temp.exists()) temp.delete();
        }
    }

    private static boolean validUuid(String s) {
        if (s == null || !s.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) return false;
        try { UUID.fromString(s); return true; } catch (Exception e) { return false; }
    }

    private static long copy(InputStream in, OutputStream out, byte[] buffer, long limit) throws IOException {
        long total = 0; int n;
        while ((n=in.read(buffer))!=-1) {
            total += n;
            if (total > limit) throw new IOException("Limite di dimensione superato");
            out.write(buffer, 0, n);
        }
        return total;
    }
}
