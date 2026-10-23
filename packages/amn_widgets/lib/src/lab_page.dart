import 'package:amn_ui/amn_ui.dart';
import 'package:flutter/material.dart';

import 'summary_engine.dart';
import 'ui.dart';

/// Page that renders every widget size through the real preview,
/// with overrides for condition, hour, dark mode and units.
///
/// Open it from the command line with any state, for example:
/// adb shell am start -n dev.amaan.amn/.MainActivity --es route "/widget/lab?cond=rain&hour=21&dark=1&kind=weather&grid=oneui"
/// The prayer widget takes the time of day in minutes: "/widget/lab?kind=prayer&min=1100"
class WidgetLabPage extends StatefulWidget {
  const WidgetLabPage({super.key, this.query = const {}});

  final Map<String, String> query;

  @override
  State<WidgetLabPage> createState() => _WidgetLabPageState();
}

const _conditions = ['clear', 'partly', 'cloudy', 'rain', 'storm', 'snow', 'fog'];
const _clockCities = {
  'Asia/Tokyo': ('Tokyo', 'Japan', 35.68, 139.69),
  'America/New_York': ('New York', 'United States', 40.71, -74.01),
  'Europe/London': ('London', 'United Kingdom', 51.51, -0.13),
  'Asia/Dubai': ('Dubai', 'United Arab Emirates', 25.20, 55.27),
};

class _WidgetLabPageState extends State<WidgetLabPage> {
  late String _condition = _pick(widget.query['cond'], _conditions, 'partly');
  late double _hour = (int.tryParse(widget.query['hour'] ?? '') ?? DateTime.now().hour).clamp(0, 23).toDouble();
  late bool? _dark = switch (widget.query['dark']) { '1' => true, '0' => false, _ => null };
  late bool _fahrenheit = widget.query['f'] == '1';
  late bool _h24 = widget.query['h24'] != '0';
  late bool _seconds = widget.query['secs'] == '1';
  late bool _clockWeather = widget.query['cw'] != '0';
  late String _zone = _pick(widget.query['city'], _clockCities.keys.toList(), 'Asia/Tokyo');
  late String _kind = _pick(widget.query['kind'], ['weather', 'clock', 'both', 'prayer'], 'both');
  // Prayer widget: minutes after midnight, UAE time.
  late double _minute = (int.tryParse(widget.query['min'] ?? '') ?? DateTime.now().hour * 60 + DateTime.now().minute).clamp(0, 1439).toDouble();
  late bool _oneUi = widget.query['grid'] == 'oneui';
  late final bool _showEngine = widget.query['engine'] != '0';
  late String _refresh = _pick(widget.query['rf'], ['idle', 'busy', 'done', 'offline'], 'idle');
  // Same rotation as the widget: a new seed every two hours.
  late int _seed = int.tryParse(widget.query['seed'] ?? '') ?? DateTime.now().millisecondsSinceEpoch ~/ 7200000;

  static String _pick(String? v, List<String> allowed, String fallback) => allowed.contains(v) ? v! : fallback;

  Map<String, Object?> get _debug => {
        'condition': _condition,
        'hour': _hour.round(),
        'h24': _h24,
        'seed': _seed,
        'refresh': _refresh,
        'minute': _minute.round(),
        if (_dark != null) 'dark': _dark,
      };

  Map<String, Object?> get _weatherSettings => {
        'useGps': false,
        'cityName': 'Berlin',
        'lat': 52.52,
        'lon': 13.41,
        'fahrenheit': _fahrenheit,
        'inches': false,
      };

  Map<String, Object?> get _clockSettings {
    final c = _clockCities[_zone]!;
    return {
      'cityName': c.$1,
      'countryName': c.$2,
      'timeZone': _zone,
      'lat': c.$3,
      'lon': c.$4,
      'h24': _h24,
      'seconds': _seconds,
      'showWeather': _clockWeather,
    };
  }

  @override
  Widget build(BuildContext context) {
    final h = _hour.round();
    return Scaffold(
      appBar: AppBar(flexibleSpace: const GlassBar(), title: const PageTitle('Widget lab')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(12, 0, 12, 32),
        children: [
          Wrap(spacing: 6, runSpacing: 6, children: [
            for (final c in _conditions)
              ChoiceChip(label: Text(c), selected: c == _condition, showCheckmark: false, onSelected: (_) => setState(() => _condition = c)),
          ]),
          if (_kind == 'prayer')
            Row(children: [
              SizedBox(width: 56, child: Text('${(_minute ~/ 60).toString().padLeft(2, '0')}:${(_minute.round() % 60).toString().padLeft(2, '0')}')),
              Expanded(child: Slider(value: _minute, min: 0, max: 1435, divisions: 287, onChanged: (v) => setState(() => _minute = v))),
            ])
          else
            Row(children: [
              SizedBox(width: 56, child: Text('${h.toString().padLeft(2, '0')}:00')),
              Expanded(child: Slider(value: _hour, min: 0, max: 23, divisions: 23, onChanged: (v) => setState(() => _hour = v))),
            ]),
          Wrap(spacing: 6, runSpacing: 6, crossAxisAlignment: WrapCrossAlignment.center, children: [
            SegmentedButton<bool?>(
              showSelectedIcon: false,
              segments: const [
                ButtonSegment(value: null, label: Text('Phone')),
                ButtonSegment(value: false, label: Text('Light')),
                ButtonSegment(value: true, label: Text('Dark')),
              ],
              selected: {_dark},
              onSelectionChanged: (v) => setState(() => _dark = v.first),
            ),
            FilterChip(label: const Text('°F'), selected: _fahrenheit, onSelected: (v) => setState(() => _fahrenheit = v)),
            FilterChip(label: const Text('24h'), selected: _h24, onSelected: (v) => setState(() => _h24 = v)),
            FilterChip(label: const Text('Seconds'), selected: _seconds, onSelected: (v) => setState(() => _seconds = v)),
            FilterChip(label: const Text('Clock weather'), selected: _clockWeather, onSelected: (v) => setState(() => _clockWeather = v)),
          ]),
          const SizedBox(height: 6),
          Wrap(spacing: 6, runSpacing: 6, children: [
            for (final e in _clockCities.entries)
              ChoiceChip(label: Text(e.value.$1), selected: e.key == _zone, showCheckmark: false, onSelected: (_) => setState(() => _zone = e.key)),
            for (final k in ['both', 'weather', 'clock', 'prayer'])
              ChoiceChip(label: Text(k), selected: k == _kind, showCheckmark: false, onSelected: (_) => setState(() => _kind = k)),
            FilterChip(label: const Text('One UI grid'), selected: _oneUi, onSelected: (v) => setState(() => _oneUi = v)),
          ]),
          const SizedBox(height: 6),
          Row(children: [
            const Text('Refresh  '),
            Expanded(
              child: SegmentedButton<String>(
                showSelectedIcon: false,
                segments: const [
                  ButtonSegment(value: 'idle', label: Text('Idle')),
                  ButtonSegment(value: 'busy', label: Text('Updating')),
                  ButtonSegment(value: 'done', label: Text('Just now')),
                  ButtonSegment(value: 'offline', label: Text('Offline')),
                ],
                selected: {_refresh},
                onSelectionChanged: (v) => setState(() => _refresh = v.first),
              ),
            ),
          ]),
          if ((_kind == 'both' || _kind == 'weather') && _showEngine) ...[
            const SizedBox(height: 20),
            SummaryEngine(
              condition: _condition,
              hour: h,
              h24: _h24,
              fahrenheit: _fahrenheit,
              seed: _seed,
              onSeed: (s) => setState(() => _seed = s),
            ),
          ],
          if (_kind == 'prayer')
            for (final s in [...prayerSizes, ...oneUiPrayerSizes])
              _Slot(
                label: 'Prayer ${s.label}${s.hostScale < 1 ? ' (One UI)' : ''}',
                // A 5×1 is wider than the phone's screen; scroll sideways to see all of it.
                child: SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: WidgetPreview(kind: 'prayer', widgetId: -1, size: s, settings: const {}, debug: _debug),
                ),
              ),
          if (_kind == 'both' || _kind == 'weather')
            for (final s in _oneUi ? oneUiWeatherSizes : weatherSizes) _Slot(label: 'Weather ${s.label}', child: WidgetPreview(kind: 'weather', widgetId: -1, size: s, settings: _weatherSettings, debug: _debug)),
          if (_kind == 'both' || _kind == 'clock')
            for (final s in _oneUi ? oneUiClockSizes : clockSizes) _Slot(label: 'Clock ${s.label}', child: WidgetPreview(kind: 'clock', widgetId: -1, size: s, settings: _clockSettings, debug: _debug)),
        ],
      ),
    );
  }
}

class _Slot extends StatelessWidget {
  const _Slot({required this.label, required this.child});

  final String label;
  final Widget child;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(top: 18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: Theme.of(context).textTheme.labelMedium),
            const SizedBox(height: 6),
            child,
          ],
        ),
      );
}
