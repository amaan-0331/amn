import 'package:amn_widgets/amn_widgets.dart';
import 'package:flutter/material.dart';

void main() => runApp(const AmnApp());

/// AMN: a personal toolbox. Tools are added here over time; the home-screen
/// widgets live in packages/amn_widgets.
class AmnApp extends StatelessWidget {
  const AmnApp({super.key});

  static const _accent = Color(0xFFC2410C);

  ThemeData _theme(Brightness brightness) => ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: _accent, brightness: brightness),
        useMaterial3: true,
      );

  Route<void> _route(RouteSettings settings) =>
      widgetConfigRoute(settings) ?? MaterialPageRoute<void>(settings: settings, builder: (_) => const HomePage());

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AMN',
      theme: _theme(Brightness.light),
      darkTheme: _theme(Brightness.dark),
      onGenerateRoute: _route,
      // One route only: the default would also push '/' and '/widget' under '/widget/weather?...'.
      onGenerateInitialRoutes: (name) => [_route(RouteSettings(name: name))],
    );
  }
}

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: Text('AMN', style: theme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800))),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('No tools yet', style: theme.textTheme.titleLarge),
              const SizedBox(height: 8),
              Text(
                'Add a widget: long-press your home screen, tap Widgets, then find AMN.',
                textAlign: TextAlign.center,
                style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
              const SizedBox(height: 24),
              OutlinedButton(
                onPressed: () => Navigator.of(context).pushNamed('/widget/lab'),
                child: const Text('Widget lab'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
