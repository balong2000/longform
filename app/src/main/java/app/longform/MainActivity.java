package app.longform;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSession.NavigationDelegate.LoadRequest;
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.MediaSession;
import org.mozilla.geckoview.OrientationController;

import java.util.List;
import java.util.Locale;

/**
 * The whole app: one GeckoView showing m.youtube.com, with a built-in web
 * extension (assets/noshorts) that strips every Shorts surface from the page,
 * and navigation rules that turn any Shorts URL into the normal video player.
 */
public class MainActivity extends Activity {
    private static final String TAG = "Longform";
    private static final String EXTENSION_URI = "resource://android/assets/noshorts/";
    private static final String EXTENSION_ID = "noshorts@longform.app";

    // One Gecko runtime per process, as GeckoView requires.
    private static GeckoRuntime runtime;
    private static boolean extensionReady;

    private FrameLayout root;
    private GeckoView geckoView;
    private GeckoSession session;
    private String currentUrl = Links.HOME;
    private boolean canGoBack;
    private boolean fullScreen;
    private OnBackInvokedCallback backCallback;
    private boolean backCallbackRegistered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new FrameLayout(this);
        geckoView = new GeckoView(this);
        root.addView(geckoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        setUpWindow();

        if (runtime == null) {
            GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                    .consoleOutput(BuildConfig.DEBUG)
                    .remoteDebuggingEnabled(BuildConfig.DEBUG)
                    .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM)
                    .build();
            runtime = GeckoRuntime.create(getApplicationContext(), settings);
        }
        runtime.getOrientationController().setDelegate(orientationDelegate);

        String start = urlFromIntent(getIntent());
        openSession(start != null ? start : Links.HOME);
    }

    // ---------------------------------------------------------------- session

    private void openSession(String url) {
        session = new GeckoSession();
        session.setNavigationDelegate(navigationDelegate);
        session.setContentDelegate(contentDelegate);
        session.setPermissionDelegate(permissionDelegate);
        session.setMediaSessionDelegate(mediaDelegate);
        session.open(runtime);
        geckoView.setSession(session);
        load(url);
    }

    /** Loads only once the Shorts filter is installed, so no page ever renders without it. */
    private void load(String url) {
        final GeckoSession target = session;
        if (target == null) return;
        if (extensionReady) {
            target.loadUri(url);
            return;
        }
        runtime.getWebExtensionController().ensureBuiltIn(EXTENSION_URI, EXTENSION_ID).accept(
                ext -> {
                    extensionReady = true;
                    loadIfCurrent(target, url);
                },
                err -> {
                    // The URL rules below still keep the Shorts player out.
                    Log.e(TAG, "Shorts filter extension failed to install", err);
                    loadIfCurrent(target, url);
                });
    }

    /**
     * The first install of the extension takes a few seconds; Android may have
     * recreated this activity (theme or font change) or replaced the session by then.
     */
    private void loadIfCurrent(GeckoSession target, String url) {
        if (target == session && target.isOpen()) target.loadUri(url);
    }

    private void restartSession() {
        if (session != null) {
            geckoView.releaseSession();
            session.close();
        }
        openSession(currentUrl);
    }

    // ------------------------------------------------------------- navigation

    private final GeckoSession.NavigationDelegate navigationDelegate = new GeckoSession.NavigationDelegate() {
        @Override
        public void onLocationChange(GeckoSession s, String url, List<ContentPermission> perms, Boolean hasUserGesture) {
            if (url != null) currentUrl = url;
        }

        @Override
        public void onCanGoBack(GeckoSession s, boolean value) {
            canGoBack = value;
            updateBackCallback();
        }

        @Override
        public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession s, LoadRequest request) {
            String uri = request.uri;
            String scheme = scheme(uri);

            if (scheme.equals("http") || scheme.equals("https")) {
                String watch = Links.shortsToWatch(uri);
                if (watch != null) {
                    s.loadUri(watch);
                    return GeckoResult.deny();
                }
                String out = Links.redirectTarget(uri);
                if (out != null) {
                    if (Links.isInternalHost(Links.host(out))) s.loadUri(out); else openExternally(out);
                    return GeckoResult.deny();
                }
                boolean internal = Links.isInternalHost(Links.host(uri))
                        || Links.isAccountsHost(Links.host(currentUrl));
                if (!internal) {
                    openExternally(uri);
                    return GeckoResult.deny();
                }
                if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
                    // No tabs in this app: open "new window" links in place.
                    s.loadUri(uri);
                    return GeckoResult.deny();
                }
                return GeckoResult.allow();
            }

            switch (scheme) {
                case "about":
                case "data":
                case "blob":
                case "resource":
                case "moz-extension":
                case "javascript":
                    return GeckoResult.allow();
                case "intent":
                case "vnd.youtube":
                case "youtube":
                    // "Open in the YouTube app" prompts: stay here.
                    return GeckoResult.deny();
                default:
                    openExternally(uri); // mailto:, tel:, market:, ...
                    return GeckoResult.deny();
            }
        }
    };

    private static String scheme(String uri) {
        int c = uri == null ? -1 : uri.indexOf(':');
        return c > 0 ? uri.substring(0, c).toLowerCase(Locale.ROOT) : "";
    }

    private void openExternally(String uri) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "No app can open " + uri);
        }
    }

    // ---------------------------------------------------- content and media

    private final GeckoSession.ContentDelegate contentDelegate = new GeckoSession.ContentDelegate() {
        @Override
        public void onFullScreen(GeckoSession s, boolean fs) {
            setFullScreen(fs);
        }

        @Override
        public void onCrash(GeckoSession s) {
            restartSession();
        }

        @Override
        public void onKill(GeckoSession s) {
            restartSession();
        }
    };

    private final GeckoSession.PermissionDelegate permissionDelegate = new GeckoSession.PermissionDelegate() {
        @Override
        public GeckoResult<Integer> onContentPermissionRequest(GeckoSession s, ContentPermission perm) {
            switch (perm.permission) {
                case PERMISSION_AUTOPLAY_AUDIBLE:     // autoplay of the next video
                case PERMISSION_AUTOPLAY_INAUDIBLE:
                case PERMISSION_MEDIA_KEY_SYSTEM_ACCESS: // rented/bought movies (Widevine)
                case PERMISSION_PERSISTENT_STORAGE:
                case PERMISSION_STORAGE_ACCESS:        // Google sign-in iframes
                    return GeckoResult.fromValue(ContentPermission.VALUE_ALLOW);
                default:                               // location, notifications, ...
                    return GeckoResult.fromValue(ContentPermission.VALUE_DENY);
            }
        }
    };

    private final MediaSession.Delegate mediaDelegate = new MediaSession.Delegate() {
        @Override
        public void onPlay(GeckoSession s, MediaSession m) {
            geckoView.setKeepScreenOn(true);
        }

        @Override
        public void onPause(GeckoSession s, MediaSession m) {
            geckoView.setKeepScreenOn(false);
        }

        @Override
        public void onStop(GeckoSession s, MediaSession m) {
            geckoView.setKeepScreenOn(false);
        }

        @Override
        public void onDeactivated(GeckoSession s, MediaSession m) {
            geckoView.setKeepScreenOn(false);
        }

        @Override
        public void onFullscreen(GeckoSession s, MediaSession m, boolean enabled, MediaSession.ElementMetadata meta) {
            // Vertical videos stay upright in full screen.
            if (enabled && meta != null && meta.height > meta.width) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            }
        }
    };

    private final OrientationController.OrientationDelegate orientationDelegate =
            new OrientationController.OrientationDelegate() {
                @Override
                public GeckoResult<AllowOrDeny> onOrientationLock(int orientation) {
                    setRequestedOrientation(orientation);
                    return GeckoResult.allow();
                }

                @Override
                public void onOrientationUnlock() {
                    setRequestedOrientation(fullScreen
                            ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                }
            };

    // ----------------------------------------------------------------- window

    private void setUpWindow() {
        if (Build.VERSION.SDK_INT >= 30) {
            // Draw edge to edge and pad the page by the system bars, the camera
            // cutout and the keyboard. Full screen video drops the padding.
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                if (fullScreen) {
                    v.setPadding(0, 0, 0, 0);
                } else {
                    Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                            | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                    v.setPadding(i.left, i.top, i.right, i.bottom);
                }
                return WindowInsets.CONSUMED;
            });
        }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        applyTheme();
    }

    /** Background and status-bar icon colours follow the system light/dark mode, like the page. */
    @SuppressWarnings("deprecation")
    private void applyTheme() {
        int bg = getColor(R.color.bg);
        getWindow().setBackgroundDrawable(new ColorDrawable(bg));
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            if (c != null) c.setSystemBarsAppearance(night ? 0 : light, light);
        } else {
            getWindow().setStatusBarColor(bg);
            getWindow().setNavigationBarColor(bg);
            View d = getWindow().getDecorView();
            int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            int flags = d.getSystemUiVisibility();
            d.setSystemUiVisibility(night ? flags & ~light : flags | light);
        }
    }

    @SuppressWarnings("deprecation")
    private void setFullScreen(boolean fs) {
        fullScreen = fs;
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                if (fs) {
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    c.hide(WindowInsets.Type.systemBars());
                } else {
                    c.show(WindowInsets.Type.systemBars());
                }
            }
            root.requestApplyInsets();
        } else {
            View d = getWindow().getDecorView();
            int immersive = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            int flags = d.getSystemUiVisibility();
            d.setSystemUiVisibility(fs ? flags | immersive : flags & ~immersive);
        }
        setRequestedOrientation(fs
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        updateBackCallback();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyTheme();
    }

    // ------------------------------------------------------------------- back

    private boolean handleBack() {
        if (fullScreen) {
            session.exitFullScreen();
            return true;
        }
        if (canGoBack) {
            session.goBack();
            return true;
        }
        return false;
    }

    /** Android 13+: claim the back gesture only while there is somewhere to go back to. */
    private void updateBackCallback() {
        if (Build.VERSION.SDK_INT < 33) return;
        boolean want = fullScreen || canGoBack;
        OnBackInvokedDispatcher d = getOnBackInvokedDispatcher();
        if (backCallback == null) backCallback = () -> handleBack();
        if (want && !backCallbackRegistered) {
            d.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
            backCallbackRegistered = true;
        } else if (!want && backCallbackRegistered) {
            d.unregisterOnBackInvokedCallback(backCallback);
            backCallbackRegistered = false;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() { // Android 12 and older
        if (!handleBack()) super.onBackPressed();
    }

    // ---------------------------------------------------------------- intents

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String url = urlFromIntent(intent);
        if (url != null) {
            load(url);
        } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Toast.makeText(this, "That isn't a YouTube link", Toast.LENGTH_SHORT).show();
        }
    }

    private static String urlFromIntent(Intent intent) {
        if (intent == null) return null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            return Links.normalizeIncoming(intent.getDataString());
        }
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            return Links.fromSharedText(intent.getStringExtra(Intent.EXTRA_TEXT));
        }
        return null;
    }

    // -------------------------------------------------------------- lifecycle

    @Override
    protected void onStart() {
        super.onStart();
        if (session != null) session.setActive(true);
    }

    @Override
    protected void onStop() {
        if (session != null) session.setActive(false);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        // A recreated activity may already have installed its own delegate.
        if (runtime != null && runtime.getOrientationController().getDelegate() == orientationDelegate) {
            runtime.getOrientationController().setDelegate(null);
        }
        if (session != null) {
            geckoView.releaseSession();
            session.close();
            session = null;
        }
        super.onDestroy();
    }
}
