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

## Credits

- Weather icons: [Fluent Emoji](https://github.com/microsoft/fluentui-emoji) © Microsoft, MIT License
  (see `packages/amn_widgets/third_party/fluent-emoji/LICENSE`). Night partly-cloudy and fog are
  composites of those images.
- Weather data and city search: [Open-Meteo](https://open-meteo.com), CC BY 4.0.
- Numerals: [Barlow Condensed](https://github.com/jpt/barlow), SIL Open Font License 1.1
  (see `packages/amn_widgets/third_party/barlow-condensed/OFL.txt`).
