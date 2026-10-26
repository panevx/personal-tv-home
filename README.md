# Personal TV Home

A native Android TV / Google TV launcher in Kotlin + Jetpack Compose, with a "Scenic Glass" look:
clear scenic photography, a floating glass dock of full-artwork app tiles, Inter typography and
spring-based motion. Runs on Android 8.0+ (glass blur is done in software, so it works on Android 9 TVs).

## Build & install

    export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
    ./gradlew :app:assembleRelease
    adb install -r app/build/outputs/apk/release/app-release.apk

Allow Now Playing to read media sessions (no UI for this on most TVs):

    adb shell cmd notification allow_listener dev.panev.tvhome/dev.panev.tvhome.data.MediaListenerService

Press Home on the remote and pick Personal TV Home (choose "Always") to make it the launcher.

## Using it

- Hold select on a dock tile: open, move left/right, remove, app info.
- Hold select on any tile in Apps / Discover: add to or remove from the dock.
- Settings (glass button, top right): Chromecast system settings, Home, Discover, My Space, Apps,
  Search, Wallpapers, Appearance.
- My Space: save a title, choose which app opens it (it searches that app for the title) and a collection.

Wallpapers are Unsplash photos (Unsplash License) via picsum.photos. Inter is under the SIL OFL (INTER_LICENSE.txt).
