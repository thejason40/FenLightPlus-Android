# FenLight+ Companion (Android)

An Android companion app for the **FenLight+** Kodi video plugin. Browse movies and TV shows on your phone, manage your watchlists, and send anything you find straight to Kodi for playback through FenLight+.

## Download

The latest signed APK can be downloaded here:

**https://thejason40.github.io/apk/**

Because the app is distributed outside the Play Store, you'll need to allow installation from your browser or file manager when prompted. The app can also update itself: it checks `version.json` on the same site at startup and offers to download and install new releases in place.

## What it does

The companion app talks to your Kodi box over the local network and drives the FenLight+ plugin remotely. When you pick something to watch, the app issues a Kodi JSON-RPC `Player.Open` call with a `plugin://plugin.video.fenlight/...` URL, so FenLight+ resolves sources and plays the title on your TV while you browse from the sofa. This might work with some other forks of FenLight but I haven't tried it.

Main features:

- **Movie and TV browsing** - customisable home rows (trending, popular, top rated, and more) powered by TMDB, with full detail pages, cast, recommendations, and similar titles.
- **Search** across movies, TV shows, and people.
- **Play to Kodi** - send any movie or episode to FenLight+ for playback with a single tap.
- **Trakt integration** - sign in to surface your Continue Watching list and personal lists, with cached state for fast loading.
- **TMDB account** - sign in via OAuth to access your personal TMDB lists and watchlists.
- **Real-Debrid** - view and manage your Real-Debrid account.
- **Automatic Kodi discovery** - scans your local network to find the Kodi instance automatically during setup.
- **Light / dark / system theming** and configurable region and adult-content filtering.

## Requirements

- An Android device running Android 8.0 (API 26) or newer.
- A Kodi installation on the same network with the FenLight+ plugin installed and its JSON-RPC remote control enabled (Kodi → Settings → Services → Control → "Allow remote control via HTTP").
