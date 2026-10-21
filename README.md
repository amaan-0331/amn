# AMN

A personal toolbox app. Flutter for the app and its tools; the home-screen
widgets are native Android (Jetpack Glance) in their own package.

```
lib/                      Flutter app shell and in-app tools
packages/amn_widgets/     Home-screen widgets (Flutter plugin)
  lib/                    Edit pages, live preview, amn/widgets bridge
  android/…/widgets/
    core/                 Pure Kotlin: conditions, summary rules, palettes, offsets
    data/                 Settings, storage, Open-Meteo client, refresh and clock scheduling
    glance/               Weather and clock widgets
    config/               Edit-page activity and preview platform view
```

Kotlin computes, Flutter displays: everything a widget needs runs natively, so
refreshes work while the app is closed. Flutter owns every screen.

## Run

```
flutter run
cd android && ./gradlew :amn_widgets:testDebugUnitTest   # widget logic tests
flutter test && (cd packages/amn_widgets && flutter test)
```

Android only for now (minSdk 34). For "Current location" weather, allow location
**all the time**: widget refreshes run in the background. On Samsung, also add AMN to
Settings → Battery → Background usage limits → Never sleeping apps, so refreshes aren't delayed.

Design spec: `docs/design/widgets-spec.html`.

## Widget lab

AMN's home screen has a **Widget lab** button (debug and release builds). It renders every widget size through the
real preview (inflated like a launcher does), with overrides for condition, hour, dark mode,
units, 12/24h, seconds and clock city. Open it in any state from the command line:

```
adb shell am start -n dev.amaan.amn/.MainActivity --es route "'/widget/lab?cond=rain&hour=21&dark=1&kind=weather'"
```

Query keys: `cond` (clear, partly, cloudy, rain, storm, snow, fog), `hour` (0–23), `dark` (0/1),
`f` (1 for °F), `h24` (0/1), `secs` (0/1), `city` (Asia/Tokyo, America/New_York, Europe/London,
Asia/Dubai), `kind` (weather, clock, both). Grab the screen with `adb exec-out screencap -p > lab.png`.

## Credits

- Weather icons: [Fluent Emoji](https://github.com/microsoft/fluentui-emoji) © Microsoft, MIT License
  (see `packages/amn_widgets/third_party/fluent-emoji/LICENSE`). Night partly-cloudy and fog are
  composites of those images.
- Weather data and city search: [Open-Meteo](https://open-meteo.com), CC BY 4.0.
- Numerals: [Barlow Condensed](https://github.com/jpt/barlow), SIL Open Font License 1.1
  (see `packages/amn_widgets/third_party/barlow-condensed/OFL.txt`).
