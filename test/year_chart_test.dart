import 'dart:io';

import 'package:amn/prayer/timetable.dart';
import 'package:amn/prayer/year_chart.dart';
import 'package:amn_ui/amn_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final table = Timetable.fromJson(File('packages/amn_widgets/android/src/main/assets/prayer/uae.json').readAsStringSync());
  final series = table.series(69, Prayer.fajr);

  Future<List<DateTime>> pump(WidgetTester tester, YearChartController controller) async {
    final picked = <DateTime>[];
    await tester.pumpWidget(MaterialApp(
      theme: AmnTheme.build(Brightness.light, Colors.indigo),
      home: Scaffold(
        body: Center(
          child: SizedBox(
            width: 360,
            child: StatefulBuilder(
              builder: (context, setState) => YearChart(
                controller: controller,
                year: 2026,
                series: series,
                today: DateTime.utc(2026, 10, 24),
                selected: picked.isEmpty ? DateTime.utc(2026, 10, 24) : picked.last,
                onSelected: (d) => setState(() => picked.add(d)),
                format: (m) => '${m ~/ 60}:${(m % 60).toString().padLeft(2, '0')}',
                h24: true,
              ),
            ),
          ),
        ),
      ),
    ));
    return picked;
  }

  testWidgets('pinch zooms in, one finger still picks a day, reset shows the year', (tester) async {
    final controller = YearChartController();
    final picked = await pump(tester, controller);
    expect(controller.zoomed, isFalse);

    // Two fingers spread apart across the middle of the chart.
    final chart = tester.getRect(find.byType(YearChart));
    final c = Offset(chart.center.dx, chart.top + 120);
    final a = await tester.startGesture(c - const Offset(20, 0), pointer: 1);
    final b = await tester.startGesture(c + const Offset(20, 0), pointer: 2);
    for (var i = 1; i <= 10; i++) {
      await a.moveTo(c - Offset(20 + i * 12, 0));
      await b.moveTo(c + Offset(20 + i * 12, 0));
      await tester.pump();
    }
    await a.up();
    await b.up();
    await tester.pump();
    await tester.pump();
    expect(controller.zoomed, isTrue);
    expect(picked, isEmpty, reason: 'a pinch must not pick days');

    // Zoomed about 7x: dragging the same distance now moves far fewer days.
    final drag = await tester.startGesture(c - const Offset(60, 0));
    for (var i = 1; i <= 12; i++) {
      await drag.moveTo(c + Offset(-60 + i * 10, 0));
      await tester.pump();
    }
    await drag.up();
    await tester.pump();
    final span = picked.last.difference(picked.first).inDays.abs();
    expect(span, inInclusiveRange(1, 40));

    controller.reset();
    await tester.pump();
    await tester.pump();
    expect(controller.zoomed, isFalse);
  });
}
