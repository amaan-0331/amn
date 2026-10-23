import 'dart:async';
import 'dart:isolate';

import 'package:amn_widgets/amn_widgets.dart';
import 'package:flutter/foundation.dart';

import 'timetable.dart';

/// The timetable, any zones refreshed since, and the chosen area, as Kotlin
/// holds them (see PrayerStore in amn_widgets). The home-screen widgets read
/// the same data natively, so the area picked here is theirs too.
class PrayerStore extends ChangeNotifier {
  PrayerStore._(this.table, this.refreshedAt, this._areaId);

  /// Al Karama, Dubai, until one is chosen. Kotlin uses the same default.
  static const defaultArea = 50326;

  static Future<PrayerStore>? _instance;

  /// Loaded once and shared by every screen.
  static Future<PrayerStore> get instance => _instance ??= _load();

  static Future<PrayerStore> _load() async {
    final data = await PrayerBridge.load();
    // 370 KB of JSON: decode it off the UI thread so no frame waits on it.
    final source = data.bundle;
    final table = await Isolate.run(() => Timetable.fromJson(source));
    table.apply(data.refreshed);
    return PrayerStore._(table, data.refreshedAt, data.area);
  }

  final Timetable table;

  /// Epoch ms of the last successful refresh; null while on the bundled table.
  int? refreshedAt;

  int _areaId;
  bool refreshing = false;
  PrayerRefresh? lastRefresh;

  Area get area => table.area(_areaId) ?? table.area(defaultArea)!;

  String get emirate => table.emirates[area.emirate]!;

  set area(Area a) {
    _areaId = a.id;
    PrayerBridge.setArea(a.id);
    notifyListeners();
  }

  /// Fetches every zone again from Awqaf (in Kotlin). It saves only if all of
  /// them come back valid; otherwise the current table stays as it is.
  Future<PrayerRefresh> refresh() async {
    if (refreshing) return lastRefresh!;
    refreshing = true;
    notifyListeners();
    final r = await PrayerBridge.refresh();
    if (r.error == null) {
      table.apply(r.refreshed);
      refreshedAt = r.refreshedAt;
    }
    refreshing = false;
    lastRefresh = r;
    notifyListeners();
    return r;
  }
}
