import 'dart:async';
import 'dart:convert';
import 'dart:isolate';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'awqaf.dart';
import 'timetable.dart';

/// What the last refresh did.
class RefreshOutcome {
  const RefreshOutcome({required this.checked, required this.changed, this.error});

  final int checked;
  final int changed;
  final String? error;
}

/// The bundled timetable, any zones refreshed since, and the chosen area.
class PrayerStore extends ChangeNotifier {
  PrayerStore._(this.table, this._prefs, this.refreshedAt, this._areaId);

  static const _zonesKey = 'prayer.zones';
  static const _refreshedKey = 'prayer.refreshedAt';
  static const _areaKey = 'prayer.area';

  /// Al Karama, Dubai, until one is chosen.
  static const defaultArea = 50326;

  static Future<PrayerStore>? _instance;

  /// Loaded once and shared by every screen.
  static Future<PrayerStore> get instance => _instance ??= _load();

  static Future<PrayerStore> _load() async {
    final source = await rootBundle.loadString('assets/prayer/uae.json');
    // 370 KB of JSON: decode it off the UI thread so no frame waits on it.
    final table = await Isolate.run(() => Timetable.fromJson(source));
    final prefs = await SharedPreferences.getInstance();
    final saved = prefs.getString(_zonesKey);
    if (saved != null) {
      (jsonDecode(saved) as Map<String, dynamic>).forEach((k, v) {
        final zone = int.parse(k);
        if (table.zones.containsKey(zone) && Timetable.isValid(v as String)) table.zones[zone] = v;
      });
    }
    return PrayerStore._(table, prefs, prefs.getInt(_refreshedKey), prefs.getInt(_areaKey) ?? defaultArea);
  }

  final Timetable table;
  final SharedPreferences _prefs;

  /// Epoch ms of the last successful refresh; null while on the bundled table.
  int? refreshedAt;

  int _areaId;
  bool refreshing = false;
  RefreshOutcome? lastRefresh;

  Area get area => table.area(_areaId) ?? table.area(defaultArea)!;

  String get emirate => table.emirates[area.emirate]!;

  set area(Area a) {
    _areaId = a.id;
    _prefs.setInt(_areaKey, a.id);
    notifyListeners();
  }

  /// Fetches every zone again from Awqaf. Zones that come back valid replace
  /// what's stored; anything else leaves the current table as it is.
  Future<RefreshOutcome> refresh({AwqafClient? client}) async {
    if (refreshing) return lastRefresh!;
    refreshing = true;
    notifyListeners();
    final awqaf = client ?? AwqafClient();
    final fresh = <int, String>{};
    String? error;
    final queue = table.zones.keys.toList();
    // A few at a time: 41 requests, gently.
    Future<void> worker() async {
      while (queue.isNotEmpty && error == null) {
        final zone = queue.removeLast();
        try {
          fresh[zone] = await awqaf.fetchZone(zone);
        } on AwqafException catch (e) {
          error ??= e.message;
        }
      }
    }

    await Future.wait([for (var i = 0; i < 4; i++) worker()]);
    var changed = 0;
    if (error == null) {
      fresh.forEach((zone, times) {
        if (table.zones[zone] != times) changed++;
        table.zones[zone] = times;
      });
      refreshedAt = DateTime.now().millisecondsSinceEpoch;
      await _prefs.setString(_zonesKey, jsonEncode(fresh.map((k, v) => MapEntry('$k', v))));
      await _prefs.setInt(_refreshedKey, refreshedAt!);
    }
    refreshing = false;
    lastRefresh = RefreshOutcome(checked: fresh.length, changed: changed, error: error);
    notifyListeners();
    return lastRefresh!;
  }
}
