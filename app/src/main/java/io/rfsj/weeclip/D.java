package io.rfsj.weeclip;

import android.content.Context;
import java.io.*;
import java.util.*;

/** Tiny tab-separated store: clips.tsv (oldest first) and groups.tsv. */
final class D {
    static final class Clip { String id, g, url, title = "", by = ""; long t; boolean ok; }
    static final class Grp { String id, name; }

    /** A WhatsApp chat the user enabled as a share-sheet "contact". id is the shortcut id, chat the listener's chat key. */
    static final class Wa { String id, chat, name, group = "", phone = ""; boolean save; }

    static final Object L = new Object();
    static volatile Runnable ui; // set by M while visible

    static File th(Context c, String id) { return new File(c.getFilesDir(), id + ".jpg"); }

    static String clean(String s) { return s == null ? "" : s.replaceAll("[\\t\\r\\n]+", " ").trim(); }

    static void notifyUi() { Runnable r = ui; if (r != null) r.run(); }

    // ---- file helpers ----
    static List<String[]> rows(Context c, String name, int min) {
        List<String[]> r = new ArrayList<>();
        File f = new File(c.getFilesDir(), name);
        if (!f.exists()) return r;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
            String l;
            while ((l = in.readLine()) != null) {
                String[] p = l.split("\t", -1);
                if (p.length >= min) r.add(p);
            }
        } catch (IOException e) { /* treat as empty */ }
        return r;
    }

    static void write(Context c, String name, List<String> lines) {
        File f = new File(c.getFilesDir(), name), tmp = new File(c.getFilesDir(), name + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8")) {
            for (String l : lines) w.write(l + "\n");
        } catch (IOException e) { return; }
        tmp.renameTo(f);
    }

    // ---- clips ----
    static List<Clip> load(Context c) {
        List<Clip> l = new ArrayList<>();
        for (String[] p : rows(c, "clips.tsv", 7)) {
            Clip k = new Clip();
            k.id = p[0]; k.g = p[1];
            try { k.t = Long.parseLong(p[2]); } catch (NumberFormatException e) { k.t = 0; }
            k.ok = p[3].equals("1"); k.url = p[4]; k.title = p[5]; k.by = p[6];
            l.add(k);
        }
        return l;
    }

    static void store(Context c, List<Clip> l) {
        List<String> s = new ArrayList<>();
        for (Clip k : l) s.add(k.id + "\t" + k.g + "\t" + k.t + "\t" + (k.ok ? "1" : "0") + "\t" + k.url + "\t" + clean(k.title) + "\t" + clean(k.by));
        write(c, "clips.tsv", s);
    }

    /** Newest first. */
    static List<Clip> clips(Context c) {
        synchronized (L) {
            List<Clip> l = load(c);
            Collections.reverse(l);
            return l;
        }
    }

    /** @return true if newly added, false if the link was already saved (it is moved to g if g is set). */
    static boolean add(Context c, String g, String url) {
        synchronized (L) {
            List<Clip> l = load(c);
            for (Clip k : l) {
                if (k.url.equals(url)) {
                    if (!g.isEmpty() && !g.equals(k.g)) { k.g = g; store(c, l); }
                    return false;
                }
            }
            Clip k = new Clip();
            k.id = "c" + Long.toString(System.currentTimeMillis(), 36);
            k.g = g; k.url = url; k.t = System.currentTimeMillis();
            l.add(k);
            store(c, l);
            return true;
        }
    }

    static void update(Context c, Clip n) {
        synchronized (L) {
            List<Clip> l = load(c);
            for (Clip k : l) if (k.id.equals(n.id)) { k.title = n.title; k.by = n.by; k.ok = n.ok; store(c, l); return; }
        }
    }

    static void move(Context c, String id, String g) {
        synchronized (L) {
            List<Clip> l = load(c);
            for (Clip k : l) if (k.id.equals(id)) { k.g = g; store(c, l); return; }
        }
    }

    static void delete(Context c, String id) {
        synchronized (L) {
            List<Clip> l = load(c);
            for (Iterator<Clip> i = l.iterator(); i.hasNext();) if (i.next().id.equals(id)) { i.remove(); store(c, l); break; }
            th(c, id).delete();
        }
    }

    static boolean hasPending(Context c) {
        synchronized (L) {
            for (Clip k : load(c)) if (!k.ok) return true;
            return false;
        }
    }

    // ---- groups ("contacts" in the share sheet) ----
    static List<Grp> groups(Context c) {
        synchronized (L) {
            List<Grp> l = new ArrayList<>();
            for (String[] p : rows(c, "groups.tsv", 2)) { Grp g = new Grp(); g.id = p[0]; g.name = p[1]; l.add(g); }
            return l;
        }
    }

    static void storeGroups(Context c, List<Grp> l) {
        List<String> s = new ArrayList<>();
        for (Grp g : l) s.add(g.id + "\t" + clean(g.name));
        write(c, "groups.tsv", s);
    }

    static String nameIn(List<Grp> l, String id) {
        for (Grp g : l) if (g.id.equals(id)) return g.name;
        return null;
    }

    static String groupName(Context c, String id) { return nameIn(groups(c), id); }

    static Grp addGroup(Context c, String name) {
        synchronized (L) {
            List<Grp> l = groups(c);
            Grp g = new Grp();
            g.id = "g" + Long.toString(System.currentTimeMillis(), 36);
            g.name = clean(name);
            l.add(g);
            storeGroups(c, l);
            return g;
        }
    }

    static void renameGroup(Context c, String id, String name) {
        synchronized (L) {
            List<Grp> l = groups(c);
            for (Grp g : l) if (g.id.equals(id)) { g.name = clean(name); storeGroups(c, l); return; }
        }
    }

    /** Clips of a deleted group go back to the inbox. */
    static void deleteGroup(Context c, String id) {
        synchronized (L) {
            List<Grp> l = groups(c);
            for (Iterator<Grp> i = l.iterator(); i.hasNext();) if (i.next().id.equals(id)) i.remove();
            storeGroups(c, l);
            List<Clip> k = load(c);
            for (Clip x : k) if (x.g.equals(id)) x.g = "";
            store(c, k);
            List<Wa> w = was(c);
            for (Wa x : w) if (x.group.equals(id)) x.group = "";
            storeWas(c, w);
        }
    }

    // ---- WhatsApp contacts (wa.tsv: id, chat, name, save, group, phone) ----
    static List<Wa> was(Context c) {
        synchronized (L) {
            List<Wa> l = new ArrayList<>();
            for (String[] p : rows(c, "wa.tsv", 6)) {
                Wa w = new Wa();
                w.id = p[0]; w.chat = p[1]; w.name = p[2]; w.save = p[3].equals("1"); w.group = p[4]; w.phone = p[5];
                l.add(w);
            }
            return l;
        }
    }

    static void storeWas(Context c, List<Wa> l) {
        List<String> s = new ArrayList<>();
        for (Wa w : l) s.add(w.id + "\t" + clean(w.chat) + "\t" + clean(w.name) + "\t" + (w.save ? "1" : "0") + "\t" + w.group + "\t" + clean(w.phone));
        write(c, "wa.tsv", s);
    }

    static Wa waById(Context c, String id) {
        for (Wa w : was(c)) if (w.id.equals(id)) return w;
        return null;
    }

    static Wa waAdd(Context c, String chat, String name) {
        synchronized (L) {
            List<Wa> l = was(c);
            for (Wa w : l) if (w.chat.equals(chat)) return w;
            Wa w = new Wa();
            w.id = "w" + Long.toString(System.currentTimeMillis(), 36);
            w.chat = chat; w.name = clean(name);
            l.add(w);
            storeWas(c, l);
            return w;
        }
    }

    static void waUpdate(Context c, Wa n) {
        synchronized (L) {
            List<Wa> l = was(c);
            for (Wa w : l) if (w.id.equals(n.id)) { w.save = n.save; w.group = n.group; w.phone = n.phone; storeWas(c, l); return; }
        }
    }

    static void waRemove(Context c, String id) {
        synchronized (L) {
            List<Wa> l = was(c);
            for (Iterator<Wa> i = l.iterator(); i.hasNext();) if (i.next().id.equals(id)) { i.remove(); storeWas(c, l); return; }
        }
    }
}
