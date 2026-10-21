import 'dart:async';

import 'package:amn_ui/amn_ui.dart';
import 'package:amn_widgets/amn_widgets.dart';
import 'package:flutter/material.dart';

import 'home_page.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  unawaited(Grain.load());
  runApp(const DriftScope(child: AmnApp()));
}

/// AMN: a personal toolbox. Tools are added here over time; the home-screen
/// widgets live in packages/amn_widgets, the look in packages/amn_ui.
class AmnApp extends StatelessWidget {
  const AmnApp({super.key});

  Route<void> _route(RouteSettings settings) =>
      widgetConfigRoute(settings) ?? MaterialPageRoute<void>(settings: settings, builder: (_) => const HomePage());

  @override
  Widget build(BuildContext context) {
    final accent = Drift.of(context).accent;
    return MaterialApp(
      title: 'AMN',
      theme: AmnTheme.build(Brightness.light, accent),
      darkTheme: AmnTheme.build(Brightness.dark, accent),
      // The accent follows the sky; let it glide rather than jump.
      themeAnimationDuration: const Duration(milliseconds: 1600),
      builder: (context, child) => DriftBackground(child: child!),
      onGenerateRoute: _route,
      // One route only: the default would also push '/' and '/widget' under '/widget/weather?...'.
      onGenerateInitialRoutes: (name) => [_route(RouteSettings(name: name))],
    );
  }
}
