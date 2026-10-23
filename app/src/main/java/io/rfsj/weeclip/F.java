package io.rfsj.weeclip;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.Html;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/** Link cleanup and metadata (title, author, thumbnail) fetching. */
final class F {
    static final String UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36";
    static final String FB = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)";
    static final Pattern URL_RE = Pattern.compile("https?://[^\\s]+");
    static final Pattern META = Pattern.compile("<meta\\s[^>]*>", Pattern.CASE_INSENSITIVE);
    static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    static final Set<String> busy = new HashSet<>();

    static String host(String url) {
        String h = Uri.parse(url).getHost();
        if (h == null) return "";
        h = h.toLowerCase();
        return h.startsWith("www.") ? h.substring(4) : h;
    }

    /** First link in the shared text, https only, tracking params dropped for known hosts. */
    static String url(String t) {
        if (t == null) return null;
        Matcher m = URL_RE.matcher(t);
        if (!m.find()) return null;
        Uri u = Uri.parse(m.group().replaceAll("[).,]+$", ""));
        String h = host(u.toString());
        if (h.isEmpty()) return null;
        boolean yt = h.endsWith("youtube.com");
        boolean known = yt || h.equals("youtu.be") || h.endsWith("instagram.com") || h.endsWith("tiktok.com");
        String v = u.getQueryParameter("v");
        Uri.Builder b = u.buildUpon().scheme("https").fragment(null);
        if (known) {
            b.clearQuery();
            if (yt && v != null) b.appendQueryParameter("v", v);
        }
        String s = b.build().toString();
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /** Fetch metadata for every clip that has none yet. Safe to call from several threads. */
    static void pending(Context c) {
        for (D.Clip k : D.clips(c)) {
            if (k.ok) continue;
            synchronized (busy) { if (!busy.add(k.id)) continue; }
            try { one(c, k); } finally { synchronized (busy) { busy.remove(k.id); } }
        }
    }

    static void one(Context c, D.Clip k) {
        String title = null, by = null, img = null;
        try {
            String h = host(k.url);
            String api = null;
            if (h.endsWith("youtube.com") || h.equals("youtu.be"))
                api = "https://www.youtube.com/oembed?format=json&url=" + Uri.encode(k.url);
            else if (h.endsWith("tiktok.com"))
                api = "https://www.tiktok.com/oembed?url=" + Uri.encode(k.url);
            if (api != null) {
                String j = get(api, UA);
                if (j != null) {
                    JSONObject o = new JSONObject(j);
                    title = o.optString("title");
                    by = o.optString("author_name");
                    img = o.optString("thumbnail_url");
                }
            } else {
                String s = get(k.url, FB);
                if (s != null) {
                    title = og(s, "og:title");
                    img = og(s, "og:image");
                    if (title == null) {
                        Matcher m = TITLE.matcher(s);
                        if (m.find()) title = dec(m.group(1));
                    }
                }
            }
            if (title != null && (title.equals("Instagram") || title.startsWith("Login"))) title = null;
            if (img != null && img.startsWith("http"))
                thumb(c, k.id, img.replaceFirst("^http:", "https:"), k.url.contains("/shorts/"));
        } catch (IOException e) {
            return; // offline or server trouble: stays pending, retried later
        } catch (Exception e) {
            // unparsable reply: keep whatever we got
        }
        if (title == null || title.isEmpty()) {
            String p = Uri.parse(k.url).getPath();
            title = host(k.url) + (p == null ? "" : p);
        }
        k.title = D.clean(title);
        k.by = D.clean(by);
        k.ok = true;
        D.update(c, k);
        D.notifyUi();
    }

    static byte[] fetch(String u, String ua, int max) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
        try {
            h.setConnectTimeout(10000);
            h.setReadTimeout(15000);
            h.setRequestProperty("User-Agent", ua);
            h.setRequestProperty("Accept-Language", "en-US,en;q=0.8");
            int s = h.getResponseCode();
            if (s >= 500 || s == 429) throw new IOException("http " + s);
            if (s >= 400) return null;
            try (InputStream in = h.getInputStream()) {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                int n;
                while (o.size() < max && (n = in.read(b)) > 0) o.write(b, 0, n);
                return o.toByteArray();
            }
        } finally {
            h.disconnect();
        }
    }

    static String get(String u, String ua) throws IOException {
        byte[] d = fetch(u, ua, 400000);
        return d == null ? null : new String(d, "UTF-8");
    }

    static void thumb(Context c, String id, String u, boolean crop) throws IOException {
        byte[] d = fetch(u, UA, 3000000);
        if (d == null) return;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(d, 0, d.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return;
        int s = 1;
        while (o.outWidth / (s * 2) >= 480) s *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = s;
        Bitmap b = BitmapFactory.decodeByteArray(d, 0, d.length, o);
        if (b == null) return;
        if (crop && b.getWidth() * 4 > b.getHeight() * 3) { // Shorts thumbs come pillarboxed in a 4:3 frame
            int w = b.getHeight() * 9 / 16;
            if (w < b.getWidth()) b = Bitmap.createBitmap(b, (b.getWidth() - w) / 2, 0, w, b.getHeight());
        }
        if (b.getWidth() > 540) b = Bitmap.createScaledBitmap(b, 480, b.getHeight() * 480 / b.getWidth(), true);
        try (FileOutputStream f = new FileOutputStream(D.th(c, id))) {
            b.compress(Bitmap.CompressFormat.JPEG, 80, f);
        }
    }

    // ---- tiny html helpers ----
    static String dec(String s) { return Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().trim(); }

    static String attr(String tag, String name) {
        Matcher m = Pattern.compile("[\\s\"']" + name + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", Pattern.CASE_INSENSITIVE).matcher(tag);
        if (!m.find()) return null;
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    static String og(String html, String prop) {
        Matcher m = META.matcher(html);
        while (m.find()) {
            String t = m.group();
            String k = attr(t, "property");
            if (k == null) k = attr(t, "name");
            if (k != null && k.equalsIgnoreCase(prop)) {
                String v = attr(t, "content");
                if (v != null && !v.trim().isEmpty()) return dec(v);
            }
        }
        return null;
    }
}
