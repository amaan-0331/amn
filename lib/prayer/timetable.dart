import 'dart:convert';

/// The five prayers, plus sunrise, which ends Fajr's time.
enum Prayer {
  fajr('Fajr'),
  sunrise('Sunrise'),
  dhuhr('Dhuhr'),
  asr('Asr'),
  maghrib('Maghrib'),
  isha('Isha');

  const Prayer(this.label);

  final String label;

  bool get isPrayer => this != sunrise;
}

/// A neighbourhood from Awqaf's list, and the timetable zone it follows.
class Area {
  const Area({required this.emirate, required this.id, required this.zone, required this.name, required this.nameAr});

  final int emirate;
  final int id;
  final int zone;
  final String name;
  final String nameAr;
}

/// One prayer on one day. [at] is the exact instant; [hhmm] is the time as
/// Awqaf prints it, in UAE time.
class PrayerTime {
  const PrayerTime(this.prayer, this.at, this.hhmm);

  final Prayer prayer;
  final DateTime at;
  final String hhmm;
}

/// Where a prayer time stops moving one way and turns: its latest or earliest
/// of the year. Times hold for a few days around a turn, so it spans
/// [from]..[to], leap-year day indices (inclusive; [to] < [from] across New Year).
class Turn {
  const Turn({required this.latest, required this.minutes, required this.from, required this.to});

  final bool latest;

  /// Minutes after midnight, UAE time.
  final int minutes;
  final int from;
  final int to;

  bool holds(int day) => from <= to ? day >= from && day <= to : day >= from || day <= to;

  /// Turns in a year of daily minutes, read as a loop (31 Dec runs into 1 Jan).
  /// Wobbles under [hysteresis] minutes don't count as a turn.
  static List<Turn> find(List<int> s, {int hysteresis = 2}) {
    final n = s.length;
    var g = 0;
    for (var i = 1; i < n; i++) {
      if (s[i] < s[g]) g = i;
    }
    // Walk once round from the earliest day, climbing first.
    final out = <Turn>[];
    var up = true;
    var from = g, to = g;
    for (var k = 1; k <= n; k++) {
      final i = (g + k) % n;
      final v = s[i], e = s[from];
      if (up ? v > e : v < e) {
        from = to = i;
      } else if (v == e) {
        to = i;
      } else if ((e - v).abs() >= hysteresis) {
        out.add(Turn(latest: up, minutes: e, from: from, to: to));
        up = !up;
        from = to = i;
      }
    }
    // Back at the earliest day: the walk began on that plateau, so finish it.
    var end = g;
    while (s[(end + 1) % n] == s[g] && (end + 1) % n != from) {
      end = (end + 1) % n;
    }
    out.add(Turn(latest: false, minutes: s[g], from: from, to: end));
    out.sort((a, b) => a.from.compareTo(b.from));
    return out;
  }

  /// The turn [day] is on, or the next one after it.
  static Turn next(List<Turn> turns, int day, {int n = 366}) {
    for (final t in turns) {
      if (t.holds(day)) return t;
    }
    return turns.reduce((a, b) => (a.from - day) % n <= (b.from - day) % n ? a : b);
  }
}

/// One prayer through the year, worked out once per zone table: the daily
/// times, their turns, and a smoothed line to draw.
class YearSeries {
  YearSeries(this.source, this.values) : turns = Turn.find(values), smooth = _smooth(values);

  /// The zone string these came from; a refresh replaces it.
  final String source;

  /// Minutes after midnight on each of the 366 leap-year days.
  final List<int> values;
  final List<Turn> turns;

  /// Times move in whole minutes, which draws as a staircase where a prayer
  /// barely moves (Dhuhr: half an hour all year). The line follows a 9-day
  /// average; labels keep the exact times.
  final List<double> smooth;

  static List<double> _smooth(List<int> v) => [
        for (var i = 0; i < v.length; i++) [for (var k = -4; k <= 4; k++) v[(i + k) % v.length]].reduce((a, b) => a + b) / 9,
      ];
}

/// The official UAE timetable. Awqaf's table is fixed by day of year, so each
/// zone is one leap year of days: 366 x 6 times x HHMM (see tool/prayer/fetch.py).
class Timetable {
  Timetable({required this.emirates, required this.areas, required this.zoneNames, required this.zones, required this.fetched});

  factory Timetable.fromJson(String source) {
    final j = jsonDecode(source) as Map<String, dynamic>;
    final zones = (j['zones'] as Map<String, dynamic>).map((k, v) => MapEntry(int.parse(k), v as Map<String, dynamic>));
    return Timetable(
      emirates: (j['emirates'] as Map<String, dynamic>).map((k, v) => MapEntry(int.parse(k), v as String)),
      areas: [
        for (final a in j['areas'] as List)
          Area(emirate: a[0] as int, id: a[1] as int, zone: a[2] as int, name: a[3] as String, nameAr: a[4] as String),
      ],
      zoneNames: zones.map((k, v) => MapEntry(k, v['name'] as String)),
      zones: zones.map((k, v) => MapEntry(k, v['times'] as String)),
      fetched: DateTime.parse(j['fetched'] as String),
    );
  }

  final Map<int, String> emirates;
  final List<Area> areas;
  final Map<int, String> zoneNames;

  /// Zone id to its 366-day string. Refreshed zones replace bundled ones.
  final Map<int, String> zones;

  /// When the bundled table was fetched.
  final DateTime fetched;

  static const daysLength = 366 * 24;

  /// UAE time is UTC+4 all year.
  static const uaeOffset = Duration(hours: 4);

  /// Whether [times] is a well-formed zone string.
  static bool isValid(String times) => times.length == daysLength && RegExp(r'^\d+$').hasMatch(times);

  /// Day of a leap year (0-365) for a calendar date: 29 Feb is its own day,
  /// and in other years 1 March onwards keeps its leap-year index.
  static int dayIndex(int month, int day) => DateTime.utc(2028, month, day).difference(DateTime.utc(2028)).inDays;

  /// Today's date in the UAE.
  static DateTime uaeDate(DateTime now) {
    final t = now.toUtc().add(uaeOffset);
    return DateTime.utc(t.year, t.month, t.day);
  }

  late final _areasById = {for (final a in areas) a.id: a};

  /// Puts refreshed zones in place of the bundled ones (well-formed, known zones only).
  void apply(Map<int, String> refreshed) {
    refreshed.forEach((zone, times) {
      if (zones.containsKey(zone) && isValid(times)) zones[zone] = times;
    });
  }

  Area? area(int id) => _areasById[id];

  /// The six times for [zone] on [date] (a UAE calendar date; only y/m/d are used).
  List<PrayerTime> day(int zone, DateTime date) {
    final s = zones[zone]!;
    final base = dayIndex(date.month, date.day) * 24;
    return [
      for (final p in Prayer.values)
        () {
          final i = base + p.index * 4;
          final h = int.parse(s.substring(i, i + 2));
          final m = int.parse(s.substring(i + 2, i + 4));
          final at = DateTime.utc(date.year, date.month, date.day, h, m).subtract(uaeOffset);
          return PrayerTime(p, at, '${s.substring(i, i + 2)}:${s.substring(i + 2, i + 4)}');
        }(),
    ];
  }

  final _series = <(int, Prayer), YearSeries>{};

  /// [prayer] through the year in [zone], kept until the zone is refreshed.
  YearSeries series(int zone, Prayer prayer) {
    final s = zones[zone]!;
    final hit = _series[(zone, prayer)];
    if (hit != null && identical(hit.source, s)) return hit;
    return _series[(zone, prayer)] = YearSeries(s, year(zone, prayer));
  }

  /// [prayer]'s time in minutes after midnight on each of the 366 leap-year days.
  List<int> year(int zone, Prayer prayer) {
    final s = zones[zone]!;
    return [
      for (var d = 0; d < 366; d++)
        () {
          final i = d * 24 + prayer.index * 4;
          return int.parse(s.substring(i, i + 2)) * 60 + int.parse(s.substring(i + 2, i + 4));
        }(),
    ];
  }

  /// The next prayer (not sunrise) after [now], looking into tomorrow after Isha.
  PrayerTime next(int zone, DateTime now) {
    final today = uaeDate(now);
    for (final d in [today, today.add(const Duration(days: 1))]) {
      for (final t in day(zone, d)) {
        if (t.prayer.isPrayer && t.at.isAfter(now)) return t;
      }
    }
    throw StateError('no prayer within two days');
  }
}
