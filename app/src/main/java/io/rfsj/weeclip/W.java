package io.rfsj.weeclip;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WhatsApp relay screen: chats captured by the listener plus the ones enabled as share-sheet contacts.
 * Tap a chat for actions, long-press for details.
 */
public class W extends Activity {
    static final int BG = 0xFF000000, FG = 0xFFFFFFFF, DIM = 0xFF9E9E9E, WARN = 0xFFFFB74D;
    static final int MP = ViewGroup.LayoutParams.MATCH_PARENT, WC = ViewGroup.LayoutParams.WRAP_CONTENT;

    static final class R { String chat, title; N.Cap cap; D.Wa wa; }

    List<R> items = new ArrayList<>();
    TextView status;
    BaseAdapter ad;

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

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(14), dp(16), dp(6));
        TextView h = new TextView(this);
        h.setText("WhatsApp relay");
        h.setTextColor(FG);
        h.setTextSize(22);
        bar.addView(h, new LinearLayout.LayoutParams(0, WC, 1f));
        TextView log = new TextView(this);
        log.setText("Log");
        log.setTextColor(DIM);
        log.setTextSize(16);
        log.setPadding(dp(12), dp(6), 0, dp(6));
        log.setOnClickListener(v -> showLog());
        bar.addView(log);
        root.addView(bar, new LinearLayout.LayoutParams(MP, WC));

        status = new TextView(this);
        status.setTextSize(13);
        status.setPadding(dp(16), 0, dp(16), dp(10));
        status.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
            catch (Exception e) { Toast.makeText(this, "Open Settings > Notification access", Toast.LENGTH_LONG).show(); }
        });
        root.addView(status, new LinearLayout.LayoutParams(MP, WC));

        ad = new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int i) { return items.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup p) {
                if (v == null) {
                    LinearLayout r = new LinearLayout(W.this);
                    r.setOrientation(LinearLayout.VERTICAL);
                    r.setPadding(dp(16), dp(10), dp(16), dp(10));
                    TextView t = new TextView(W.this);
                    t.setTextColor(FG);
                    t.setTextSize(16);
                    TextView s = new TextView(W.this);
                    s.setTextSize(12);
                    r.addView(t);
                    r.addView(s);
                    r.setTag(new TextView[]{t, s});
                    v = r;
                }
                TextView[] tv = (TextView[]) v.getTag();
                R r = items.get(i);
                tv[0].setText(r.title);
                StringBuilder s = new StringBuilder();
                if (r.wa != null) s.append("IN SHARE SHEET \u00B7 ");
                if (r.cap == null) {
                    s.append("no live action");
                } else {
                    if (r.cap.dead) s.append("CANCELED \u00B7 ");
                    s.append("captured ").append(DateUtils.getRelativeTimeSpanString(r.cap.t, System.currentTimeMillis(),
                            DateUtils.SECOND_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE))
                            .append(" \u00B7 notification ").append(r.cap.live ? "visible" : "gone");
                }
                if (r.wa != null && r.wa.save) s.append(" \u00B7 also saves clip");
                tv[1].setText(s);
                tv[1].setTextColor((r.cap == null && r.wa != null) || (r.cap != null && r.cap.dead) ? WARN : DIM);
                return v;
            }
        };
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setAdapter(ad);
        lv.setOnItemClickListener((p, v, i, id) -> menu(items.get(i)));
        lv.setOnItemLongClickListener((p, v, i, id) -> { details(items.get(i)); return true; });

        TextView empty = new TextView(this);
        empty.setText("No chats captured yet.\n\nTurn on notification access above, then receive a WhatsApp message. Chats with a visible notification are picked up right away. Tap a chat to add it to the share sheet.");
        empty.setTextColor(DIM);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(32), dp(32), dp(32), dp(32));
        FrameLayout fl = new FrameLayout(this);
        fl.addView(lv, new FrameLayout.LayoutParams(MP, MP));
        fl.addView(empty, new FrameLayout.LayoutParams(MP, MP));
        lv.setEmptyView(empty);
        root.addView(fl, new LinearLayout.LayoutParams(MP, 0, 1f));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        N.ui = () -> runOnUiThread(this::refresh);
        refresh();
    }

    @Override protected void onPause() {
        N.ui = null;
        super.onPause();
    }

    boolean granted() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(getPackageName() + "/");
    }

    void refresh() {
        Map<String, R> m = new LinkedHashMap<>();
        for (D.Wa w : D.was(this)) {
            R r = new R();
            r.chat = w.chat; r.title = w.name; r.wa = w;
            m.put(w.chat, r);
        }
        for (N.Cap k : N.CAP.values()) {
            R r = m.get(k.id);
            if (r == null) { r = new R(); r.chat = k.id; r.title = k.title; m.put(k.id, r); }
            r.cap = k;
        }
        List<R> l = new ArrayList<>(m.values());
        Collections.sort(l, (a, b) -> {
            if ((a.wa != null) != (b.wa != null)) return a.wa != null ? -1 : 1;
            return Long.compare(b.cap == null ? 0 : b.cap.t, a.cap == null ? 0 : a.cap.t);
        });
        items = l;
        boolean g = granted();
        status.setText(!g ? "Notification access is OFF \u00B7 tap to open settings"
                : N.ready ? "Notification access ON \u00B7 listener connected"
                : "Notification access ON \u00B7 listener not connected yet (tap to open settings)");
        status.setTextColor(g && N.ready ? DIM : WARN);
        ad.notifyDataSetChanged();
    }

    // ---- actions ----
    void menu(R r) {
        List<String> n = new ArrayList<>();
        if (r.wa == null) {
            n.add("Add to share sheet");
        } else {
            n.add("Contact settings\u2026");
            n.add("Remove from share sheet");
        }
        if (r.cap != null) n.add("Send test message");
        n.add("Details");
        new AlertDialog.Builder(this).setTitle(r.title).setItems(n.toArray(new String[0]), (d, w) -> {
            String a = n.get(w);
            if (a.startsWith("Add")) {
                D.waAdd(this, r.chat, r.title);
                K.publish(this);
                refresh();
            } else if (a.startsWith("Contact")) {
                settings(r);
            } else if (a.startsWith("Remove")) {
                D.waRemove(this, r.wa.id);
                K.publish(this);
                refresh();
            } else if (a.startsWith("Send")) {
                testDialog(r);
            } else {
                details(r);
            }
        }).show();
    }

    void settings(R r) {
        final D.Wa x = r.wa;
        final String[] g = {x.group};
        final List<D.Grp> gs = D.groups(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        CheckBox cb = new CheckBox(this);
        cb.setText("Also save the link as a clip");
        cb.setChecked(x.save);
        box.addView(cb);
        TextView gt = new TextView(this);
        gt.setTextSize(16);
        gt.setPadding(dp(4), dp(12), dp(4), dp(12));
        Runnable upd = () -> {
            String nm = g[0].isEmpty() ? "Inbox" : D.nameIn(gs, g[0]);
            gt.setText("Save to: " + (nm == null ? "Inbox" : nm) + " \u25BE");
        };
        upd.run();
        gt.setOnClickListener(v -> {
            List<String> n = new ArrayList<>();
            n.add("Inbox");
            for (D.Grp q : gs) n.add(q.name);
            new AlertDialog.Builder(this).setItems(n.toArray(new String[0]), (d, w) -> {
                g[0] = w == 0 ? "" : gs.get(w - 1).id;
                upd.run();
            }).show();
        });
        box.addView(gt);
        EditText ph = new EditText(this);
        ph.setHint("Phone for wa.me fallback (optional)");
        ph.setInputType(InputType.TYPE_CLASS_PHONE);
        ph.setSingleLine();
        ph.setText(x.phone);
        box.addView(ph);
        new AlertDialog.Builder(this).setTitle(r.title).setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    x.save = cb.isChecked();
                    x.group = g[0];
                    x.phone = D.clean(ph.getText().toString());
                    D.waUpdate(this, x);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void testDialog(R r) {
        EditText e = new EditText(this);
        e.setText("weeclip test");
        e.setSelection(e.getText().length());
        new AlertDialog.Builder(this).setTitle("Send to " + r.title).setView(e)
                .setPositiveButton("Send", (d, w) -> {
                    String res = N.send(getApplicationContext(), r.chat, e.getText().toString());
                    Toast.makeText(this, res, Toast.LENGTH_LONG).show();
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void details(R r) {
        StringBuilder s = new StringBuilder("chat: " + r.chat);
        if (r.cap != null) s.append("\ntag: ").append(r.cap.tag).append("\npackage: ").append(r.cap.pkg)
                .append("\ncaptured: ").append(r.cap.n).append("x\ncanceled: ").append(r.cap.dead);
        if (r.wa != null) s.append("\nshortcut id: ").append(r.wa.id).append("\nsave clip: ").append(r.wa.save)
                .append("\nphone: ").append(r.wa.phone.isEmpty() ? "-" : r.wa.phone);
        AlertDialog.Builder d = new AlertDialog.Builder(this).setTitle(r.title).setMessage(s)
                .setPositiveButton("OK", null);
        if (r.cap != null) d.setNegativeButton("Forget action", (x, w) -> { N.CAP.remove(r.chat); refresh(); });
        d.show();
    }

    void showLog() {
        StringBuilder s = new StringBuilder();
        synchronized (N.LOG) { for (String l : N.LOG) s.append(l).append('\n'); }
        new AlertDialog.Builder(this).setTitle("Log")
                .setMessage(s.length() == 0 ? "(empty)" : s.toString())
                .setPositiveButton("OK", null)
                .show();
    }
}
