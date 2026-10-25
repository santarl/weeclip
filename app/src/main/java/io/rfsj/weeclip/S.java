package io.rfsj.weeclip;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Share target. No UI: saves the link, kicks off the metadata fetch, and finishes. */
public class S extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Context c = getApplicationContext();
        Intent i = getIntent();
        String u = F.url(i.getStringExtra(Intent.EXTRA_TEXT));
        String msg;
        try {
            if (u == null) {
                msg = "No link found";
            } else {
                String g = i.getStringExtra("android.intent.extra.shortcut.ID");
                String name = g == null || g.isEmpty() ? null : D.groupName(c, g);
                if (name == null) g = "";
                boolean added = D.add(c, g, u);
                msg = (added ? "Saved" : "Already saved") + (name == null ? "" : " to " + name);
                F.go(c);
                J.schedule(c);
            }
        } catch (Throwable t) {
            msg = "Couldn't save";
        }
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
        finish();
    }
}
