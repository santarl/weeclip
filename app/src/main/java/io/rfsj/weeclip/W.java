package io.rfsj.weeclip;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Debug screen for the WhatsApp relay: shows captured chats and can send a test message. */
public class W extends Activity {
    static final int BG = 0xFF000000, FG = 0xFFFFFFFF, DIM = 0xFF9E9E9E, WARN = 0xFFFFB74D;
    static final int MP = ViewGroup.LayoutParams.MATCH_PARENT, WC = ViewGroup.LayoutParams.WRAP_CONTENT;

    List<N.Cap> items = new ArrayList<>();
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
        log.setPadding(dp(12), dp(6), dp(0), dp(6));
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
                    s.setTextColor(DIM);
                    s.setTextSize(12);
                    r.addView(t);
                    r.addView(s);
                    r.setTag(new TextView[]{t, s});
                    v = r;
                }
                TextView[] tv = (TextView[]) v.getTag();
                N.Cap k = items.get(i);
                tv[0].setText(k.title);
                tv[1].setText((k.dead ? "CANCELED \u00B7 " : "") + "captured "
                        + DateUtils.getRelativeTimeSpanString(k.t, System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE) + " \u00B7 notification " + (k.live ? "visible" : "gone")
                        + " \u00B7 seen " + k.n + "x");
                tv[1].setTextColor(k.dead ? WARN : DIM);
                return v;
            }
        };
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setAdapter(ad);
        lv.setOnItemClickListener((p, v, i, id) -> testDialog(items.get(i)));
        lv.setOnItemLongClickListener((p, v, i, id) -> { details(items.get(i)); return true; });

        TextView empty = new TextView(this);
        empty.setText("No chats captured yet.\n\nTurn on notification access above, then receive a WhatsApp message. Chats with a visible notification are picked up right away.");
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
        List<N.Cap> l = new ArrayList<>(N.CAP.values());
        Collections.sort(l, (a, b) -> Long.compare(b.t, a.t));
        items = l;
        boolean g = granted();
        status.setText(!g ? "Notification access is OFF \u00B7 tap to open settings"
                : N.on ? "Notification access ON \u00B7 listener connected"
                : "Notification access ON \u00B7 listener not connected yet (tap to open settings)");
        status.setTextColor(g && N.on ? DIM : WARN);
        ad.notifyDataSetChanged();
    }

    void testDialog(N.Cap k) {
        EditText e = new EditText(this);
        e.setText("weeclip test");
        e.setSelection(e.getText().length());
        new AlertDialog.Builder(this).setTitle("Send to " + k.title).setView(e)
                .setPositiveButton("Send", (d, w) -> {
                    String r = N.send(getApplicationContext(), k.id, e.getText().toString());
                    Toast.makeText(this, r, Toast.LENGTH_LONG).show();
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void details(N.Cap k) {
        new AlertDialog.Builder(this).setTitle(k.title)
                .setMessage("id: " + k.id + "\ntag: " + k.tag + "\npackage: " + k.pkg
                        + "\ncaptured: " + k.n + "x\ncanceled: " + k.dead)
                .setPositiveButton("OK", null)
                .setNegativeButton("Forget", (d, w) -> { N.CAP.remove(k.id); refresh(); })
                .show();
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
