package app.longform;

import java.net.URI;
import java.net.URLDecoder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure URL rules, kept free of Android classes so they run in plain JUnit. */
final class Links {
    static final String HOME = "https://m.youtube.com/";

    private static final Pattern SHORTS_PATH = Pattern.compile("^/shorts/([A-Za-z0-9_-]{6,})");
    private static final Pattern YOUTU_BE_PATH = Pattern.compile("^/([A-Za-z0-9_-]{6,})");
    private static final Pattern URL_IN_TEXT = Pattern.compile("https?://\\S+");
    private static final Pattern GOOGLE_HOST = Pattern.compile("(^|\\.)google\\.[a-z]{2,3}(\\.[a-z]{2})?$");

    private Links() {}

    static String host(String url) {
        try {
            String h = new URI(url).getHost();
            return h == null ? null : h.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }

    static boolean isYouTubeHost(String host) {
        return host != null
                && (host.equals("youtube.com") || host.endsWith(".youtube.com")
                        || host.equals("youtu.be") || host.endsWith(".youtube-nocookie.com"));
    }

    /**
     * Hosts that stay inside the app. Google account hosts are needed for signing
     * in, which hops across accounts.google.com, accounts.youtube.com and regional
     * google.xx domains that set the session cookies.
     */
    static boolean isInternalHost(String host) {
        if (host == null) return true;
        return isYouTubeHost(host)
                || GOOGLE_HOST.matcher(host).find()
                || host.endsWith(".gstatic.com")
                || host.endsWith(".googleusercontent.com")
                || host.endsWith(".googleapis.com");
    }

    /**
     * If {@code url} points at a Short, returns the same video as a normal watch
     * page (with any other query parameters dropped); otherwise null.
     */
    static String shortsToWatch(String url) {
        try {
            URI u = new URI(url);
            if (!isYouTubeHost(lower(u.getHost())) || u.getRawPath() == null) return null;
            Matcher m = SHORTS_PATH.matcher(u.getRawPath());
            if (!m.find()) return null;
            return "https://m.youtube.com/watch?v=" + m.group(1);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Turns any YouTube link into the page the app should open: Shorts become
     * watch pages and youtu.be short links are expanded. Returns null for
     * links that are not YouTube.
     */
    static String normalizeIncoming(String url) {
        if (url == null) return null;
        String host = host(url);
        if (!isYouTubeHost(host)) return null;
        String watch = shortsToWatch(url);
        if (watch != null) return watch;
        if (host.equals("youtu.be")) {
            try {
                URI u = new URI(url);
                Matcher m = YOUTU_BE_PATH.matcher(u.getRawPath() == null ? "" : u.getRawPath());
                if (!m.find()) return HOME;
                String q = u.getRawQuery();
                return "https://m.youtube.com/watch?v=" + m.group(1) + (q == null || q.isEmpty() ? "" : "&" + q);
            } catch (Exception e) {
                return HOME;
            }
        }
        return url.startsWith("http://") ? "https://" + url.substring(7) : url;
    }

    /** Google sign-in pages; while on one, every hop (including SSO providers) stays in the app. */
    static boolean isAccountsHost(String host) {
        return host != null && (host.startsWith("accounts.google.") || host.equals("accounts.youtube.com"));
    }

    /**
     * Links in video descriptions go through youtube.com/redirect?q=TARGET.
     * Returns TARGET when it is a web link, otherwise null.
     */
    static String redirectTarget(String url) {
        try {
            URI u = new URI(url);
            if (!isYouTubeHost(lower(u.getHost())) || !"/redirect".equals(u.getRawPath())) return null;
            String q = u.getRawQuery();
            if (q == null) return null;
            for (String part : q.split("&")) {
                if (part.startsWith("q=")) {
                    String target = URLDecoder.decode(part.substring(2), "UTF-8");
                    return target.startsWith("http://") || target.startsWith("https://") ? target : null;
                }
            }
        } catch (Exception e) {
            // fall through
        }
        return null;
    }

    /** First YouTube link inside shared text ("Check this out https://youtu.be/..."). */
    static String fromSharedText(String text) {
        if (text == null) return null;
        Matcher m = URL_IN_TEXT.matcher(text);
        while (m.find()) {
            String n = normalizeIncoming(m.group());
            if (n != null) return n;
        }
        return null;
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }
}
