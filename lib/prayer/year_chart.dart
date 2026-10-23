import 'dart:math' as math;

import 'package:amn_ui/amn_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'timetable.dart';

/// One prayer's time across a calendar year: later is higher. Its turns (the
/// latest and earliest of the year) are marked and labelled; today has a
/// faint line. One finger picks a day; two fingers pinch to zoom and drag to
/// pan, and the time axis fits whatever stretch is in view.
///
/// Drawn in two layers so scrubbing stays cheap: the chart itself, behind a
/// repaint boundary and repainted only when its data, view or colours change,
/// and a cursor on top that's all that moves while a finger picks a day.
/// Lets the page show a reset while the chart is zoomed, and reset it.
class YearChartController extends ChangeNotifier {
  bool _zoomed = false;
  VoidCallback? _reset;

  bool get zoomed => _zoomed;

  /// Back to the whole year.
  void reset() => _reset?.call();

  void _set(bool z) {
    if (z == _zoomed) return;
    _zoomed = z;
    notifyListeners();
  }
}

class YearChart extends StatefulWidget {
  const YearChart({
    super.key,
    this.controller,
    required this.year,
    required this.series,
    required this.today,
    required this.selected,
    required this.onSelected,
    required this.format,
    required this.h24,
    this.height = 232,
  });

  /// The calendar year shown.
  final int year;
  final YearSeries series;

  /// UAE dates.
  final DateTime today;
  final DateTime selected;
  final ValueChanged<DateTime> onSelected;

  /// Minutes after midnight as the phone shows times; [h24] says which way,
  /// so the chart knows when its labels need redrawing.
  final String Function(int minutes) format;
  final bool h24;
  final double height;
  final YearChartController? controller;

  @override
  State<YearChart> createState() => _YearChartState();
}

class _YearChartState extends State<YearChart> {
  /// Fewest days the view zooms in to.
  static const _minSpan = 10.0;

  /// The days in view, from Jan 1 = 0; fractional while pinching.
  late double _start, _end;

  // Where a pinch began: the view then, and the day under the fingers.
  double _span0 = 0, _focusDay = 0;

  int get _days => DateTime.utc(widget.year + 1).difference(DateTime.utc(widget.year)).inDays;
  double get _span => _end - _start;
  bool get _zoomed => _span < _days - 1 - .5;

  @override
  void initState() {
    super.initState();
    _full();
    widget.controller?._reset = _reset;
  }

  @override
  void dispose() {
    if (widget.controller?._reset == _reset) widget.controller?._reset = null;
    super.dispose();
  }

  void _full() {
    _start = 0;
    _end = _days - 1.0;
  }

  void _reset() => setState(_full);

  @override
  void didUpdateWidget(YearChart old) {
    super.didUpdateWidget(old);
    if (old.controller != widget.controller) {
      old.controller?._reset = null;
      widget.controller?._reset = _reset;
    }
    if (old.year != widget.year) {
      _full();
    } else if (old.selected != widget.selected) {
      // Picked from outside (back to today): bring it into view.
      final d = _dayOf(widget.selected);
      if (d < _start || d > _end) _pan(d - _span / 2);
    }
  }

  double _dayOf(DateTime d) => d.difference(DateTime.utc(widget.year)).inDays.toDouble();

  /// Moves the view to start at [start], kept inside the year.
  void _pan(double start) {
    final span = _span;
    _start = start.clamp(0, _days - 1 - span).toDouble();
    _end = _start + span;
  }

  /// 0..1 across the plot for a touch at [dx].
  double _frac(double dx, double width) => ((dx - _Layout.left) / (width - _Layout.left - _Layout.right)).clamp(0.0, 1.0);

  void _select(double dx, double width) {
    final date = DateTime.utc(widget.year).add(Duration(days: (_start + _frac(dx, width) * _span).round()));
    final old = widget.selected;
    if (date == old) return;
    // A tick for each new month and on each turn, not every day.
    final idx = Timetable.dayIndex(date.month, date.day);
    if (date.month != old.month || widget.series.turns.any((t) => t.from == idx)) HapticFeedback.selectionClick();
    widget.onSelected(date);
  }

  void _scaleStart(ScaleStartDetails d, double width) {
    _span0 = _span;
    _focusDay = _start + _frac(d.localFocalPoint.dx, width) * _span;
  }

  /// Two fingers: zoom by their spread, and pan so the day that was under
  /// them stays under them. One finger is a horizontal drag, handled apart.
  void _scaleUpdate(ScaleUpdateDetails d, double width) {
    if (d.pointerCount < 2 || _span0 == 0) return;
    final span = (_span0 / d.horizontalScale).clamp(_minSpan, _days - 1.0);
    final start = _focusDay - _frac(d.localFocalPoint.dx, width) * span;
    setState(() {
      _start = start.clamp(0, _days - 1 - span).toDouble();
      _end = _start + span;
    });
  }

  @override
  Widget build(BuildContext context) {
    final w = widget;
    final g = AmnGlass.of(context);
    final accent = Theme.of(context).colorScheme.primary;
    final surface = Theme.of(context).colorScheme.surface;
    // Tell the page when zooming starts or ends, after this frame: it rebuilds in response.
    final zoomed = _zoomed, c = widget.controller;
    if (c != null && c.zoomed != zoomed) WidgetsBinding.instance.addPostFrameCallback((_) => c._set(zoomed));
    return LayoutBuilder(
      builder: (context, box) {
        final size = Size(box.maxWidth, w.height);
        final scale = _Scale(w.series.values, w.year, _start, _end, size);
        return GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTapDown: (d) => _select(d.localPosition.dx, box.maxWidth),
          onHorizontalDragStart: (d) => _select(d.localPosition.dx, box.maxWidth),
          onHorizontalDragUpdate: (d) => _select(d.localPosition.dx, box.maxWidth),
          onScaleStart: (d) => _scaleStart(d, box.maxWidth),
          onScaleUpdate: (d) => _scaleUpdate(d, box.maxWidth),
          child: Stack(
            children: [
              RepaintBoundary(
                child: CustomPaint(
                  size: size,
                  painter: _ChartPainter(
                    scale: scale,
                    series: w.series,
                    today: w.today,
                    format: w.format,
                    h24: w.h24,
                    sub: g.sub,
                    ink: g.ink,
                    grid: g.divider,
                    accent: accent,
                    ring: surface,
                  ),
                ),
              ),
              CustomPaint(
                size: size,
                painter: _CursorPainter(
                  scale: scale,
                  series: w.series,
                  selected: w.selected,
                  format: w.format,
                  h24: w.h24,
                  ink: g.ink,
                  accent: accent,
                  ring: surface,
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

abstract final class _Layout {
  static const left = 44.0;
  static const right = 10.0;

  /// Room for the selected-day label, then for labels above the latest turns.
  static const top = 30.0 + 32;

  /// Room for labels under the earliest turns, then the date row.
  static const bottom = 30.0 + 20;
}

const _monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/// Where days and minutes land, shared by both layers: the days in view
/// across the plot, and their range of times up its height.
class _Scale {
  factory _Scale(List<int> values, int year, double start, double end, Size size) {
    // Fit the times in view (and a day either side, so the line meets the edges).
    var lo = 1 << 30, hi = -1;
    for (var d = start.floor() - 1; d <= end.ceil() + 1; d++) {
      final date = DateTime.utc(year).add(Duration(days: d));
      final v = values[_leap(date)];
      lo = math.min(lo, v);
      hi = math.max(hi, v);
    }
    // A flat stretch still gets a few minutes of height.
    if (hi - lo < 4) {
      final mid = (hi + lo) / 2;
      lo = (mid - 2).floor();
      hi = (mid + 2).ceil();
    }
    return _Scale._(year, start, end, size, lo, hi);
  }

  _Scale._(this.year, this.start, this.end, this.size, this.lo, this.hi)
    : plot = Rect.fromLTRB(_Layout.left, _Layout.top, size.width - _Layout.right, size.height - _Layout.bottom);

  final int year;
  final double start, end;
  final Size size;
  final Rect plot;
  final int lo, hi;

  double get span => end - start;

  /// Days from Jan 1 to [d].
  double day(DateTime d) => d.difference(DateTime.utc(year)).inDays.toDouble();
  double xDay(double day) => plot.left + (day - start) / span * plot.width;
  double x(DateTime d) => xDay(day(d));
  double y(num m) => plot.bottom - (m - lo) / (hi - lo) * plot.height;
  bool shows(DateTime d) => day(d) >= start - .01 && day(d) <= end + .01;

  @override
  bool operator ==(Object other) =>
      other is _Scale && other.year == year && other.start == start && other.end == end && other.size == size && other.lo == lo && other.hi == hi;

  @override
  int get hashCode => Object.hash(year, start, end, size, lo, hi);
}

int _leap(DateTime d) => Timetable.dayIndex(d.month, d.day);

TextPainter _text(String s, double size, Color color, {FontWeight weight = FontWeight.w600}) => TextPainter(
  text: TextSpan(
    text: s,
    style: TextStyle(fontSize: size, color: color, fontWeight: weight, height: 1.1),
  ),
  textDirection: TextDirection.ltr,
)..layout();

/// A dot with a ring in the surface colour, so it reads over the line.
void _dot(Canvas canvas, Offset p, Color color, Color ring, double radius) {
  canvas.drawCircle(p, radius + 2, Paint()..color = ring);
  canvas.drawCircle(p, radius, Paint()..color = color);
}

class _ChartPainter extends CustomPainter {
  _ChartPainter({
    required this.scale,
    required this.series,
    required this.today,
    required this.format,
    required this.h24,
    required this.sub,
    required this.ink,
    required this.grid,
    required this.accent,
    required this.ring,
  });

  final _Scale scale;
  final YearSeries series;
  final DateTime today;
  final String Function(int) format;
  final bool h24;
  final Color sub, ink, grid, accent, ring;

  /// A turn's first day, in the year shown (29 Feb becomes 1 Mar in other years).
  DateTime _turnDate(Turn t) {
    final d = DateTime.utc(2028).add(Duration(days: t.from));
    return DateTime.utc(scale.year, d.month, d.day);
  }

  /// Date labels for the view, kept ~36 px apart: month names across the
  /// year; the 1st, 8th, 15th and 22nd as it zooms; then every few days or
  /// every day, numbered, with the month on the first label and on each 1st.
  /// [months] is true for the first kind, which labels spans, not days.
  (List<(double, String)>, bool months) _dates() {
    final s = scale;
    final perDay = s.plot.width / s.span;
    final out = <(double, String)>[];
    final every = (36 / perDay).ceil();
    final months = every > 10;
    if (months) {
      // Each month's name in the middle of its days.
      for (var m = 1; m <= 12; m++) {
        out.add((s.day(DateTime.utc(s.year, m, 15)), _monthNames[m - 1]));
      }
    } else {
      var first = true;
      for (var d = s.start.ceil(); d <= s.end.floor(); d++) {
        final date = DateTime.utc(s.year).add(Duration(days: d));
        final show = every > 3 ? const [1, 8, 15, 22].contains(date.day) : d % every == 0 || date.day == 1;
        // A 1st forced in among every-other days can crowd the label before it.
        if (!show || (out.isNotEmpty && (d - out.last.$1) * perDay < 30)) continue;
        out.add((d.toDouble(), every > 3 || first || date.day == 1 ? '${date.day} ${_monthNames[date.month - 1]}' : '${date.day}'));
        first = false;
      }
    }
    return (out, months);
  }

  @override
  void paint(Canvas canvas, Size size) {
    final s = scale, plot = s.plot;
    // Gridlines at clean steps inside the range in view, two to four.
    final step = [1, 2, 5, 10, 15, 20, 30, 60, 120].firstWhere((v) => (s.hi - s.lo) / v <= 3, orElse: () => 180);
    final hair = Paint()
      ..color = grid
      ..strokeWidth = 1;
    for (var m = (s.lo + step - 1) ~/ step * step; m <= s.hi ~/ step * step; m += step) {
      canvas.drawLine(Offset(plot.left, s.y(m)), Offset(plot.right, s.y(m)), hair);
      final t = _text(format(m), 10.5, sub, weight: FontWeight.w500);
      t.paint(canvas, Offset(plot.left - t.width - 8, s.y(m) - t.height / 2));
    }

    // Dates along the bottom, with a short tick where a label marks a day.
    final (dates, months) = _dates();
    for (final (d, label) in dates) {
      final x = s.xDay(d);
      final t = _text(label, 10.5, sub);
      t.paint(canvas, Offset((x - t.width / 2).clamp(0, size.width - t.width).toDouble(), size.height - 16));
      if (!months) canvas.drawLine(Offset(x, plot.bottom + 26), Offset(x, plot.bottom + 30), hair);
    }

    // Everything on the time axis stays inside the plot's width.
    canvas.save();
    canvas.clipRect(Rect.fromLTRB(plot.left - 1, 0, plot.right + 1, size.height));

    // Today: a faint line, when it's in view.
    if (today.year == s.year && s.shows(today)) {
      canvas.drawLine(
        Offset(s.x(today), plot.top - 18),
        Offset(s.x(today), plot.bottom + 18),
        Paint()
          ..color = sub.withValues(alpha: .35)
          ..strokeWidth = 1,
      );
    }

    // The line, with a wash under it: the days in view and one either side.
    final line = Path();
    var first = true;
    for (var d = math.max(0, s.start.floor() - 1); d <= math.min(s.end.ceil() + 1, DateTime.utc(s.year + 1).difference(DateTime.utc(s.year)).inDays - 1); d++) {
      final date = DateTime.utc(s.year).add(Duration(days: d));
      final p = Offset(s.xDay(d.toDouble()), s.y(series.smooth[_leap(date)]));
      first ? line.moveTo(p.dx, p.dy) : line.lineTo(p.dx, p.dy);
      first = false;
    }
    final bounds = line.getBounds();
    final wash = Path.from(line)
      ..lineTo(bounds.right, plot.bottom)
      ..lineTo(bounds.left, plot.bottom)
      ..close();
    canvas.drawPath(
      wash,
      Paint()
        ..shader = LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [accent.withValues(alpha: .18), accent.withValues(alpha: 0)],
        ).createShader(plot),
    );
    canvas.drawPath(
      line,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2
        ..strokeJoin = StrokeJoin.round
        ..strokeCap = StrokeCap.round
        ..color = accent,
    );
    canvas.restore();

    // Turns in view: a ringed dot, its time and first day. Latest above, earliest below.
    for (final t in series.turns) {
      final date = _turnDate(t);
      if (!s.shows(date)) continue;
      final p = Offset(s.x(date), s.y(series.smooth[_leap(date)]));
      _dot(canvas, p, ink, ring, 4);
      final time = _text(format(t.minutes), 12, ink, weight: FontWeight.w800);
      final day = _text('${date.day} ${_monthNames[date.month - 1]}', 10.5, sub);
      final w = math.max(time.width, day.width);
      final left = (p.dx - w / 2).clamp(plot.left - 6, size.width - w).toDouble();
      if (t.latest) {
        day.paint(canvas, Offset(left + (w - day.width) / 2, p.dy - 10 - day.height));
        time.paint(canvas, Offset(left + (w - time.width) / 2, p.dy - 10 - day.height - time.height));
      } else {
        time.paint(canvas, Offset(left + (w - time.width) / 2, p.dy + 9));
        day.paint(canvas, Offset(left + (w - day.width) / 2, p.dy + 9 + time.height));
      }
    }
  }

  @override
  bool shouldRepaint(_ChartPainter old) =>
      old.scale != scale ||
      !identical(old.series, series) ||
      old.today != today ||
      old.h24 != h24 ||
      old.accent != accent ||
      old.ink != ink ||
      old.sub != sub ||
      old.ring != ring;
}

/// The picked day, when in view: a crosshair, a dot on the line, and a label pinned to the top.
class _CursorPainter extends CustomPainter {
  _CursorPainter({
    required this.scale,
    required this.series,
    required this.selected,
    required this.format,
    required this.h24,
    required this.ink,
    required this.accent,
    required this.ring,
  });

  final _Scale scale;
  final YearSeries series;
  final DateTime selected;
  final String Function(int) format;
  final bool h24;
  final Color ink, accent, ring;

  @override
  void paint(Canvas canvas, Size size) {
    final s = scale;
    if (!s.shows(selected)) return;
    final sx = s.x(selected);
    canvas.drawLine(
      Offset(sx, 24),
      Offset(sx, s.plot.bottom),
      Paint()
        ..color = ink.withValues(alpha: .5)
        ..strokeWidth = 1,
    );
    _dot(canvas, Offset(sx, s.y(series.smooth[_leap(selected)])), accent, ring, 5);
    final label = _text('${selected.day} ${_monthNames[selected.month - 1]} · ${format(series.values[_leap(selected)])}', 12, ring, weight: FontWeight.w700);
    final pill = Rect.fromCenter(center: Offset(sx, 12), width: label.width + 16, height: 22);
    final shift = pill.left < 0 ? -pill.left : (pill.right > size.width ? size.width - pill.right : 0.0);
    final r = RRect.fromRectAndRadius(pill.shift(Offset(shift, 0)), const Radius.circular(11));
    canvas.drawRRect(r, Paint()..color = ink);
    label.paint(canvas, Offset(r.left + 8, r.top + (22 - label.height) / 2));
  }

  @override
  bool shouldRepaint(_CursorPainter old) =>
      old.selected != selected ||
      old.h24 != h24 ||
      old.scale != scale ||
      !identical(old.series, series) ||
      old.accent != accent ||
      old.ink != ink ||
      old.ring != ring;
}
