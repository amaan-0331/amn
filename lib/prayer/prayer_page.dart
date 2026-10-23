import 'dart:async';

import 'package:amn_ui/amn_ui.dart';
import 'package:amn_widgets/amn_widgets.dart';
import 'package:fluentui_system_icons/fluentui_system_icons.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'prayer_store.dart';
import 'timetable.dart';
import 'year_chart.dart';

/// UAE prayer times from Awqaf's official timetable. Up top, the next prayer
/// and where it falls in the day; then one prayer through the year, with its
/// turns (when it stops getting later and starts getting earlier); then the
/// times for whichever day is picked on that chart.
class PrayerPage extends StatefulWidget {
  const PrayerPage({super.key});

  @override
  State<PrayerPage> createState() => _PrayerPageState();
}

class _PrayerPageState extends State<PrayerPage> {
  PrayerStore? _store;
  Timer? _tick;
  DateTime _now = DateTime.now();

  @override
  void initState() {
    super.initState();
    PrayerStore.instance.then((s) {
      if (!mounted) return;
      setState(() => _store = s);
      s.addListener(_changed);
    });
    _scheduleTick();
  }

  void _changed() => setState(() {});

  // Wake on each new minute, like the home screen.
  void _scheduleTick() {
    final now = DateTime.now();
    _tick = Timer(Duration(seconds: 60 - now.second, milliseconds: -now.millisecond + 20), () {
      if (!mounted) return;
      setState(() => _now = DateTime.now());
      _scheduleTick();
    });
  }

  @override
  void dispose() {
    _tick?.cancel();
    _store?.removeListener(_changed);
    super.dispose();
  }

  Future<void> _refresh() async {
    final r = await _store!.refresh();
    if (!mounted) return;
    final text = r.error ?? (r.changed == 0 ? 'Up to date · Awqaf has no changes' : 'Updated ${r.changed} of ${r.checked} timetables from Awqaf');
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  Future<void> _pickArea() async {
    final s = _store!;
    final picked = await Navigator.of(context).push<Area>(
      MaterialPageRoute(builder: (_) => DriftMute(child: _AreaPicker(table: s.table, selected: s.area))),
    );
    if (picked != null) s.area = picked;
  }

  @override
  Widget build(BuildContext context) {
    final s = _store;
    return Scaffold(
      appBar: AppBar(
        flexibleSpace: const GlassBar(),
        title: const PageTitle('Prayer times'),
        actions: [
          if (s != null)
            s.refreshing
                ? const Padding(
                    padding: EdgeInsets.all(14),
                    child: SizedBox.square(dimension: 20, child: CircularProgressIndicator(strokeWidth: 2)),
                  )
                : IconButton(onPressed: _refresh, tooltip: 'Check Awqaf for updates', icon: const Icon(FluentIcons.arrow_clockwise_24_regular)),
          const SizedBox(width: 6),
        ],
      ),
      body: s == null ? const Center(child: CircularProgressIndicator()) : _body(s),
    );
  }

  Widget _body(PrayerStore s) {
    final g = AmnGlass.of(context);
    final next = s.table.next(s.area.zone, _now);
    return ListView(
      padding: const EdgeInsets.fromLTRB(12, 4, 12, 28),
      children: [
        _NowCard(store: s, now: _now, next: next, onArea: _pickArea),
        const SectionLabel('Through the year'),
        _Explorer(store: s, now: _now, next: next),
        Padding(
          padding: const EdgeInsets.fromLTRB(8, 20, 8, 0),
          child: Text(
            'Official timetable from Awqaf, the General Authority of Islamic Affairs, Endowments and Zakat · '
            '${s.refreshedAt == null ? 'bundled with the app' : updatedLabel(s.refreshedAt).toLowerCase()}',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 12, color: g.sub, height: 1.4),
          ),
        ),
      ],
    );
  }
}

/// The year chart and the picked day's times. It keeps the picked prayer and
/// day itself, so scrubbing rebuilds only this, never the rest of the page.
class _Explorer extends StatefulWidget {
  const _Explorer({required this.store, required this.now, required this.next});

  final PrayerStore store;
  final DateTime now;
  final PrayerTime next;

  @override
  State<_Explorer> createState() => _ExplorerState();
}

class _ExplorerState extends State<_Explorer> {
  /// The prayer on the year chart; the next prayer until one is picked.
  Prayer? _prayer;

  /// The day picked on the chart (UAE date); today when null.
  DateTime? _day;

  final _chart = YearChartController();

  @override
  void dispose() {
    _chart.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.store;
    final today = Timetable.uaeDate(widget.now);
    final day = _day ?? today;
    final prayer = _prayer ?? widget.next.prayer;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        RepaintBoundary(
          child: _YearCard(
            store: s,
            prayer: prayer,
            today: today,
            day: day,
            chart: _chart,
            onPrayer: (p) => setState(() => _prayer = p),
            onDay: (d) => setState(() => _day = d == today ? null : d),
          ),
        ),
        // The day's label, with the way back to today opposite it when another day is picked.
        Row(
          crossAxisAlignment: CrossAxisAlignment.baseline,
          textBaseline: TextBaseline.alphabetic,
          children: [
            Expanded(child: SectionLabel(day == today ? 'Today' : _longDate(day))),
            if (day != today)
              TextButton.icon(
                onPressed: () => setState(() => _day = null),
                style: TextButton.styleFrom(
                  visualDensity: VisualDensity.compact,
                  padding: const EdgeInsets.symmetric(horizontal: 8),
                  textStyle: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700),
                ),
                icon: const Icon(FluentIcons.calendar_today_16_regular, size: 15),
                label: const Text('Back to today'),
              ),
          ],
        ),
        RepaintBoundary(
          child: _DayCard(
            store: s,
            day: day,
            now: widget.now,
            next: day == today ? widget.next : null,
            chart: prayer,
            onPrayer: (p) => setState(() => _prayer = p),
          ),
        ),
      ],
    );
  }
}

const _days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
const _months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

String _shortDate(DateTime d) => '${d.day} ${_months[d.month - 1]}';
String _longDate(DateTime d) => '${_days[d.weekday - 1]}, ${d.day} ${_months[d.month - 1]} ${d.year}';

/// "17:48", or "5:48 PM" when the phone uses 12-hour time.
String formatHhmm(BuildContext context, String hhmm) {
  if (MediaQuery.alwaysUse24HourFormatOf(context)) return hhmm;
  final h = int.parse(hhmm.substring(0, 2));
  return '${h % 12 == 0 ? 12 : h % 12}:${hhmm.substring(3)} ${h < 12 ? 'AM' : 'PM'}';
}

/// Minutes after midnight, formatted like [formatHhmm].
String formatMinutes(BuildContext context, int m) =>
    formatHhmm(context, '${(m ~/ 60 % 24).toString().padLeft(2, '0')}:${(m % 60).toString().padLeft(2, '0')}');

/// "in 1 h 12 min", "in 25 min", "now".
String untilLabel(Duration d) {
  final min = (d.inSeconds / 60).ceil();
  if (min <= 0) return 'now';
  if (min < 60) return 'in $min min';
  return 'in ${min ~/ 60} h${min % 60 == 0 ? '' : ' ${min % 60} min'}';
}

/// "in 5 days", "in 3 weeks", "in 2 months".
String _inDays(int d) => d <= 1
    ? 'tomorrow'
    : d < 14
        ? 'in $d days'
        : d < 90
            ? 'in ${(d / 7).round()} weeks'
            : 'in ${(d / 30.4).round()} months';

/// A time in the display face, with AM/PM set smaller beside it on 12-hour phones.
class BigTime extends StatelessWidget {
  const BigTime(this.hhmm, {super.key, required this.size, this.color});

  final String hhmm;
  final double size;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final text = formatHhmm(context, hhmm);
    final c = color ?? AmnGlass.of(context).ink;
    final space = text.indexOf(' ');
    if (space < 0) return Text(text, style: AmnType.display(size, color: c));
    return Text.rich(TextSpan(children: [
      TextSpan(text: text.substring(0, space), style: AmnType.display(size, color: c)),
      TextSpan(text: ' ${text.substring(space + 1)}', style: AmnType.display(size * .42, color: c, letterSpacing: .5)),
    ]));
  }
}

/// "in 25 min" ahead, "2 h 10 min ago" behind.
String relativeLabel(Duration d) {
  if (d.inSeconds > 0) return untilLabel(d);
  final min = (-d.inSeconds / 60).floor();
  if (min < 1) return 'now';
  if (min < 60) return '$min min ago';
  return '${min ~/ 60} h${min % 60 == 0 ? '' : ' ${min % 60} min'} ago';
}

/// The area, the next prayer, and today as a 24-hour track. Touch the track
/// to look at any of today's prayers; let go and it returns to the next one.
class _NowCard extends StatefulWidget {
  const _NowCard({required this.store, required this.now, required this.next, required this.onArea});

  final PrayerStore store;
  final DateTime now;
  final PrayerTime next;
  final VoidCallback onArea;

  @override
  State<_NowCard> createState() => _NowCardState();
}

class _NowCardState extends State<_NowCard> {
  PrayerTime? _focus;
  Timer? _release;

  void _touch(PrayerTime? t) {
    _release?.cancel();
    if (t != null && t.at != _focus?.at) HapticFeedback.selectionClick();
    setState(() => _focus = t);
  }

  /// A beat after the finger lifts, back to the next prayer.
  void _lift() {
    _release?.cancel();
    _release = Timer(const Duration(milliseconds: 1600), () {
      if (mounted) setState(() => _focus = null);
    });
  }

  @override
  void dispose() {
    _release?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final g = AmnGlass.of(context);
    final store = widget.store;
    final shown = _focus ?? widget.next;
    return Glass(
      padding: const EdgeInsets.fromLTRB(20, 10, 20, 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // The area, tappable to change it.
          InkWell(
            onTap: widget.onArea,
            borderRadius: BorderRadius.circular(10),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(FluentIcons.location_16_filled, size: 15, color: g.sub),
                  const SizedBox(width: 6),
                  Flexible(
                    child: Text(
                      '${store.area.name} · ${store.emirate}'.toUpperCase(),
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, letterSpacing: 1, color: g.sub),
                    ),
                  ),
                  Icon(FluentIcons.chevron_down_16_regular, size: 16, color: g.sub),
                ],
              ),
            ),
          ),
          AnimatedSwitcher(
            duration: const Duration(milliseconds: 160),
            layoutBuilder: (current, previous) => Stack(alignment: Alignment.topLeft, children: [...previous, ?current]),
            child: Column(
              key: ValueKey(shown.at),
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // On one baseline: centring the boxes looks low, since the name's box makes room for descenders.
                Row(
                  crossAxisAlignment: CrossAxisAlignment.baseline,
                  textBaseline: TextBaseline.alphabetic,
                  children: [
                    Expanded(child: Text(shown.prayer.label, style: Theme.of(context).textTheme.displayMedium)),
                    BigTime(shown.hhmm, size: 44),
                  ],
                ),
                // Clears descenders (the j in Fajr): the display style's line height is under 1.
                const SizedBox(height: 4),
                Text(relativeLabel(shown.at.difference(widget.now)), style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: g.sub)),
              ],
            ),
          ),
          const SizedBox(height: 12),
          _DayTrack(
            times: store.table.day(store.area.zone, Timetable.uaeDate(widget.now)),
            now: widget.now,
            next: widget.next,
            focus: _focus,
            onTouch: _touch,
            onLift: _lift,
          ),
        ],
      ),
    );
  }
}

/// Midnight to midnight: daylight between sunrise and Maghrib, twilight either
/// side, a gap at each of the day's six times, and a knob for now. Touch or drag along it and
/// it snaps to the nearest of the day's times.
class _DayTrack extends StatelessWidget {
  const _DayTrack({required this.times, required this.now, required this.next, required this.focus, required this.onTouch, required this.onLift});

  final List<PrayerTime> times;
  final DateTime now;
  final PrayerTime next;
  final PrayerTime? focus;
  final ValueChanged<PrayerTime?> onTouch;
  final VoidCallback onLift;

  static const _height = 52.0;

  @override
  Widget build(BuildContext context) {
    final g = AmnGlass.of(context);
    final scheme = Theme.of(context).colorScheme;
    final h24 = MediaQuery.alwaysUse24HourFormatOf(context);
    final midnight = Timetable.uaeDate(now).subtract(Timetable.uaeOffset);
    double f(DateTime t) => (t.difference(midnight).inMinutes / 1440).clamp(0.0, 1.0);
    final marked = focus ?? (next.at.isBefore(midnight.add(const Duration(days: 1))) ? next : null);
    return LayoutBuilder(builder: (context, box) {
      PrayerTime nearest(double dx) {
        final at = dx / box.maxWidth;
        return times.reduce((a, b) => (f(a.at) - at).abs() <= (f(b.at) - at).abs() ? a : b);
      }

      return GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTapDown: (d) => onTouch(nearest(d.localPosition.dx)),
        onTapUp: (_) => onLift(),
        onTapCancel: onLift,
        onHorizontalDragStart: (d) => onTouch(nearest(d.localPosition.dx)),
        onHorizontalDragUpdate: (d) => onTouch(nearest(d.localPosition.dx)),
        onHorizontalDragEnd: (_) => onLift(),
        onHorizontalDragCancel: onLift,
        child: CustomPaint(
          size: Size(box.maxWidth, _height),
          painter: _TrackPainter(
            fajr: f(times[0].at),
            sunrise: f(times[1].at),
            maghrib: f(times[4].at),
            isha: f(times[5].at),
            ticks: [for (final t in times) f(t.at)],
            marked: marked == null ? null : f(marked.at),
            touched: focus != null,
            now: f(now),
            hours: h24 ? const ['00', '06', '12', '18', '24'] : const ['12 AM', '6 AM', '12 PM', '6 PM', '12 AM'],
            base: g.ink.withValues(alpha: .10),
            accent: scheme.primary,
            ink: g.ink,
            sub: g.sub,
            ring: scheme.surface,
          ),
        ),
      );
    });
  }
}

class _TrackPainter extends CustomPainter {
  _TrackPainter({
    required this.fajr,
    required this.sunrise,
    required this.maghrib,
    required this.isha,
    required this.ticks,
    required this.marked,
    required this.touched,
    required this.now,
    required this.hours,
    required this.base,
    required this.accent,
    required this.ink,
    required this.sub,
    required this.ring,
  });

  final double fajr, sunrise, maghrib, isha, now;

  /// The prayer under the caret: the touched one, else the next.
  final double? marked;
  final bool touched;
  final List<double> ticks;
  final List<String> hours;
  final Color base, accent, ink, sub, ring;

  @override
  void paint(Canvas canvas, Size size) {
    const h = 10.0, top = 12.0;
    double x(double f) => f * size.width;
    final track = RRect.fromRectAndRadius(Rect.fromLTWH(0, top, size.width, h), const Radius.circular(h / 2));
    canvas.save();
    canvas.clipRRect(track);
    canvas.drawRRect(track, Paint()..color = base);
    // Twilight fades in from Fajr and out to Isha; full daylight between.
    final day = Rect.fromLTRB(x(fajr), top, x(isha), top + h);
    canvas.drawRect(
      day,
      Paint()
        ..shader = LinearGradient(
          colors: [accent.withValues(alpha: .15), accent.withValues(alpha: .55), accent.withValues(alpha: .55), accent.withValues(alpha: .15)],
          stops: [0, (sunrise - fajr) / (isha - fajr), (maghrib - fajr) / (isha - fajr), 1],
        ).createShader(day),
    );
    // Gaps at each of the day's times, sunrise included.
    for (final t in ticks) {
      canvas.drawRect(Rect.fromCenter(center: Offset(x(t), top + h / 2), width: 2, height: h), Paint()..color = ring.withValues(alpha: .9));
    }
    canvas.restore();

    // Now: a knob riding the track.
    final c = Offset(x(now).clamp(7, size.width - 7).toDouble(), top + h / 2);
    canvas.drawCircle(c, 8, Paint()..color = ring);
    canvas.drawCircle(c, 6, Paint()..color = ink);

    // The marked prayer: a caret under it; while touched, also a ringed bead on the track.
    if (marked != null) {
      final mx = x(marked!);
      if (touched) {
        canvas.drawCircle(Offset(mx, top + h / 2), 9, Paint()..color = ring);
        canvas.drawCircle(Offset(mx, top + h / 2), 7, Paint()..color = accent);
      }
      final p = Path()
        ..moveTo(mx, top + h + 3)
        ..lineTo(mx - 4, top + h + 9)
        ..lineTo(mx + 4, top + h + 9)
        ..close();
      canvas.drawPath(p, Paint()..color = accent);
    }

    // Hours.
    for (final (i, label) in hours.indexed) {
      final t = TextPainter(
        text: TextSpan(text: label, style: TextStyle(fontSize: 10, fontWeight: FontWeight.w600, color: sub)),
        textDirection: TextDirection.ltr,
      )..layout();
      final dx = (x(i / 4) - t.width / 2).clamp(0, size.width - t.width).toDouble();
      t.paint(canvas, Offset(dx, size.height - t.height));
    }
  }

  @override
  bool shouldRepaint(_TrackPainter old) =>
      old.now != now || old.fajr != fajr || old.isha != isha || old.accent != accent || old.ink != ink || old.marked != marked || old.touched != touched || old.hours != hours;
}

/// One prayer through the year: which way it's moving and when it turns.
class _YearCard extends StatelessWidget {
  const _YearCard({
    required this.store,
    required this.prayer,
    required this.today,
    required this.day,
    required this.chart,
    required this.onPrayer,
    required this.onDay,
  });

  final PrayerStore store;
  final Prayer prayer;
  final DateTime today;
  final DateTime day;
  final YearChartController chart;
  final ValueChanged<Prayer> onPrayer;
  final ValueChanged<DateTime> onDay;

  @override
  Widget build(BuildContext context) {
    final g = AmnGlass.of(context);
    final series = store.table.series(store.area.zone, prayer);
    final turns = series.turns;
    final idx = Timetable.dayIndex(day.month, day.day);
    final turn = Turn.next(turns, idx);
    final after = turn.latest ? 'earlier' : 'later';
    final turnDate = DateTime.utc(2028).add(Duration(days: turn.from));
    final lastDate = DateTime.utc(2028).add(Duration(days: turn.to));
    final time = formatMinutes(context, turn.minutes);
    final detailStyle = TextStyle(fontSize: 14, height: 1.35, color: g.sub);
    final (headline, detail) = turn.holds(idx)
        ? (
            '${prayer.label} is at its ${turn.latest ? 'latest' : 'earliest'}',
            'Stays at $time until ${_shortDate(lastDate)}, then gets $after.',
          )
        : (
            '${prayer.label} is getting ${turn.latest ? 'later' : 'earlier'}',
            '${turn.latest ? 'Latest' : 'Earliest'} $time from ${_shortDate(turnDate)}, ${_inDays((turn.from - idx) % 366)}. Then it gets $after.',
          );
    return Glass(
      padding: const EdgeInsets.fromLTRB(0, 14, 0, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 14),
            child: Row(
              children: [
                for (final p in Prayer.values)
                  Padding(
                    padding: const EdgeInsets.only(right: 6),
                    child: ChoiceChip(
                      label: Text(p.label),
                      selected: p == prayer,
                      showCheckmark: false,
                      onSelected: (_) => onPrayer(p),
                    ),
                  ),
              ],
            ),
          ),
          // The headline, with a reset opposite it while the chart is zoomed.
          Padding(
            padding: const EdgeInsets.fromLTRB(18, 16, 8, 4),
            child: Row(
              children: [
                // One line, shrinking a little rather than wrapping, for the same reason.
                Expanded(
                  child: FittedBox(
                    fit: BoxFit.scaleDown,
                    alignment: Alignment.centerLeft,
                    child: Text(headline, maxLines: 1, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
                  ),
                ),
                ListenableBuilder(
                  listenable: chart,
                  builder: (context, _) => chart.zoomed
                      ? IconButton(
                          onPressed: chart.reset,
                          tooltip: 'Show the whole year',
                          visualDensity: VisualDensity.compact,
                          icon: const Icon(FluentIcons.arrow_reset_24_regular, size: 22),
                        )
                      : const SizedBox(height: 40),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 18),
            // Always two lines tall, so the chart below holds still while the
            // text changes under a finger. The hidden line break sets the height
            // at the current text scale.
            child: Stack(
              children: [
                Opacity(opacity: 0, child: Text('\n', style: detailStyle)),
                Text(detail, maxLines: 2, overflow: TextOverflow.ellipsis, style: detailStyle),
              ],
            ),
          ),
          const SizedBox(height: 8),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8),
            child: Semantics(
              label: '${prayer.label} through ${day.year}. $headline. $detail',
              child: YearChart(
                controller: chart,
                year: day.year,
                series: series,
                today: today,
                selected: day,
                onSelected: onDay,
                format: (m) => formatMinutes(context, m),
                h24: MediaQuery.alwaysUse24HourFormatOf(context),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// A day's six times, each with where it's heading over the next week.
class _DayCard extends StatelessWidget {
  const _DayCard({required this.store, required this.day, required this.now, required this.next, required this.chart, required this.onPrayer});

  final PrayerStore store;
  final DateTime day;
  final DateTime now;

  /// Today's next prayer, highlighted; null on other days.
  final PrayerTime? next;

  /// The prayer on the year chart.
  final Prayer chart;
  final ValueChanged<Prayer> onPrayer;

  static String _week(int delta) => switch (delta) {
        0 => 'Same next week',
        > 0 => '$delta min later next week',
        _ => '${-delta} min earlier next week',
      };

  @override
  Widget build(BuildContext context) {
    final g = AmnGlass.of(context);
    final scheme = Theme.of(context).colorScheme;
    final zone = store.area.zone;
    final times = store.table.day(zone, day);
    final week = store.table.day(zone, day.add(const Duration(days: 7)));
    return GroupCard(children: [
      const SizedBox(height: 6),
      for (final (i, t) in times.indexed)
        () {
          final isNext = next != null && t.at == next!.at;
          final past = next != null && !t.at.isAfter(now);
          final delta = week[i].at.difference(t.at.add(const Duration(days: 7))).inMinutes;
          final color = isNext ? scheme.primary : (past ? g.sub : g.ink);
          return ListTile(
            onTap: () => onPrayer(t.prayer),
            contentPadding: const EdgeInsets.fromLTRB(18, 0, 18, 0),
            leading: Container(
              width: 8,
              height: 8,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: t.prayer == chart ? scheme.primary : Colors.transparent,
                border: Border.all(color: t.prayer == chart ? scheme.primary : g.sub.withValues(alpha: .5), width: 1.5),
              ),
            ),
            minLeadingWidth: 8,
            title: Text(
              t.prayer.label,
              style: TextStyle(fontSize: 16, fontWeight: isNext ? FontWeight.w800 : FontWeight.w600, color: t.prayer.isPrayer ? color : g.sub),
            ),
            subtitle: Text(_week(delta), style: TextStyle(fontSize: 12, color: g.sub)),
            trailing: BigTime(t.hhmm, size: 26, color: color),
          );
        }(),
      const SizedBox(height: 6),
    ]);
  }
}

/// Search and pick an area, grouped by emirate.
class _AreaPicker extends StatefulWidget {
  const _AreaPicker({required this.table, required this.selected});

  final Timetable table;
  final Area selected;

  @override
  State<_AreaPicker> createState() => _AreaPickerState();
}

class _AreaPickerState extends State<_AreaPicker> {
  String _query = '';

  bool _matches(Area a) {
    if (_query.isEmpty) return true;
    final q = _query.toLowerCase();
    return a.name.toLowerCase().contains(q) || a.nameAr.contains(_query) || widget.table.emirates[a.emirate]!.toLowerCase().contains(q);
  }

  @override
  Widget build(BuildContext context) {
    final t = widget.table;
    final scheme = Theme.of(context).colorScheme;
    final groups = <int, List<Area>>{};
    for (final a in t.areas.where(_matches)) {
      groups.putIfAbsent(a.emirate, () => []).add(a);
    }
    for (final list in groups.values) {
      list.sort((a, b) => a.name.compareTo(b.name));
    }
    return Scaffold(
      appBar: AppBar(flexibleSpace: const GlassBar(), title: const PageTitle('Choose area')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(12, 4, 12, 24),
        children: [
          TextField(
            autofocus: true,
            decoration: const InputDecoration(prefixIcon: Icon(FluentIcons.search_24_regular), hintText: 'Search areas, in English or Arabic'),
            onChanged: (v) => setState(() => _query = v.trim()),
          ),
          if (groups.isEmpty)
            const Padding(padding: EdgeInsets.all(24), child: Center(child: Text('No areas match'))),
          for (final e in groups.entries) ...[
            SectionLabel(t.emirates[e.key]!),
            GroupCard(children: [
              for (final a in e.value)
                ListTile(
                  dense: true,
                  onTap: () => Navigator.of(context).pop(a),
                  title: Text(a.name, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
                  subtitle: Text(a.nameAr),
                  trailing: a.id == widget.selected.id ? Icon(FluentIcons.checkmark_24_regular, color: scheme.primary) : null,
                ),
            ]),
          ],
        ],
      ),
    );
  }
}
