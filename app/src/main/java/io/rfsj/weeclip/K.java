package io.rfsj.weeclip;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Publishes sharing shortcuts: one per group (save) and one per enabled WhatsApp contact (send). */
final class K {
    static final String CAT = "io.rfsj.weeclip.category.CLIP";

    static void publish(Context c) {
        try {
            ShortcutManager sm = c.getSystemService(ShortcutManager.class);
            if (sm == null) return;
            int max = sm.getMaxShortcutCountPerActivity();
            List<ShortcutInfo> l = new ArrayList<>();
            for (D.Grp x : D.groups(c)) {
                if (l.size() >= max) break;
                Intent it = new Intent(Intent.ACTION_MAIN).setClass(c, M.class).putExtra("g", x.id);
                l.add(build(c, x.id, x.name, x.name, false, it, l.size()));
            }
            for (D.Wa x : D.was(c)) {
                if (l.size() >= max) break;
                Intent it = new Intent(Intent.ACTION_MAIN).setClass(c, W.class);
                l.add(build(c, x.id, x.name, "WhatsApp: " + x.name, true, it, l.size()));
            }
            sm.setDynamicShortcuts(l);
        } catch (Exception e) {
            // shortcuts are a nicety; the app still works without them
        }
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
