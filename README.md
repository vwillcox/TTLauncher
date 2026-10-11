# TT Launcher

A home screen for the Galaxy Z Fold 6, written in Kotlin with Jetpack Compose. It works on both the
cover screen and the inner screen, and each screen keeps its own home pages.

## What it does

- **Home pages** of skinnable widgets on a grid you choose. Hold a widget to rearrange: drag it to
  reorder, drag its corner to resize. Each widget has its own options for look, frame, font, colours
  and content. Each screen has its own pages, widgets and grid.
- **Built-in widgets**: clock (digital, stacked, analog), date, battery (bar, ring), month calendar,
  search, a pad of macro buttons (each opens an app or a link, or does something in the launcher),
  a decorative data cascade, the weather for a place you choose (from Open-Meteo, which needs no
  account), and a Spotify player with the cover, progress, transport buttons and a button that
  shows whether the track is in Liked Songs and adds or removes it. The player reads
  Spotify's media session, which Android allows with the same notification access the badges use.
  Ordinary Android widgets from other apps can be added too. On the LCARS skin their frames are
  named for the app, curve round the widget's own rounded card, and close any margin it leaves.
- **Dock** with pinned apps and fly-out folders. It and the page dots stay put while the home pages
  swipe behind them, and the dock can be switched off.
- **Notification badges**: an unread count on each app's icon, in the dock and the drawer. A dock
  folder shows the total for its apps, brings the ones with something unread to the front with a
  dot on each, and keeps flashing until those notifications are cleared. The drawer's category bar marks the folders
  with unread apps. Needs notification access (Settings > Icons), and follows each app's own badge
  switch in Android's settings.
- **App drawer** that sorts apps into folders by what they do. Folders are editable: each collects
  one or more topics (Audio, Video, Security, ...) plus any words you add.
- **News feed** to the left of home, from any RSS or Atom feeds, with muted topics. A story leaves
  the page once it has been opened.
- **Skins**, including an LCARS one, plus accent colours, icon shapes, themed (monochrome) icons, and
  ADW/Nova icon packs.
- **Wallpaper** per screen: share a picture to TT Launcher from any app (Immich included) and choose
  the cover screen, the inner screen, both, or the lock screen.
- **Page transitions**: slide, fade, zoom, cube, or a transporter beam that sparkles each page out and
  the next one in, and can do the same for apps opened from the launcher and for the home screen
  when you come back to it. Up to 12 home pages per screen.
- **Gestures**: swipe up for apps, swipe sideways between drawer folders, pull down for notifications.
  A sideways swipe over the news turns the page even when it arcs up or down, as a thumb's does.
  The news page takes no other gestures: nothing pulls down or swipes up there, and a swipe towards
  its far side, where there is no page, is left alone.

## Building

Needs JDK 17 or newer and the Android SDK (`local.properties` points at it).

```sh
./gradlew :app:assembleRelease      # the build to put on the phone: R8 makes Compose far faster
./gradlew :app:testDebugUnitTest    # the sorter, feed and weather parsers, topic filter, grid maths and badge counts
adb install -r app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the debug key, so it installs over itself without losing settings.
Make it the home screen from Settings > Apps > Default apps > Home app.

## How it stays light

- No background work of its own: no scheduled jobs, no polling. Feeds are fetched only while the news
  page is on screen and the last fetch is over 30 minutes old.
- Badges come from one notification listener that Android runs and calls only when a notification
  comes or goes. It is let go of while badges are switched off.
- Weather is fetched the same way: only while its widget is on screen and its report is over 30
  minutes old. The Spotify widget is told of changes by Android and listens only while visible.
- The clock redraws on the system's own once-a-minute tick, and only while the launcher is visible.
- The data cascade is the one widget that animates: about six small redraws a second, only while
  the launcher is in front, and it can be set to stand still.
- Icons are drawn once at display size and cached; the app list updates from system callbacks.
- Glass frames are translucent, not blurred. Opaque black skins switch OLED pixels off.

## Layout

| Path | What's there |
| --- | --- |
| `data/` | The saved state (`Models.kt`), its storage, and the home grid maths (`Grid.kt`) |
| `apps/` | Installed apps, the topic sorter (`Categorizer.kt`), folders, icon rendering |
| `feed/` | RSS/Atom parsing, topic muting, fetching |
| `weather/` | Open-Meteo forecasts and place search |
| `ui/` | Pages, widgets, sheets, skins, gestures |

The LCARS skin uses the Antonio typeface (`res/font/antonio.ttf`), which is under the SIL Open Font
License 1.1.

Not built yet: backup and restore of the setup, and drag-and-drop between the drawer and the dock.
