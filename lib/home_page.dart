import 'dart:async';

import 'package:amn_ui/amn_ui.dart';
import 'package:amn_widgets/amn_widgets.dart';
import 'package:fluentui_system_icons/fluentui_system_icons.dart';
import 'package:flutter/material.dart';

/// Home: a greeting and the time over the full sky, the widgets you've
/// placed, and the way into the widget lab.
class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  late final AppLifecycleListener _life;
  Timer? _tick;
  DateTime _now = DateTime.now();
  PlacedWidgets? _placed;
  Map<String, ZoneInfo> _zones = const {};

  @override
  void initState() {
    super.initState();
    _life = AppLifecycleListener(onResume: _refresh);
    _scheduleTick();
    _refresh();
  }

  // Wake on each new minute, not every few seconds.
  void _scheduleTick() {
    _tick?.cancel();
    final now = DateTime.now();
    _tick = Timer(Duration(seconds: 60 - now.second, milliseconds: -now.millisecond + 20), () {
      if (!mounted) return;
      setState(() => _now = DateTime.now());
      _loadZones();
      _scheduleTick();
    });
  }

  Future<void> _refresh() async {
    _scheduleTick();
    final placed = await WidgetBridge.listWidgets();
    if (!mounted) return;
    setState(() {
      _now = DateTime.now();
      _placed = placed;
    });
    await _loadZones();
  }

  Future<void> _loadZones() async {
    final zones = [
      for (final w in _placed?.widgets ?? const <PlacedWidget>[])
        if (w.timeZone != null) w.timeZone!,
    ];
    if (zones.isEmpty) return;
    final info = await WidgetBridge.describeZones(zones, h24: _h24);
    if (mounted) setState(() => _zones = info);
  }

  bool get _h24 => MediaQuery.alwaysUse24HourFormatOf(context);

  @override
  void dispose() {
    _tick?.cancel();
    _life.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final g = AmnGlass.of(context);
    final text = Theme.of(context).textTheme;
    return Scaffold(
      body: SafeArea(
        child: CustomScrollView(
          slivers: [
            SliverFillRemaining(
              hasScrollBody: false,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(20, 12, 20, 20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('AMN', style: AmnType.display(22, color: g.ink, letterSpacing: 1.4)),
                    const SizedBox(height: 28),
                    Text(_greeting(_now.hour), style: text.titleSmall?.copyWith(color: g.sub, fontWeight: FontWeight.w600)),
                    Text(_time(_now, _h24), style: text.displayLarge),
                    const SizedBox(height: 4),
                    Text(_date(_now), style: text.bodyLarge?.copyWith(color: g.sub, fontWeight: FontWeight.w500)),
                    const SizedBox(height: 16),
                    const _SkyChip(),
                    const Spacer(),
                    const SizedBox(height: 24),
                    _WidgetsCard(placed: _placed, zones: _zones),
                    const SizedBox(height: 12),
                    const _LabCard(),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  static String _greeting(int h) => h < 5
      ? 'Good night'
      : h < 12
          ? 'Good morning'
          : h < 17
              ? 'Good afternoon'
              : h < 22
                  ? 'Good evening'
                  : 'Good night';

  static String _time(DateTime t, bool h24) {
    final m = t.minute.toString().padLeft(2, '0');
    if (h24) return '${t.hour.toString().padLeft(2, '0')}:$m';
    final h = t.hour % 12 == 0 ? 12 : t.hour % 12;
    return '$h:$m';
  }

  static const _days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
  static const _months = [
    'January', 'February', 'March', 'April', 'May', 'June', //
    'July', 'August', 'September', 'October', 'November', 'December',
  ];

  static String _date(DateTime t) => '${_days[t.weekday - 1]}, ${t.day} ${_months[t.month - 1]}';
}

/// "Drifting through rain at dusk", with a dot in the near glow's colour.
class _SkyChip extends StatelessWidget {
  const _SkyChip();

  @override
  Widget build(BuildContext context) {
    final sky = Drift.of(context).current;
    final g = AmnGlass.of(context);
    return Glass(
      radius: 999,
      blur: false,
      padding: const EdgeInsets.fromLTRB(10, 6, 12, 6),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          AnimatedContainer(
            duration: const Duration(seconds: 2),
            width: 9,
            height: 9,
            decoration: BoxDecoration(
              color: sky.top,
              shape: BoxShape.circle,
              border: Border.all(color: g.edge, width: 1.5),
            ),
          ),
          const SizedBox(width: 8),
          AnimatedSwitcher(
            duration: const Duration(milliseconds: 600),
            child: Text(
              'Drifting through ${sky.name}',
              key: ValueKey(sky.name),
              style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: g.ink),
            ),
          ),
        ],
      ),
    );
  }
}

class _WidgetsCard extends StatelessWidget {
  const _WidgetsCard({required this.placed, required this.zones});

  final PlacedWidgets? placed;
  final Map<String, ZoneInfo> zones;

  @override
  Widget build(BuildContext context) {
    final g = AmnGlass.of(context);
    final list = placed?.widgets ?? const <PlacedWidget>[];
    return Glass(
      blur: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(18, 16, 18, 4),
            child: Text(
              'YOUR WIDGETS',
              style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, letterSpacing: 1, color: g.sub),
            ),
          ),
          if (placed != null && list.isEmpty)
            ListTile(
              leading: const Icon(FluentIcons.apps_add_in_24_regular),
              title: const Text('No widgets yet'),
              subtitle: const Text('Long-press your home screen, tap Widgets, then find AMN.'),
            ),
          for (final (i, w) in list.indexed) ...[
            if (i > 0) const Divider(indent: 72, endIndent: 18),
            _WidgetRow(w: w, fahrenheit: placed!.fahrenheit, zone: zones[w.timeZone]),
          ],
          const SizedBox(height: 8),
        ],
      ),
    );
  }
}

class _WidgetRow extends StatelessWidget {
  const _WidgetRow({required this.w, required this.fahrenheit, this.zone});

  final PlacedWidget w;
  final bool fahrenheit;
  final ZoneInfo? zone;

  @override
  Widget build(BuildContext context) {
    final g = AmnGlass.of(context);
    final scheme = Theme.of(context).colorScheme;
    final weather = w.kind == 'weather';
    final temp = w.temperature(fahrenheit: fahrenheit);
    final place = w.place ?? (weather ? 'Current location' : 'No city yet');
    final subtitle = weather
        ? [place, if (temp == null) 'waiting for weather'].join(' · ')
        : [place, if (zone != null && zone!.offset.isNotEmpty) zone!.offset, ?temp].join(' · ');
    final value = weather ? temp : zone?.time;
    return ListTile(
      onTap: () => WidgetBridge.openEditor(w),
      contentPadding: const EdgeInsets.fromLTRB(16, 2, 12, 2),
      leading: Container(
        width: 40,
        height: 40,
        decoration: BoxDecoration(color: g.tint, shape: BoxShape.circle, border: Border.all(color: g.edge)),
        child: Icon(weather ? _icon(w.condition, w.night) : FluentIcons.globe_clock_24_regular, size: 22, color: g.ink),
      ),
      title: Text(weather ? 'Weather' : 'City clock', style: const TextStyle(fontWeight: FontWeight.w700)),
      subtitle: Text(subtitle, maxLines: 1, overflow: TextOverflow.ellipsis),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (value != null) Text(value, style: AmnType.display(24, color: g.ink)),
          const SizedBox(width: 4),
          Icon(FluentIcons.chevron_right_20_regular, size: 20, color: scheme.primary),
        ],
      ),
    );
  }

  static IconData _icon(String? condition, bool night) => switch (condition) {
        'clear' => night ? FluentIcons.weather_moon_24_regular : FluentIcons.weather_sunny_24_regular,
        'partly' => night ? FluentIcons.weather_partly_cloudy_night_24_regular : FluentIcons.weather_partly_cloudy_day_24_regular,
        'cloudy' => FluentIcons.weather_cloudy_24_regular,
        'rain' => FluentIcons.weather_rain_24_regular,
        'storm' => FluentIcons.weather_thunderstorm_24_regular,
        'snow' => FluentIcons.weather_snow_24_regular,
        'fog' => FluentIcons.weather_fog_24_regular,
        _ => FluentIcons.weather_partly_cloudy_day_24_regular,
      };
}

class _LabCard extends StatelessWidget {
  const _LabCard();

  @override
  Widget build(BuildContext context) {
    final g = AmnGlass.of(context);
    return Glass(
      blur: false,
      padding: const EdgeInsets.fromLTRB(18, 14, 14, 14),
      child: Row(
        children: [
          Icon(FluentIcons.beaker_24_regular, color: g.ink),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Widget lab', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
                Text('Every size, any sky', style: TextStyle(fontSize: 12.5, color: g.sub)),
              ],
            ),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pushNamed('/widget/lab'),
            child: const Text('Open'),
          ),
        ],
      ),
    );
  }
}
