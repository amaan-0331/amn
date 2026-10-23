import 'dart:convert';
import 'dart:io';

import 'package:amn/prayer/awqaf.dart';
import 'package:amn/prayer/prayer_page.dart';
import 'package:amn/prayer/timetable.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final table = Timetable.fromJson(File('assets/prayer/uae.json').readAsStringSync());

  test('bundle covers every zone and area', () {
    expect(table.zones.length, 41);
    expect(table.areas.length, 319);
    for (final t in table.zones.values) {
      expect(Timetable.isValid(t), isTrue);
    }
    for (final a in table.areas) {
      expect(table.zones, contains(a.zone));
    }
  });

  test('day index follows a leap year', () {
    expect(Timetable.dayIndex(1, 1), 0);
    expect(Timetable.dayIndex(2, 29), 59);
    expect(Timetable.dayIndex(3, 1), 60);
    expect(Timetable.dayIndex(12, 31), 365);
  });

  test("Dubai on 24 Oct 2026 matches awqaf.gov.ae", () {
    final day = table.day(69, DateTime.utc(2026, 10, 24));
    expect(day.map((t) => t.hhmm), ['05:04', '06:18', '12:06', '15:19', '17:48', '19:02']);
    // 05:04 in the UAE is 01:04 UTC.
    expect(day.first.at, DateTime.utc(2026, 10, 24, 1, 4));
  });

  test('next skips sunrise and rolls into tomorrow after Isha', () {
    // 06:00 UAE: sunrise is next on the clock, but Dhuhr is the next prayer.
    expect(table.next(69, DateTime.utc(2026, 10, 24, 2)).prayer, Prayer.dhuhr);
    // 20:00 UAE: tomorrow's Fajr.
    final n = table.next(69, DateTime.utc(2026, 10, 24, 16));
    expect(n.prayer, Prayer.fajr);
    expect(n.at, DateTime.utc(2026, 10, 25, 1, 4));
  });

  test('turns: Fajr in Dubai is latest in January, earliest in June', () {
    final turns = Turn.find(table.year(69, Prayer.fajr));
    expect(turns.map((t) => (t.latest, t.minutes)), [(true, 5 * 60 + 45), (false, 3 * 60 + 58)]);
    final latest = turns.first;
    expect(latest.from, Timetable.dayIndex(1, 14));
    expect(latest.to, greaterThan(latest.from));
    // On 24 Oct, the next turn is January's latest.
    expect(Turn.next(turns, Timetable.dayIndex(10, 24)), same(latest));
    expect(Turn.next(turns, Timetable.dayIndex(1, 14)), same(latest));
  });

  test('turns: Dhuhr turns four times a year', () {
    final turns = Turn.find(table.year(69, Prayer.dhuhr));
    expect(turns.map((t) => t.latest), [true, false, true, false]);
    expect(turns.last.from, Timetable.dayIndex(10, 27));
  });

  test('turns read the year as a loop', () {
    // Earliest straddles New Year: 31 Dec and 1 Jan.
    final s = [for (var i = 0; i < 366; i++) 183 - (i - 183).abs()];
    s[365] = s[0];
    final turns = Turn.find(s);
    expect(turns.map((t) => (t.latest, t.from, t.to)), [(true, 183, 183), (false, 365, 0)]);
    expect(turns.last.holds(0), isTrue);
    expect(turns.last.holds(365), isTrue);
    expect(turns.last.holds(1), isFalse);
  });

  test('parse accepts the API shape and rejects anything else', () {
    final days = [
      for (var i = 0; i < 366; i++)
        {'fajer': '05:04', 'shuroq': '06:18', 'dhohur': '12:06', 'aser': '15:19', 'maghreb': '17:48', 'isha': '19:02'},
    ];
    String body(List d, {bool official = true}) => jsonEncode({
          'result': {
            'prayer_times': d,
            'meta_data': {'method': {'official': official}},
          },
        });
    expect(AwqafClient.parse(body(days)), startsWith('050406181206151917481902'));
    expect(() => AwqafClient.parse(body(days.sublist(1))), throwsA(isA<AwqafException>()));
    expect(() => AwqafClient.parse(body(days, official: false)), throwsA(isA<AwqafException>()));
    expect(() => AwqafClient.parse('{"message":"Invalid or expired API key","result":null}'), throwsA(isA<AwqafException>()));
  });

  test('until label', () {
    expect(untilLabel(const Duration(seconds: 30)), 'in 1 min');
    expect(untilLabel(const Duration(minutes: 25)), 'in 25 min');
    expect(untilLabel(const Duration(hours: 2)), 'in 2 h');
    expect(untilLabel(const Duration(hours: 1, minutes: 12)), 'in 1 h 12 min');
    expect(untilLabel(Duration.zero), 'now');
  });
}
