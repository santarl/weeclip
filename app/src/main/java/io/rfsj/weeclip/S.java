package io.rfsj.weeclip;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

/**
 * Share target. No UI: either saves the link to a group, or relays it to an enabled WhatsApp
 * contact by replaying that chat's cached Reply action (optionally saving a clip as well).
 */
public class S extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Context c = getApplicationContext();
        Intent i = getIntent();
        String raw = i.getStringExtra(Intent.EXTRA_TEXT);
        String u = F.url(raw);
        String sid = i.getStringExtra("android.intent.extra.shortcut.ID");
        String msg;
        try {
            D.Wa w = sid == null ? null : D.waById(c, sid);
            msg = w != null ? relay(c, w, u, raw) : save(c, u, sid);
        } catch (Throwable t) {
            msg = "Couldn't save";
        }
        if (msg != null) Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
        finish();
    }

    /** Plain save to a group (or the inbox). */
    String save(Context c, String u, String g) {
        if (u == null) return "No link found";
        String name = g == null || g.isEmpty() ? null : D.groupName(c, g);
        if (name == null) g = "";
        boolean added = D.add(c, g, u);
        F.go(c);
        J.schedule(c);
        return (added ? "Saved" : "Already saved") + (name == null ? "" : " to " + name);
    }

    /** @return the toast to show now, or null if a background wait will show it. */
    String relay(Context c, D.Wa w, String u, String raw) {
        String text = u != null ? u : raw;
        if (text == null || text.trim().isEmpty()) return "Nothing to send";
        String saved = "";
        if (w.save && u != null) {
            D.add(c, w.group, u);
            F.go(c);
            J.schedule(c);
            saved = " \u00B7 saved";
        }
        final String tail = saved;
        N.Cap k = N.CAP.get(w.chat);
        if (k != null && !k.dead) {
            String r = N.send(c, w.chat, text);
            return r.equals("sent") ? "Sent to " + w.name + tail : noHandle(c, w, text, tail, true);
        }
        if (N.ready) return noHandle(c, w, text, tail, true);

        // Listener not connected yet (cold start). Its callbacks run on the main thread,
        // so wait on a worker instead of blocking here.
        final Handler h = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String m = null;
            long end = SystemClock.uptimeMillis() + 3000;
            while (SystemClock.uptimeMillis() < end) {
                N.Cap x = N.CAP.get(w.chat);
                if (x != null && !x.dead) {
                    m = N.send(c, w.chat, text).equals("sent") ? "Sent to " + w.name + tail : null;
                    break;
                }
                if (N.ready) break;
                SystemClock.sleep(100);
            }
            final String msg = m != null ? m : noHandle(c, w, text, tail, false);
            h.post(() -> Toast.makeText(c, msg, Toast.LENGTH_LONG).show());
        }).start();
        return null;
    }

    /** No usable reply action: open WhatsApp if the contact is set up that way, else queue until they next message. */
    String noHandle(Context c, D.Wa w, String text, String tail, boolean canOpen) {
        String p = w.phone.replaceAll("[^0-9]", "");
        if (canOpen && w.open && !p.isEmpty()) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + p + "?text=" + Uri.encode(text))));
                return "Opened WhatsApp" + tail;
            } catch (Exception e) {
                // fall through to the queue
            }
        }
        D.qAdd(c, w.chat, text);
        return "Queued for " + w.name + ", sends when they next message you" + tail;
    }
}
