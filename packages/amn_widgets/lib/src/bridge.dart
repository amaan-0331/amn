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

  /// Fetches this widget's weather now. Returns the new fetch time and place name.
  static Future<Map<String, Object?>> refreshNow(int id) async =>
      await _channel.invokeMapMethod<String, Object?>('refreshNow', {'id': id}) ?? {};

  /// Current time, offset from the phone ("+7h · tomorrow") and GMT label per IANA zone.
  static Future<Map<String, ZoneInfo>> describeZones(List<String> zones, {required bool h24}) async {
    final m = await _channel.invokeMapMethod<String, Object?>('describeZones', {'zones': zones, 'h24': h24}) ?? {};
    return {
      for (final e in m.entries)
        if (e.value is Map) e.key: ZoneInfo.from((e.value! as Map).cast<String, Object?>()),
    };
  }

  /// Widget lab: every summary rule's verdict and how the line was picked.
  static Future<Map<String, Object?>> explainSummary(Map<String, Object?> args) async =>
      await _channel.invokeMapMethod<String, Object?>('explainSummary', args) ?? {};

  /// Every widget placed on the home screen, weather first.
  static Future<PlacedWidgets> listWidgets() async {
    final m = await _channel.invokeMapMethod<String, Object?>('listWidgets') ?? {};
    return PlacedWidgets(
      fahrenheit: m['fahrenheit'] as bool? ?? false,
      widgets: [
        for (final w in (m['widgets'] as List? ?? const []).cast<Map>()) PlacedWidget.from(w.cast<String, Object?>()),
      ],
    );
  }

  /// Opens a placed widget's edit page over the app.
  static Future<void> openEditor(PlacedWidget w) => _channel.invokeMethod('openEditor', {'id': w.id, 'kind': w.kind});

  /// Closes the page without saving. When adding, the widget isn't added.
  static Future<void> cancel() => _channel.invokeMethod('cancel');
}

class ZoneInfo {
  const ZoneInfo({required this.time, required this.offset, required this.gmt});

  factory ZoneInfo.from(Map<String, Object?> m) =>
      ZoneInfo(time: m['time'] as String? ?? '', offset: m['offset'] as String? ?? '', gmt: m['gmt'] as String? ?? '');

  final String time;
  final String offset;
  final String gmt;
}

class PlacedWidgets {
  const PlacedWidgets({required this.widgets, required this.fahrenheit});

  final List<PlacedWidget> widgets;
  final bool fahrenheit;
}

/// A widget on the home screen and what it shows right now.
class PlacedWidget {
  const PlacedWidget({
    required this.id,
    required this.kind,
    this.place,
    this.gps = false,
    this.timeZone,
    this.tempC,
    this.condition,
    this.night = false,
    this.fetchedAt,
  });

  factory PlacedWidget.from(Map<String, Object?> m) => PlacedWidget(
        id: m['id']! as int,
        kind: m['kind']! as String,
        place: m['place'] as String?,
        gps: m['gps'] as bool? ?? false,
        timeZone: m['timeZone'] as String?,
        tempC: (m['tempC'] as num?)?.toDouble(),
        condition: m['condition'] as String?,
        night: m['night'] as bool? ?? false,
        fetchedAt: m['fetchedAt'] as int?,
      );

  final int id;
  final String kind; // 'weather' | 'clock'
  final String? place;
  final bool gps;
  final String? timeZone;
  final double? tempC;

  /// clear, partly, cloudy, rain, storm, snow or fog; null when not known.
  final String? condition;

  /// Whether it's night where the widget is (weather widgets only).
  final bool night;
  final int? fetchedAt;

  String? temperature({required bool fahrenheit}) {
    final c = tempC;
    if (c == null) return null;
    return '${(fahrenheit ? c * 9 / 5 + 32 : c).round()}°';
  }
}
