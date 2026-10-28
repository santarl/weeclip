package io.rfsj.weeclip;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Saved clips list. Tap the title to switch group, tap a clip to open it, long-press for more. */
public class M extends Activity {
    static final int BG = 0xFF000000, FG = 0xFFFFFFFF, DIM = 0xFF9E9E9E;
    static final int MP = ViewGroup.LayoutParams.MATCH_PARENT, WC = ViewGroup.LayoutParams.WRAP_CONTENT;

    static final class H { ImageView i; TextView t, s; }

    String flt; // null = all, "" = inbox, otherwise a group id
    List<D.Clip> items = new ArrayList<>();
    List<D.Grp> grps = new ArrayList<>();
    TextView head;
    ListView lv;
    BaseAdapter ad;
    final LruCache<String, Bitmap> cache = new LruCache<>(48);

    int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, i) -> {
            v.setPadding(i.getSystemWindowInsetLeft(), i.getSystemWindowInsetTop(),
                    i.getSystemWindowInsetRight(), i.getSystemWindowInsetBottom());
            return i;
        });

        head = new TextView(this);
        head.setTextColor(FG);
        head.setTextSize(22);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setPadding(dp(16), dp(14), dp(16), dp(14));
        head.setOnClickListener(v -> pickFilter());
        head.setOnLongClickListener(v -> groupMenu());
        root.addView(head, new LinearLayout.LayoutParams(MP, WC));

        ad = new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int i) { return items.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup p) {
                if (v == null) v = row();
                bind((H) v.getTag(), items.get(i));
                return v;
            }
        };
        lv = new ListView(this);
        lv.setDivider(null);
        lv.setAdapter(ad);
        lv.setOnItemClickListener((p, v, i, id) -> open(items.get(i)));
        lv.setOnItemLongClickListener((p, v, i, id) -> { menu(items.get(i)); return true; });

        TextView empty = new TextView(this);
        empty.setText("Nothing saved yet.\n\nTap the title above to create a group, then share a Short or Reel to it.");
        empty.setTextColor(DIM);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(32), dp(32), dp(32), dp(32));

        FrameLayout fl = new FrameLayout(this);
        fl.addView(lv, new FrameLayout.LayoutParams(MP, MP));
        fl.addView(empty, new FrameLayout.LayoutParams(MP, MP));
        lv.setEmptyView(empty);
        root.addView(fl, new LinearLayout.LayoutParams(MP, 0, 1f));
        setContentView(root);

        applyIntent(getIntent());
        K.publish(this);
    }

    void applyIntent(Intent i) {
        String g = i == null ? null : i.getStringExtra("g");
        if (g != null) flt = g;
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        applyIntent(i);
    }

    @Override protected void onResume() {
        super.onResume();
        D.ui = () -> runOnUiThread(this::refresh);
        refresh();
        F.go(this);
    }

    @Override protected void onPause() {
        D.ui = null;
        super.onPause();
    }

    void refresh() {
        grps = D.groups(this);
        if (flt != null && !flt.isEmpty() && D.nameIn(grps, flt) == null) flt = null;
        List<D.Clip> l = new ArrayList<>();
        for (D.Clip k : D.clips(this)) if (flt == null || flt.equals(k.g)) l.add(k);
        items = l;
        head.setText((flt == null ? "All" : flt.isEmpty() ? "Inbox" : D.nameIn(grps, flt)) + " \u25BE");
        ad.notifyDataSetChanged();
    }

    // ---- rows ----
    View row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(12), dp(6), dp(12), dp(6));
        H h = new H();
        h.i = new ImageView(this);
        h.i.setScaleType(ImageView.ScaleType.CENTER_CROP);
        h.i.setBackgroundColor(0xFF1E1E1E);
        r.addView(h.i, new LinearLayout.LayoutParams(dp(72), dp(96)));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), 0, 0, 0);
        h.t = new TextView(this);
        h.t.setTextColor(FG);
        h.t.setTextSize(15);
        h.t.setMaxLines(3);
        h.t.setEllipsize(TextUtils.TruncateAt.END);
        h.s = new TextView(this);
        h.s.setTextColor(DIM);
        h.s.setTextSize(12);
        h.s.setPadding(0, dp(4), 0, 0);
        h.s.setMaxLines(2);
        h.s.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(h.t);
        c.addView(h.s);
        r.addView(c, new LinearLayout.LayoutParams(0, WC, 1f));
        r.setTag(h);
        return r;
    }

    void bind(H h, D.Clip k) {
        h.t.setText(k.ok ? k.title : k.url);
        StringBuilder s = new StringBuilder();
        if (!k.by.isEmpty()) s.append(k.by).append(" \u00B7 ");
        s.append(F.host(k.url)).append(" \u00B7 ")
                .append(DateUtils.getRelativeTimeSpanString(k.t, System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE));
        if (flt == null && !k.g.isEmpty()) {
            String gn = D.nameIn(grps, k.g);
            if (gn != null) s.append(" \u00B7 ").append(gn);
        }
        if (!k.ok) s.append(" \u00B7 fetching");
        h.s.setText(s);
        Bitmap bm = cache.get(k.id);
        if (bm == null && k.ok) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 2;
            bm = BitmapFactory.decodeFile(D.th(this, k.id).getPath(), o);
            if (bm != null) cache.put(k.id, bm);
        }
        h.i.setImageBitmap(bm);
    }

    // ---- actions ----
    void open(D.Clip k) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(k.url)));
        } catch (Exception e) {
            Toast.makeText(this, "No app can open this", Toast.LENGTH_SHORT).show();
        }
    }

    void menu(D.Clip k) {
        new AlertDialog.Builder(this).setItems(new String[]{"Copy link", "Move to\u2026", "Delete"}, (d, w) -> {
            if (w == 0) {
                getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("link", k.url));
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
            } else if (w == 1) {
                moveTo(k);
            } else {
                D.delete(this, k.id);
                cache.remove(k.id);
                refresh();
            }
        }).show();
    }

    void moveTo(D.Clip k) {
        List<String> n = new ArrayList<>();
        n.add("Inbox");
        for (D.Grp g : grps) n.add(g.name);
        new AlertDialog.Builder(this).setItems(n.toArray(new String[0]), (d, w) -> {
            D.move(this, k.id, w == 0 ? "" : grps.get(w - 1).id);
            refresh();
        }).show();
    }

    void pickFilter() {
        List<String> n = new ArrayList<>();
        n.add("All");
        n.add("Inbox");
        for (D.Grp g : grps) n.add(g.name);
        n.add("+ New group");
        n.add("WhatsApp relay\u2026");
        new AlertDialog.Builder(this).setItems(n.toArray(new String[0]), (d, w) -> {
            if (w == 0) flt = null;
            else if (w == 1) flt = "";
            else if (w <= grps.size() + 1) flt = grps.get(w - 2).id;
            else if (w == grps.size() + 2) { newGroup(); return; }
            else { startActivity(new Intent(this, W.class)); return; }
            refresh();
        }).show();
    }

    void newGroup() {
        ask("New group", "", s -> {
            D.Grp g = D.addGroup(this, s);
            K.publish(this);
            flt = g.id;
            refresh();
        });
    }

    boolean groupMenu() {
        if (flt == null || flt.isEmpty()) return false;
        final String id = flt;
        final String nm = D.nameIn(grps, id);
        if (nm == null) return false;
        new AlertDialog.Builder(this).setItems(new String[]{"Rename group", "Delete group (clips go to Inbox)"}, (d, w) -> {
            if (w == 0) {
                ask("Rename group", nm, s -> { D.renameGroup(this, id, s); K.publish(this); refresh(); });
            } else {
                D.deleteGroup(this, id);
                K.publish(this);
                flt = null;
                refresh();
            }
        }).show();
        return true;
    }

    void ask(String title, String init, Consumer<String> ok) {
        EditText e = new EditText(this);
        e.setSingleLine();
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setText(init);
        e.setSelection(init.length());
        new AlertDialog.Builder(this).setTitle(title).setView(e)
                .setPositiveButton("OK", (d, w) -> {
                    String s = D.clean(e.getText().toString());
                    if (!s.isEmpty()) ok.accept(s.length() > 20 ? s.substring(0, 20) : s);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
