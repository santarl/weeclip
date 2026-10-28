package io.rfsj.weeclip;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Publishes sharing shortcuts: one per group (save) and one per enabled WhatsApp contact (send), in the user's order. */
final class K {
    static final String CAT = "io.rfsj.weeclip.category.CLIP";

    /** Groups and WhatsApp contacts as {id, label, "g" or "w"}, in share sheet order. */
    static List<String[]> entries(Context c) {
        Map<String, String[]> m = new LinkedHashMap<>();
        for (D.Grp g : D.groups(c)) m.put(g.id, new String[]{g.id, g.name, "g"});
        for (D.Wa w : D.was(c)) m.put(w.id, new String[]{w.id, w.name, "w"});
        List<String[]> l = new ArrayList<>();
        for (String id : D.order(c)) {
            String[] e = m.remove(id);
            if (e != null) l.add(e);
        }
        l.addAll(m.values());
        return l;
    }

    /** @return null on success, else the error text. */
    static String publish(Context c) {
        try {
            ShortcutManager sm = c.getSystemService(ShortcutManager.class);
            if (sm == null) return "no ShortcutManager";
            int max = sm.getMaxShortcutCountPerActivity();
            List<ShortcutInfo> l = new ArrayList<>();
            for (String[] e : entries(c)) {
                if (l.size() >= max) break;
                boolean wa = e[2].equals("w");
                Intent it = new Intent(Intent.ACTION_MAIN).setClass(c, wa ? W.class : M.class);
                if (!wa) it.putExtra("g", e[0]);
                l.add(build(c, e[0], e[1], wa ? "WhatsApp: " + e[1] : e[1], wa, it, l.size()));
            }
            sm.setDynamicShortcuts(l);
            return null;
        } catch (Exception e) {
            N.log("share sheet publish failed: " + e);
            return String.valueOf(e);
        }
    }

    /** Tap an entry to move it to the top. Android only shows the first few (usually 3) per app. */
    static void orderDialog(Activity a) {
        List<String[]> e = entries(a);
        if (e.isEmpty()) {
            Toast.makeText(a, "No groups or contacts yet", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] n = new String[e.size()];
        for (int i = 0; i < n.length; i++) n[i] = (i + 1) + ".  " + e.get(i)[1] + (e.get(i)[2].equals("w") ? "  (WhatsApp)" : "");
        new AlertDialog.Builder(a).setTitle("Share sheet order \u00B7 tap to move to top")
                .setItems(n, (d, w) -> {
                    List<String> o = new ArrayList<>();
                    o.add(e.get(w)[0]);
                    for (int i = 0; i < e.size(); i++) if (i != w) o.add(e.get(i)[0]);
                    D.setOrder(a, o);
                    String err = publish(a);
                    if (err != null) Toast.makeText(a, "Couldn't publish: " + err, Toast.LENGTH_LONG).show();
                    orderDialog(a);
                })
                .setNegativeButton("Done", null)
                .show();
    }

    static ShortcutInfo build(Context c, String id, String label, String longLabel, boolean wa, Intent it, int rank) {
        ShortcutInfo.Builder b = new ShortcutInfo.Builder(c, id)
                .setShortLabel(label)
                .setLongLabel(longLabel)
                .setIcon(icon(label, wa))
                .setIntent(it)
                .setCategories(Collections.singleton(CAT))
                .setRank(rank);
        if (Build.VERSION.SDK_INT >= 30) b.setLongLived(true);
        return b.build();
    }

    /** Letter on a coloured square; WhatsApp contacts are always green so they stand out from groups. */
    static Icon icon(String name, boolean wa) {
        Bitmap b = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        float hue = wa ? 142 : (name.hashCode() & 0x7fffffff) % 360;
        cv.drawColor(Color.HSVToColor(new float[]{hue, 0.55f, wa ? 0.62f : 0.75f}));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(96);
        String s = name.isEmpty() ? "?" : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase();
        cv.drawText(s, 108, 108 - (p.descent() + p.ascent()) / 2, p);
        return Icon.createWithAdaptiveBitmap(b);
    }
}
