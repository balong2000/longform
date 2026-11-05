# Longform

A YouTube app for Android with Shorts removed completely. It is m.youtube.com
running in GeckoView (Firefox's engine), so signing in with Google works and you
keep your subscriptions, history, playlists and comments.

## What is gone

- The **Shorts tab** in the bottom bar
- **Shorts shelves** on Home, Search, Subscriptions, History, channel pages and the watch page
- **Single Shorts** mixed into search results, related videos and feeds
- The **Shorts tab on channel pages**, plus the Shorts filter in search
- The **Remix** button (it only makes Shorts)
- **Shorts links**: any `/shorts/ID` link (tapped, shared, or opened from another app)
  opens as a normal video in the regular player. `/shorts` and `/@channel/shorts` go to
  Home and the channel's Videos tab.

How it works: `app/src/main/assets/noshorts/` is a built-in web extension. Its CSS
hides Shorts elements before they paint, and its script catches anything labelled
"Shorts", feed entries linking to Shorts, and in-app navigation to a Short.
`MainActivity` also rewrites Shorts URLs at the navigation level, so the Shorts
player can't load even if YouTube renames its elements.

## Install

1. Copy `dist\Longform-1.0.1.apk` to the phone and open it.
2. Allow "Install unknown apps" for the app you opened it with when Android asks.
3. Optional: make YouTube links open here. Settings → Apps → Longform →
   Open by default → Add links → tick youtube.com, m.youtube.com, youtu.be.
   You can also share any YouTube link to Longform.

64-bit ARM phones only (every phone from roughly 2017 on), Android 8.0 or newer.

## Build

```
build.cmd test assembleRelease
```

Uses the project-local toolchain in `.tools\` (JDK 21, Android SDK 37.2, Gradle 9.8.2).
Output: `app\build\outputs\apk\release\app-release.apk`.

Emulator test build (x86_64): `build.cmd assembleRelease -Pabi=x86_64`. The test AVD
`longform-test` lives in `.tools\avd`; run it headless with `-gpu host` (software GPU
modes freeze once Gecko starts rendering).

Known quirk: YouTube shows an "Open App" button in the watch-page header. Longform
refuses to hand off to the YouTube app, so tapping it does nothing.

**Keep `longform-release.jks` and `keystore.properties`.** They are the app's
signing key; future versions only install over this one if they are signed with
the same key. Both are git-ignored.

## Third-party code

The APK bundles [GeckoView](https://geckoview.dev) by Mozilla, licensed under the
[Mozilla Public License 2.0](https://mozilla.org/MPL/2.0/). Its source is available at
https://hg.mozilla.org/releases/mozilla-release (GeckoView 159).

Longform is an unofficial client. It is not affiliated with or endorsed by YouTube or
Google; it shows the regular m.youtube.com website.

## If Shorts come back

YouTube renames page elements now and then. The selectors are in
`noshorts.css` (first line of defence) and `noshorts.js` (label- and link-based
fallback). Rebuild after editing them.
