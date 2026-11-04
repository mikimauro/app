package it.ritrova.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native Android MVP: nessun WebView, nessun account, nessuna API social non autorizzata. */
public final class MainActivity extends Activity {
    private static final int PICK_FILE = 11, EXPORT_BACKUP = 12, IMPORT_BACKUP = 13;
    private static final int INK = Color.rgb(36, 51, 66);
    private static final int MUTED = Color.rgb(102, 116, 132);
    private static final int CORAL = Color.rgb(242, 110, 93);
    private static final int PAPER = Color.rgb(255, 250, 245);
    private static final int WHITE = Color.WHITE;
    private ArchiveRepository repo;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout root, stage, resultHost;
    private String screen = "home", selectedFolder = "Tutte", searchType = "Tutti", searchText = "";
    private char[] pendingPassword;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = new ArchiveRepository(this);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        render();
        if (savedInstanceState == null) acceptIncoming(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        acceptIncoming(intent);
    }

    private int d(float value) { return Math.round(getResources().getDisplayMetrics().density * value); }
    private GradientDrawable bg(int color, float radius) {
        GradientDrawable b = new GradientDrawable(); b.setColor(color); b.setCornerRadius(d(radius)); return b;
    }
    private GradientDrawable outline(int color, float radius) {
        GradientDrawable b = bg(color, radius); b.setStroke(d(1), Color.rgb(233, 232, 227)); return b;
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private TextView label(String str, int sp, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(str); v.setTextColor(color); v.setTextSize(sp);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }
    private LinearLayout.LayoutParams match(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin=d(top); return p;
    }
    private void pad(View v, int h, int vert) { v.setPadding(d(h), d(vert), d(h), d(vert)); }

    private TextView pill(String txt, int background, int foreground) {
        TextView t = label(txt, 12, foreground, true);
        pad(t, 13, 9); t.setGravity(Gravity.CENTER); t.setBackground(bg(background, 22)); return t;
    }
    private TextView button(String text, int color, int foreground) {
        TextView v = label(text, 15, foreground, true);
        v.setGravity(Gravity.CENTER); pad(v, 16, 16); v.setBackground(bg(color, 16));
        v.setMinHeight(d(52)); return v;
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    private void render() {
        root = column(); root.setBackgroundColor(PAPER); root.setFocusableInTouchMode(true);
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets sys = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(0, sys.top, 0, sys.bottom);
                return insets;
            });
            root.requestApplyInsets();
        }
        LinearLayout header = row();
        header.setPadding(d(22), d(21), d(22), d(15));
        TextView mark = label("✦", 23, WHITE, true); mark.setGravity(Gravity.CENTER);
        mark.setBackground(bg(CORAL, 12));
        header.addView(mark, new LinearLayout.LayoutParams(d(39), d(39)));
        TextView name = label("Ritrova", 24, INK, true);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1); np.leftMargin = d(10);
        header.addView(name, np);
        TextView gear = label("⚙", 24, INK, false); gear.setContentDescription("Impostazioni e backup");
        gear.setGravity(Gravity.CENTER); gear.setBackground(outline(WHITE, 20));
        header.addView(gear, new LinearLayout.LayoutParams(d(42), d(42)));
        gear.setOnClickListener(v -> settings());
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        stage = column(); stage.setPadding(d(20), d(10), d(20), d(24));
        scroll.addView(stage, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (screen.equals("home")) home();
        else if (screen.equals("search")) search();
        else archive();
        navigation();
    }

    private void go(String route) {
        screen = route;
        render();
    }

    private void home() {
        stage.addView(label("LA TUA MEMORIA, IN TASCA", 12, MUTED, true), match(1));
        TextView hero = label("Salva oggi.\nRitrova domani.", 35, INK, true);
        hero.setLetterSpacing(-0.045f); stage.addView(hero, match(12));
        TextView desc = label("Foto, messaggi, video, documenti e link.\nTutto al suo posto, senza complicazioni.", 15, MUTED, false);
        desc.setLineSpacing(d(4), 1f); stage.addView(desc, match(13));
        TextView privacy = pill("●  Privato e disponibile anche offline", Color.rgb(220, 247, 235), Color.rgb(25, 118, 93));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-2, -2); pp.topMargin=d(18); stage.addView(privacy, pp);

        LinearLayout actions = row();
        cardAction(actions, "＋", "Salva", "Un contenuto", Color.rgb(255, 218, 207), this::saveMenu);
        cardAction(actions, "⌕", "Cerca", "Trova subito", Color.rgb(228, 222, 250), () -> go("search"));
        cardAction(actions, "▤", "Archivio", "Le tue cartelle", Color.rgb(212, 242, 232), () -> go("archive"));
        stage.addView(actions, match(26));

        LinearLayout tip = column(); tip.setBackground(bg(Color.rgb(255, 240, 227), 20)); pad(tip, 18, 17);
        tip.addView(label("💡  Un gesto e hai salvato", 15, INK, true));
        TextView t = label("Da WhatsApp, TikTok, Instagram o dal browser: premi «Condividi» e scegli Ritrova. Se arriva solo un link, salviamo il link.", 13, Color.rgb(129, 99, 76), false);
        t.setLineSpacing(d(3), 1f); tip.addView(t, match(7)); stage.addView(tip, match(22));

        LinearLayout latestHeader = row();
        latestHeader.addView(label("Salvati di recente", 20, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView see = label("Vedi tutti  ›", 13, CORAL, true); latestHeader.addView(see);
        see.setOnClickListener(v -> go("archive")); stage.addView(latestHeader, match(26));
        addItems(stage, repo.db.list(null, null, null, 7));
    }

    private void cardAction(LinearLayout parent, String symbol, String title, String subtitle, int color, Runnable action) {
        LinearLayout card = column(); card.setBackground(bg(color, 20));
        card.setPadding(d(12), d(16), d(9), d(13));
        TextView icon = label(symbol, 29, INK, false); card.addView(icon);
        card.addView(label(title, 17, INK, true), match(24));
        card.addView(label(subtitle, 10, Color.rgb(85, 99, 112), false), match(3));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, d(145), 1);
        p.rightMargin = d(7); parent.addView(card, p);
        card.setOnClickListener(v -> action.run()); card.setContentDescription(title + ": " + subtitle);
    }

    private void search() {
        stage.addView(label("Cerca", 33, INK, true), match(1));
        stage.addView(label("Non devi ricordare dove hai salvato qualcosa.", 14, MUTED, false), match(6));
        EditText input = new EditText(this);
        input.setSingleLine(true); input.setTextSize(16); input.setTextColor(INK); input.setHint("Es. ricetta, visita, video...");
        input.setBackground(outline(WHITE, 16)); pad(input, 15, 14);
        stage.addView(input, match(22)); input.setText(searchText); input.setSelection(input.length());
        HorizontalScrollView h = new HorizontalScrollView(this); h.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = row();
        for (String typ : new String[]{"Tutti", "link", "file", "note"}) {
            String txt = typ.equals("link") ? "Link" : typ.equals("file") ? "File" : typ.equals("note") ? "Note" : "Tutti";
            TextView chip = pill(txt, typ.equals(searchType) ? INK : WHITE, typ.equals(searchType) ? WHITE : MUTED);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2); cp.rightMargin = d(8); chips.addView(chip, cp);
            chip.setOnClickListener(v -> { searchType=typ; searchText=input.getText().toString(); render(); });
        }
        h.addView(chips); stage.addView(h, match(14));
        resultHost = column(); stage.addView(resultHost, match(12));
        fillSearch();
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { searchText=s.toString(); fillSearch(); }
            @Override public void afterTextChanged(Editable e) { }
        });
        stage.addView(label("La ricerca iniziale trova nomi, note, link e il testo dei piccoli file TXT. La ricerca AI nei video e nei PDF arriverà in una versione successiva.", 12, MUTED, false), match(18));
    }
    private void fillSearch() {
        if (resultHost == null || !screen.equals("search")) return;
        resultHost.removeAllViews();
        List<SavedItem> items = repo.db.list(null, searchText, searchType, 200);
        resultHost.addView(label(items.size() + (items.size()==1 ? " risultato" : " risultati") + (items.size()==200 ? " (primi 200)" : ""), 12, MUTED, true));
        addItems(resultHost, items);
    }

    private void archive() {
        LinearLayout title = row(); title.addView(label("Archivio", 33, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView plus = pill("＋ Cartella", Color.rgb(222, 245, 234), Color.rgb(25, 118, 93));
        title.addView(plus); plus.setOnClickListener(v -> createFolder()); stage.addView(title, match(1));
        stage.addView(label("I tuoi contenuti, organizzati come vuoi tu.", 14, MUTED, false), match(7));
        HorizontalScrollView hsv = new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout folders = row();
        List<String> names = new ArrayList<>(); names.add("Tutte"); names.addAll(repo.db.folders());
        for (String f : names) {
            TextView chip = pill(f, f.equals(selectedFolder) ? INK : WHITE, f.equals(selectedFolder) ? WHITE : MUTED);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2); cp.rightMargin = d(8);
            folders.addView(chip, cp); chip.setOnClickListener(v -> { selectedFolder=f; render(); });
        }
        hsv.addView(folders); stage.addView(hsv, match(20));
        List<SavedItem> items = repo.db.list(selectedFolder, null, null, 200);
        stage.addView(label(items.size() + " contenuti" + (items.size()==200 ? " (primi 200)" : ""), 12, MUTED, true), match(19));
        addItems(stage, items);
    }

    private void addItems(LinearLayout container, List<SavedItem> items) {
        if (items.isEmpty()) {
            LinearLayout empty = column(); empty.setGravity(Gravity.CENTER); pad(empty, 17, 32);
            empty.setBackground(outline(WHITE, 19));
            TextView icon = label("♡", 35, CORAL, false); icon.setGravity(Gravity.CENTER); empty.addView(icon);
            TextView title = label("Qui c'è spazio per le tue cose", 15, INK, true); title.setGravity(Gravity.CENTER); empty.addView(title, match(8));
            TextView desc = label("Premi Salva oppure Condividi da un'altra app.", 12, MUTED, false);
            desc.setGravity(Gravity.CENTER); empty.addView(desc, match(4)); container.addView(empty, match(15)); return;
        }
        for (SavedItem i : items) {
            LinearLayout card = row(); card.setBackground(outline(WHITE, 18)); pad(card, 13, 13);
            String symbol = i.type.equals("file") ? "▣" : i.type.equals("link") ? "↗" : "✎";
            int tint = i.type.equals("file") ? Color.rgb(238, 231, 255) : i.type.equals("link") ? Color.rgb(219, 239, 252) : Color.rgb(255, 240, 207);
            TextView icon = label(symbol, 24, INK, false); icon.setGravity(Gravity.CENTER); icon.setBackground(bg(tint, 14));
            card.addView(icon, new LinearLayout.LayoutParams(d(48), d(48)));
            LinearLayout col = column(); LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1); cp.leftMargin=d(12);
            card.addView(col, cp);
            TextView title=label(i.title, 15, INK, true); title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END); col.addView(title);
            String date = DateFormat.getDateInstance(DateFormat.MEDIUM, java.util.Locale.ITALY).format(new Date(i.createdAt));
            String desc=i.folder + " · " + date + (i.type.equals("link") ? " · Solo link" : i.type.equals("file") ? " · File offline" : " · Nota");
            TextView meta=label(desc, 11, MUTED, false); meta.setSingleLine(true); meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(meta, match(6));
            card.addView(label("›", 28, MUTED, false));
            container.addView(card, match(10));
            card.setOnClickListener(v -> itemActions(i));
            card.setContentDescription(i.title + ", " + desc + ". Tocca per aprire le azioni.");
        }
    }

    private void navigation() {
        LinearLayout nav = row(); nav.setPadding(d(12), d(9), d(12), d(10));
        nav.setBackgroundColor(WHITE);
        navItem(nav, "⌂", "Inizio", "home", () -> go("home"));
        navItem(nav, "⌕", "Cerca", "search", () -> go("search"));
        navItem(nav, "▤", "Archivio", "archive", () -> go("archive"));
        navItem(nav, "＋", "Salva", "save", this::saveMenu);
        root.addView(nav, new LinearLayout.LayoutParams(-1, -2));
    }
    private void navItem(LinearLayout nav, String icon, String name, String route, Runnable runnable) {
        LinearLayout c = column(); c.setGravity(Gravity.CENTER);
        int color=screen.equals(route) || route.equals("save") ? CORAL : MUTED;
        TextView i=label(icon, 25, color, true); i.setGravity(Gravity.CENTER); c.addView(i);
        TextView t=label(name, 11, color, true); t.setGravity(Gravity.CENTER); c.addView(t);
        nav.addView(c, new LinearLayout.LayoutParams(0, d(57), 1));
        c.setOnClickListener(v -> runnable.run()); c.setContentDescription(name);
    }

    private void saveMenu() {
        String[] choices={"✎  Scrivi una nota", "↗  Salva un link", "▣  Scegli un file dal telefono"};
        new AlertDialog.Builder(this).setTitle("Cosa vuoi salvare?").setItems(choices, (dlg, which) -> {
            if (which == 0) textDialog(false);
            if (which == 1) textDialog(true);
            if (which == 2) {
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE); pick.setType("*/*");
                startActivityForResult(pick, PICK_FILE);
            }
        }).setNegativeButton("Annulla", null).show();
    }

    private void textDialog(boolean link) {
        EditText input = new EditText(this); input.setTextSize(16);
        input.setHint(link ? "https://esempio.it/…" : "Scrivi qui la tua nota…");
        if (!link) { input.setMinLines(3); input.setGravity(Gravity.TOP); }
        input.setInputType(link ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        LinearLayout wrap = column(); pad(wrap, 18, 5); wrap.addView(input);
        new AlertDialog.Builder(this).setTitle(link ? "Salva un collegamento" : "Nuova nota")
                .setView(wrap).setNegativeButton("Annulla", null)
                .setPositiveButton("Salva", (dialog, which) -> {
                    try { repo.saveText(input.getText().toString(), null); toast("Salvato in Da organizzare ✓"); render(); }
                    catch (Exception e) { toast("Non salvato: " + e.getMessage()); }
                }).show();
    }

    private void createFolder() {
        EditText input = new EditText(this); input.setHint("Es. Viaggi"); input.setSingleLine(true);
        LinearLayout wrap=column(); pad(wrap, 18, 6); wrap.addView(input);
        new AlertDialog.Builder(this).setTitle("Nuova cartella").setView(wrap)
                .setNegativeButton("Annulla", null).setPositiveButton("Crea", (a,b)-> {
                    String name=input.getText().toString().trim();
                    if (name.isEmpty() || name.length()>80) { toast("Scegli un nome da 1 a 80 caratteri"); return; }
                    repo.db.addFolder(name); selectedFolder=name; render();
                }).show();
    }

    private void itemActions(SavedItem item) {
        new AlertDialog.Builder(this).setTitle(item.title)
                .setMessage(repo.kindLabel(item) + "\n" + item.folder + (item.body.isEmpty() ? "" : "\n\n" + preview(item.body)))
                .setItems(new String[]{"Apri", "Rinomina", "Sposta in una cartella", "Condividi", "Elimina"},
                        (dialog, choice) -> {
                            switch (choice) {
                                case 0: openItem(item); break;
                                case 1: rename(item); break;
                                case 2: move(item); break;
                                case 3: shareItem(item); break;
                                case 4: confirmDelete(item); break;
                            }
                        }).setNegativeButton("Chiudi", null).show();
    }
    private String preview(String s) { return s.length() > 300 ? s.substring(0,300) + "…" : s; }

    private void openItem(SavedItem item) {
        if (item.type.equals("note")) {
            new AlertDialog.Builder(this).setTitle(item.title).setMessage(item.body).setPositiveButton("OK", null).show(); return;
        }
        Intent intent=new Intent(Intent.ACTION_VIEW);
        if (item.type.equals("link")) {
            intent.setData(Uri.parse(item.url));
        } else {
            Uri file=SavedFileProvider.uriFor(item);
            intent.setDataAndType(file, item.mime==null || item.mime.isEmpty()?"application/octet-stream":item.mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newUri(getContentResolver(), item.fileName, file));
        }
        try { startActivity(intent); }
        catch (ActivityNotFoundException e) { toast("Nessuna app disponibile per aprire questo contenuto"); }
        catch (Exception e) { toast("Impossibile aprire: " + e.getMessage()); }
    }

    private void shareItem(SavedItem item) {
        Intent intent=new Intent(Intent.ACTION_SEND);
        if (item.type.equals("file")) {
            Uri file=SavedFileProvider.uriFor(item);
            intent.setType(item.mime==null || item.mime.isEmpty()?"application/octet-stream":item.mime);
            intent.putExtra(Intent.EXTRA_STREAM, file);
            intent.setClipData(ClipData.newUri(getContentResolver(), item.fileName, file));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, item.type.equals("link") ? item.url : item.body);
        }
        try { startActivity(Intent.createChooser(intent, "Condividi con…")); }
        catch (Exception e) { toast("Condivisione non disponibile"); }
    }
    private void rename(SavedItem item) {
        EditText edit=new EditText(this); edit.setText(item.title); edit.setSingleLine(true);
        LinearLayout wrap=column(); pad(wrap, 18, 5); wrap.addView(edit);
        new AlertDialog.Builder(this).setTitle("Rinomina").setView(wrap)
                .setNegativeButton("Annulla", null).setPositiveButton("Salva", (a,b) -> {
                    String title=edit.getText().toString().trim();
                    if (title.isEmpty() || title.length()>200) { toast("Nome non valido"); return; }
                    item.title=title; repo.db.update(item); render();
                }).show();
    }
    private void move(SavedItem item) {
        List<String> folders=repo.db.folders();
        new AlertDialog.Builder(this).setTitle("Sposta in…").setItems(folders.toArray(new String[0]), (d,n)-> {
            item.folder=folders.get(n); repo.db.update(item); render(); toast("Spostato in " + item.folder);
        }).setNegativeButton("Annulla", null).show();
    }
    private void confirmDelete(SavedItem item) {
        new AlertDialog.Builder(this).setTitle("Eliminare questo contenuto?")
                .setMessage("Il contenuto sarà rimosso dall'archivio locale. Recuperalo da un backup, se disponibile.")
                .setNegativeButton("Annulla", null).setPositiveButton("Elimina", (d,w)-> {
                    repo.delete(item); render(); toast("Elemento eliminato");
                }).show();
    }

    private void settings() {
        String text="Archivio sul telefono: " + repo.db.count() + " elementi.\n\n" +
                "I dati restano nello spazio privato dell'app. I backup esportati sono cifrati con password. " +
                "La memoria del telefono non è ancora cifrata separatamente dall'app: usa il blocco schermo e non installare questa beta per dati molto sensibili.\n\n" +
                "Importante: disinstallando l'app cancelli l'archivio locale. Esporta un backup e conservalo fuori dal telefono.";
        new AlertDialog.Builder(this).setTitle("Impostazioni e sicurezza").setMessage(text)
                .setPositiveButton("Esporta backup", (d,w)-> askPassword(true))
                .setNeutralButton("Ripristina backup", (d,w)-> askPassword(false))
                .setNegativeButton("Chiudi", null).show();
    }

    private void askPassword(boolean export) {
        EditText input=new EditText(this); input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(export ? "Scegli una password (almeno 8 caratteri)" : "Password del tuo backup");
        LinearLayout wrap=column(); pad(wrap,18,6); wrap.addView(input);
        new AlertDialog.Builder(this).setTitle(export?"Crea un backup cifrato":"Ripristina un backup")
                .setMessage(export ? "Conserva la password: non possiamo recuperarla. Salva il file .ritrova in un posto sicuro." : "Il ripristino aggiunge gli elementi mancanti senza cancellare quelli attuali.")
                .setView(wrap).setNegativeButton("Annulla", null).setPositiveButton("Continua", (d,w)-> {
                    char[] password=input.getText().toString().toCharArray();
                    if (password.length<8) { Arrays.fill(password,'\0'); toast("Password: almeno 8 caratteri"); return; }
                    if (pendingPassword != null) Arrays.fill(pendingPassword, '\0');
                    pendingPassword=password;
                    Intent intent;
                    if (export) {
                        intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("application/octet-stream");
                        intent.putExtra(Intent.EXTRA_TITLE, "Ritrova-backup-" + System.currentTimeMillis() + ".ritrova");
                    } else {
                        intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*");
                    }
                    startActivityForResult(intent, export ? EXPORT_BACKUP : IMPORT_BACKUP);
                }).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if ((requestCode==EXPORT_BACKUP || requestCode==IMPORT_BACKUP) && pendingPassword!=null) {
                Arrays.fill(pendingPassword, '\0'); pendingPassword=null;
            }
            return;
        }
        Uri uri=data.getData();
        if (requestCode==PICK_FILE) {
            saveUris(Arrays.asList(uri), data.getType(), "");
        } else if (requestCode==EXPORT_BACKUP || requestCode==IMPORT_BACKUP) {
            final char[] pw=pendingPassword;
            pendingPassword=null;
            if (pw==null) { toast("Ripeti l'operazione: password non disponibile"); return; }
            boolean exporting=requestCode==EXPORT_BACKUP;
            toast(exporting ? "Creazione backup in corso…" : "Ripristino in corso…");
            io.submit(() -> {
                try {
                    BackupCodec backup=new BackupCodec(this, repo);
                    if (exporting) {
                        backup.exportTo(uri,pw);
                        runOnUiThread(()-> {
                            toast("Backup cifrato esportato ✓");
                            new AlertDialog.Builder(this).setTitle("Backup creato")
                                .setMessage("Il file .ritrova è cifrato. Vuoi condividerlo ora, ad esempio tramite email?")
                                .setNegativeButton("Non ora", null)
                                .setPositiveButton("Condividi", (dialog, which) -> shareBackup(uri))
                                .show();
                        });
                    } else {
                        int imported=backup.importFrom(uri,pw);
                        runOnUiThread(()-> { render(); toast("Ripristinati " + imported + " elementi ✓"); });
                    }
                } catch (Exception e) {
                    runOnUiThread(()->toast("Operazione non riuscita: " + e.getMessage()));
                } finally { Arrays.fill(pw,'\0'); }
            });
        }
    }

    private void acceptIncoming(Intent intent) {
        if (intent==null) return;
        String action=intent.getAction();
        if (Intent.ACTION_PROCESS_TEXT.equals(action)) {
            CharSequence selected=intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            if (selected!=null && selected.length()>0) saveIncomingText(selected.toString(), null);
            return;
        }
        if (!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_SEND_MULTIPLE.equals(action)) return;
        LinkedHashSet<Uri> streams=new LinkedHashSet<>();
        try {
            if (Intent.ACTION_SEND.equals(action)) {
                Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (u!=null) streams.add(u);
            } else {
                ArrayList<Uri> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (list!=null) streams.addAll(list);
            }
        } catch (RuntimeException e) { toast("Contenuto condiviso non valido"); return; }
        CharSequence extra=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        String text=extra==null?"":extra.toString();
        // Se il social invia un link e una miniatura in ClipData, preferiamo il link.
        // Usiamo ClipData come fallback solo quando non abbiamo testo né EXTRA_STREAM.
        boolean sharedLink = text.matches("(?s).*https?://\\S+.*");
        if (streams.isEmpty() && !sharedLink && intent.getClipData()!=null) {
            for (int n=0;n<intent.getClipData().getItemCount();n++) {
                Uri u=intent.getClipData().getItemAt(n).getUri();
                if (u!=null) streams.add(u);
            }
        }
        String subject=intent.getStringExtra(Intent.EXTRA_SUBJECT);
        if (!streams.isEmpty()) {
            saveUris(new ArrayList<>(streams), intent.getType(), text);
        } else if (!text.trim().isEmpty()) {
            saveIncomingText(text, subject);
        } else {
            toast("Questa app ha condiviso un contenuto non accessibile: nessun file o link ricevuto");
        }
    }
    private void shareBackup(Uri uri) {
        Intent send=new Intent(Intent.ACTION_SEND);
        send.setType("application/octet-stream");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.setClipData(ClipData.newUri(getContentResolver(), "Backup Ritrova", uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(Intent.createChooser(send, "Invia backup cifrato con…")); }
        catch (Exception e) { toast("Condivisione non disponibile; il backup resta nel percorso scelto"); }
    }

    private void saveIncomingText(String text, String title) {
        try { repo.saveText(text, title); go("home"); toast("Salvato da Condividi ✓"); }
        catch (Exception e) { toast("Non salvato: " + e.getMessage()); }
    }
    private void saveUris(List<Uri> uris, String mime, String caption) {
        if (uris.isEmpty()) { toast("Nessun file ricevuto"); return; }
        toast("Importazione di " + uris.size() + " file in corso…");
        io.submit(() -> {
            int count=0; String error=null;
            for (Uri uri:uris) {
                try { repo.saveFile(uri,mime,caption); count++; }
                catch (Exception e) { error=e.getMessage(); }
            }
            final int total=count; final String failure=error;
            runOnUiThread(()-> { go("home"); toast(total + " file salvati" + (failure==null ? " ✓" : ". Problema: " + failure)); });
        });
    }
    @Override protected void onDestroy() {
        if (pendingPassword!=null) { Arrays.fill(pendingPassword,'\0'); pendingPassword=null; }
        io.shutdown();
        super.onDestroy();
    }
}
