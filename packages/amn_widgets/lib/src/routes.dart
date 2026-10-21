import 'package:flutter/material.dart';

import 'clock_page.dart';
import 'weather_page.dart';

/// Handles `/widget/weather?id=..&mode=add|edit` and `/widget/clock?...`,
/// which `WidgetConfigActivity` opens. Returns null for any other route.
Route<void>? widgetConfigRoute(RouteSettings settings) {
  final uri = Uri.tryParse(settings.name ?? '');
  if (uri == null || uri.pathSegments.length != 2 || uri.pathSegments.first != 'widget') return null;
  final id = int.tryParse(uri.queryParameters['id'] ?? '') ?? 0;
  final adding = uri.queryParameters['mode'] != 'edit';
  final Widget? page = switch (uri.pathSegments[1]) {
    'weather' => WeatherConfigPage(widgetId: id, adding: adding),
    'clock' => ClockConfigPage(widgetId: id, adding: adding),
    _ => null,
  };
  if (page == null) return null;
  return MaterialPageRoute<void>(settings: settings, builder: (_) => page);
}
