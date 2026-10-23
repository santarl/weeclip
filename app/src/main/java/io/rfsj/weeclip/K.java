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

/** Publishes one sharing shortcut per group so they show up as "contacts" in the share sheet. */
final class K {
    static final String CAT = "io.rfsj.weeclip.category.CLIP";

    static void publish(Context c) {
        try {
            ShortcutManager sm = c.getSystemService(ShortcutManager.class);
            if (sm == null) return;
            List<D.Grp> g = D.groups(c);
            int n = Math.min(g.size(), sm.getMaxShortcutCountPerActivity());
            List<ShortcutInfo> l = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                D.Grp x = g.get(i);
                Intent it = new Intent(Intent.ACTION_MAIN).setClass(c, M.class).putExtra("g", x.id);
                ShortcutInfo.Builder b = new ShortcutInfo.Builder(c, x.id)
                        .setShortLabel(x.name)
                        .setLongLabel(x.name)
                        .setIcon(icon(x.name))
                        .setIntent(it)
                        .setCategories(Collections.singleton(CAT))
                        .setRank(i);
                if (Build.VERSION.SDK_INT >= 30) b.setLongLived(true);
                l.add(b.build());
            }
            sm.setDynamicShortcuts(l);
        } catch (Exception e) {
            // shortcuts are a nicety; the app still works without them
        }
    }

    static Icon icon(String name) {
        Bitmap b = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        cv.drawColor(Color.HSVToColor(new float[]{(name.hashCode() & 0x7fffffff) % 360, 0.55f, 0.75f}));
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
