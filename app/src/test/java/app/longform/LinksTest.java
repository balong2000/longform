package app.longform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LinksTest {
    @Test
    public void shortsBecomeWatchPages() {
        assertEquals("https://m.youtube.com/watch?v=abcDEF12345",
                Links.shortsToWatch("https://m.youtube.com/shorts/abcDEF12345"));
        assertEquals("https://m.youtube.com/watch?v=abcDEF12345",
                Links.shortsToWatch("https://www.youtube.com/shorts/abcDEF12345?feature=share"));
        assertEquals("https://m.youtube.com/watch?v=a-b_c123456",
                Links.shortsToWatch("https://youtube.com/shorts/a-b_c123456/"));
    }

    @Test
    public void nonShortsAreLeftAlone() {
        assertNull(Links.shortsToWatch("https://m.youtube.com/watch?v=abcDEF12345"));
        assertNull(Links.shortsToWatch("https://m.youtube.com/shorts/"));
        assertNull(Links.shortsToWatch("https://example.com/shorts/abcDEF12345"));
        assertNull(Links.shortsToWatch("https://m.youtube.com/@someone/shorts"));
    }

    @Test
    public void incomingLinks() {
        assertEquals("https://m.youtube.com/watch?v=abcDEF12345&t=42",
                Links.normalizeIncoming("https://youtu.be/abcDEF12345?t=42"));
        assertEquals("https://m.youtube.com/watch?v=abcDEF12345",
                Links.normalizeIncoming("https://youtube.com/shorts/abcDEF12345?si=xyz"));
        assertEquals("https://m.youtube.com/watch?v=x",
                Links.normalizeIncoming("http://m.youtube.com/watch?v=x"));
        assertNull(Links.normalizeIncoming("https://example.com/watch?v=x"));
    }

    @Test
    public void sharedText() {
        assertEquals("https://m.youtube.com/watch?v=abcDEF12345",
                Links.fromSharedText("look https://example.com then https://youtu.be/abcDEF12345"));
        assertNull(Links.fromSharedText("no links here"));
    }

    @Test
    public void descriptionRedirects() {
        assertEquals("https://example.com/a?b=c",
                Links.redirectTarget("https://m.youtube.com/redirect?event=video_description&q=https%3A%2F%2Fexample.com%2Fa%3Fb%3Dc&v=x"));
        assertNull(Links.redirectTarget("https://m.youtube.com/redirect?q=javascript%3Aalert(1)"));
        assertNull(Links.redirectTarget("https://m.youtube.com/watch?v=x&q=https%3A%2F%2Fexample.com"));
    }

    @Test
    public void accountsHosts() {
        assertTrue(Links.isAccountsHost("accounts.google.com"));
        assertTrue(Links.isAccountsHost("accounts.google.hu"));
        assertTrue(Links.isAccountsHost("accounts.youtube.com"));
        assertFalse(Links.isAccountsHost("m.youtube.com"));
        assertFalse(Links.isAccountsHost(null));
    }

    @Test
    public void internalHosts() {
        assertTrue(Links.isInternalHost("m.youtube.com"));
        assertTrue(Links.isInternalHost("accounts.google.com"));
        assertTrue(Links.isInternalHost("accounts.google.co.hu"));
        assertTrue(Links.isInternalHost("www.google.hu"));
        assertFalse(Links.isInternalHost("example.com"));
        assertFalse(Links.isInternalHost("google.com.evil.example"));
        assertFalse(Links.isInternalHost("notgoogle.com"));
    }
}
