import 'package:amn_ui/amn_ui.dart';
import 'package:amn_widgets/amn_widgets.dart';
import 'package:flutter/material.dart';

import 'home_page.dart';
import 'perf_probe.dart';
import 'prayer/prayer_page.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  startPerfProbe();
  runApp(const DriftScope(child: AmnApp()));
}

/// AMN: a personal toolbox. Tools are added here over time; the home-screen
/// widgets live in packages/amn_widgets, the look in packages/amn_ui.
class AmnApp extends StatelessWidget {
  const AmnApp({super.key});

  Route<void> _route(RouteSettings settings) =>
      widgetConfigRoute(settings) ??
      switch (settings.name) {
        '/prayer' => MaterialPageRoute<void>(settings: settings, builder: (_) => const DriftMute(child: PrayerPage())),
        _ => MaterialPageRoute<void>(settings: settings, builder: (_) => const HomePage()),
      };

  @override
  Widget build(BuildContext context) {
    final drift = Drift.of(context);
    // Light or dark follows the sky behind the UI, not the phone's setting.
    final theme = AmnTheme.build(drift.theme, drift.accent);
    return MaterialApp(
      title: 'AMN',
      theme: theme,
      darkTheme: theme,
      // Text and accent follow the sky; let them glide rather than jump.
      themeAnimationDuration: drift.settled ? const Duration(milliseconds: 1200) : Duration.zero,
      builder: (context, child) => DriftBackground(child: child!),
      onGenerateRoute: _route,
      // One route only: the default would also push '/' and '/widget' under '/widget/weather?...'.
      onGenerateInitialRoutes: (name) => [_route(RouteSettings(name: name))],
    );
  }
}
