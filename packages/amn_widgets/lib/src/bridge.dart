import 'package:flutter/services.dart';

/// How the edit page was opened.
class LaunchArgs {
  const LaunchArgs({required this.id, required this.kind, required this.mode});

  final int id;
  final String kind; // 'weather' | 'clock'
  final String mode; // 'add' | 'edit'

  bool get adding => mode == 'add';
}

/// The `amn/widgets` channel. Kotlin owns all widget settings; Flutter only
/// reads and writes them through here.
class WidgetBridge {
  WidgetBridge._();

  static const _channel = MethodChannel('amn/widgets');
  static const previewViewType = 'amn/widget-preview';

  static Future<LaunchArgs?> launchArgs() async {
    final m = await _channel.invokeMapMethod<String, Object?>('launchArgs');
    if (m == null) return null;
    return LaunchArgs(id: m['id']! as int, kind: m['kind']! as String, mode: m['mode']! as String);
  }

  /// Stored settings for the widget, plus the app-wide units.
  static Future<Map<String, Object?>> getSettings(int id, String kind) async =>
      await _channel.invokeMapMethod<String, Object?>('getSettings', {'id': id, 'kind': kind}) ?? {};

  /// Re-renders a preview platform view with unsaved settings.
  static Future<void> previewChanged(int viewId, Map<String, Object?> args) =>
      _channel.invokeMethod('previewChanged', {'viewId': viewId, ...args});

  /// Saves, updates the widget and closes the page (adds the widget when adding).
  static Future<void> save(int id, String kind, Map<String, Object?> settings) =>
      _channel.invokeMethod('save', {'id': id, 'kind': kind, 'settings': settings});

  /// Closes the page without saving. When adding, the widget isn't added.
  static Future<void> cancel() => _channel.invokeMethod('cancel');
}
