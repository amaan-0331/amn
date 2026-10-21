import 'package:flutter/material.dart';

import 'ui.dart';

/// Debug-only page that renders every widget size through the real preview,
/// with overrides for condition, hour, dark mode and units.
///
/// Open it from the command line with any state, for example:
/// adb shell am start -n dev.amaan.amn/.MainActivity --es route "/widget/lab?cond=rain&hour=21&dark=1&kind=weather"
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
  late String _zone = _pick(widget.query['city'], _clockCities.keys.toList(), 'Asia/Tokyo');
  late String _kind = _pick(widget.query['kind'], ['weather', 'clock', 'both'], 'both');

  static String _pick(String? v, List<String> allowed, String fallback) => allowed.contains(v) ? v! : fallback;

  Map<String, Object?> get _debug => {
        'condition': _condition,
        'hour': _hour.round(),
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
    return {'cityName': c.$1, 'countryName': c.$2, 'timeZone': _zone, 'lat': c.$3, 'lon': c.$4, 'h24': _h24, 'seconds': _seconds};
  }

  @override
  Widget build(BuildContext context) {
    final h = _hour.round();
    return Scaffold(
      appBar: AppBar(title: const PageTitle('Widget lab')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(12, 0, 12, 32),
        children: [
          Wrap(spacing: 6, runSpacing: 6, children: [
            for (final c in _conditions)
              ChoiceChip(label: Text(c), selected: c == _condition, showCheckmark: false, onSelected: (_) => setState(() => _condition = c)),
          ]),
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
          ]),
          const SizedBox(height: 6),
          Wrap(spacing: 6, runSpacing: 6, children: [
            for (final e in _clockCities.entries)
              ChoiceChip(label: Text(e.value.$1), selected: e.key == _zone, showCheckmark: false, onSelected: (_) => setState(() => _zone = e.key)),
            for (final k in ['both', 'weather', 'clock'])
              ChoiceChip(label: Text(k), selected: k == _kind, showCheckmark: false, onSelected: (_) => setState(() => _kind = k)),
          ]),
          if (_kind != 'clock')
            for (final s in weatherSizes) _Slot(label: 'Weather ${s.label}', child: WidgetPreview(kind: 'weather', widgetId: -1, size: s, settings: _weatherSettings, debug: _debug)),
          if (_kind != 'weather')
            for (final s in clockSizes) _Slot(label: 'Clock ${s.label}', child: WidgetPreview(kind: 'clock', widgetId: -1, size: s, settings: _clockSettings, debug: _debug)),
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
