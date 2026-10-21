# AMN

A personal toolbox app. Flutter for the app and its tools; the home-screen
widgets are native Android (Jetpack Glance) in their own package.

```
lib/                      Flutter app shell and in-app tools
packages/amn_widgets/     Home-screen widgets (Flutter plugin)
  lib/                    Edit pages, live preview, amn/widgets bridge
  android/…/widgets/
    core/                 Pure Kotlin: conditions, summary rules, palettes, offsets
    data/                 Settings, storage, forecast (Open-Meteo: to do)
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

Android only for now (minSdk 34).

## Credits

- Weather icons: [Fluent Emoji](https://github.com/microsoft/fluentui-emoji) © Microsoft, MIT License
  (see `packages/amn_widgets/third_party/fluent-emoji/LICENSE`). Night partly-cloudy and fog are
  composites of those images.
- Weather data (planned): [Open-Meteo](https://open-meteo.com), CC BY 4.0.
