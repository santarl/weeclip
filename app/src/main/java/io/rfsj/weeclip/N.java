package io.rfsj.weeclip;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watches WhatsApp notifications and keeps each chat's Reply action in memory,
 * so a message can be sent later by replaying it. Message text is never read or stored.
 */
public class N extends NotificationListenerService {
    static final class Cap {
        String id, title, tag, pkg;
        PendingIntent pi;
        RemoteInput[] ri;
        long t;        // when the action was last captured
        boolean live;  // its notification is currently visible
        boolean dead;  // send() said the PendingIntent was canceled
        int n;         // how many times it has been captured
    }

    static final Map<String, Cap> CAP = new ConcurrentHashMap<>();
    static final LinkedList<String> LOG = new LinkedList<>();
    static volatile boolean on;      // listener connected
    static volatile Runnable ui;     // set by W while visible

    static void log(String s) {
        synchronized (LOG) {
            LOG.addFirst(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + "  " + s);
            while (LOG.size() > 200) LOG.removeLast();
        }
        Runnable r = ui;
        if (r != null) r.run();
    }

    @Override public void onListenerConnected() {
        on = true;
        log("listener connected");
        try {
            StatusBarNotification[] a = getActiveNotifications();
            if (a != null) for (StatusBarNotification s : a) take(s);
        } catch (Exception e) {
            log("initial scan failed: " + e);
        }
    }

    @Override public void onListenerDisconnected() {
        on = false;
        log("listener disconnected, asking to rebind");
        try { requestRebind(new ComponentName(this, N.class)); } catch (Exception e) { log("rebind failed: " + e); }
    }

    @Override public void onNotificationPosted(StatusBarNotification s) { take(s); }

    @Override public void onNotificationRemoved(StatusBarNotification s) {
        Cap k = CAP.get(idOf(s));
        if (k != null) { k.live = false; log("notification gone (action kept): " + k.title); }
    }

    static boolean wa(String pkg) { return "com.whatsapp".equals(pkg) || "com.whatsapp.w4b".equals(pkg); }

    static String titleOf(Notification n) {
        Bundle e = n.extras;
        CharSequence t = e.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
        if (t == null) t = e.getCharSequence(Notification.EXTRA_TITLE);
        return t == null ? "" : t.toString();
    }

    /** Stable per-chat key: shortcut id if present, else the notification tag, else the title. */
    static String idOf(StatusBarNotification s) {
        Notification n = s.getNotification();
        String sc = n.getShortcutId();
        String k = sc != null && !sc.isEmpty() ? sc : s.getTag() != null ? s.getTag() : titleOf(n);
        return s.getPackageName() + "|" + k;
    }

    static void take(StatusBarNotification s) {
        if (s == null || !wa(s.getPackageName())) return;
        Notification n = s.getNotification();
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0 || n.actions == null) return;
        Notification.Action reply = null;
        for (Notification.Action a : n.actions) {
            RemoteInput[] r = a.getRemoteInputs();
            if (r != null && r.length > 0 && a.actionIntent != null) { reply = a; break; }
        }
        if (reply == null) return;
        String id = idOf(s);
        Cap k = CAP.get(id);
        if (k == null) { k = new Cap(); k.id = id; CAP.put(id, k); }
        k.title = titleOf(n).isEmpty() ? id : titleOf(n);
        k.pkg = s.getPackageName();
        k.tag = s.getTag();
        k.pi = reply.actionIntent;
        k.ri = reply.getRemoteInputs();
        k.t = System.currentTimeMillis();
        k.live = true;
        k.dead = false;
        k.n++;
        log("captured " + k.title + " (#" + k.n + ")");
    }

    /** Replays the cached Reply action. @return a short human-readable result. */
    static String send(Context c, String id, String text) {
        Cap k = CAP.get(id);
        if (k == null) return "no cached action";
        try {
            Bundle b = new Bundle();
            for (RemoteInput r : k.ri) b.putCharSequence(r.getResultKey(), text);
            Intent i = new Intent();
            RemoteInput.addResultsToIntent(k.ri, i, b);
            if (Build.VERSION.SDK_INT >= 28) RemoteInput.setResultsSource(i, RemoteInput.SOURCE_FREE_FORM_INPUT);
            k.pi.send(c, 0, i);
            log("sent to " + k.title);
            return "sent";
        } catch (PendingIntent.CanceledException e) {
            k.dead = true;
            log("canceled: " + k.title);
            return "action was canceled";
        } catch (Exception e) {
            log("send error: " + e);
            return "error: " + e.getMessage();
        }
    }
}
