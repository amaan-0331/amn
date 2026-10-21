import 'dart:async';

import 'package:flutter/material.dart';

import 'bridge.dart';
import 'geocoding.dart';
import 'ui.dart';

/// Edit page for a clock widget. See the spec's "Edit pages" section.
class ClockConfigPage extends StatefulWidget {
  const ClockConfigPage({super.key, required this.widgetId, required this.adding});

  final int widgetId;
  final bool adding;

  @override
  State<ClockConfigPage> createState() => _ClockConfigPageState();
}

class _ClockConfigPageState extends State<ClockConfigPage> {
  bool _loaded = false;
  City? _city;
  bool _h24 = true;
  bool _seconds = false;
  bool _showWeather = true;
  int? _weatherAt;
  PreviewSize _size = clockSizes[2];
  ZoneInfo? _zone;
  Timer? _tick;

  @override
  void initState() {
    super.initState();
    _load();
    // Keeps the selected city's time current while the page is open.
    _tick = Timer.periodic(const Duration(seconds: 20), (_) => _describe());
  }

  @override
  void dispose() {
    _tick?.cancel();
    super.dispose();
  }

  Future<void> _load() async {
    final m = await WidgetBridge.getSettings(widget.widgetId, 'clock');
    if (!mounted) return;
    setState(() {
      final tz = m['timeZone'] as String?;
      final name = m['cityName'] as String?;
      if (tz != null && name != null) {
        _city = City(
          name: name,
          country: m['countryName'] as String? ?? '',
          lat: (m['lat'] as num?)?.toDouble() ?? 0,
          lon: (m['lon'] as num?)?.toDouble() ?? 0,
          timeZone: tz,
        );
      }
      _h24 = m['h24'] as bool? ?? true;
      _seconds = m['seconds'] as bool? ?? false;
      _showWeather = m['showWeather'] as bool? ?? true;
      _weatherAt = m['fetchedAt'] as int?;
      _loaded = true;
    });
    await _describe();
  }

  Future<void> _describe() async {
    final tz = _city?.timeZone;
    if (tz == null) return;
    final info = await WidgetBridge.describeZones([tz], h24: _h24);
    if (mounted) setState(() => _zone = info[tz]);
  }

  void _select(City c) {
    setState(() {
      _city = c;
      _zone = null;
    });
    _describe();
  }

  Map<String, Object?> get _settings => {
        'cityName': _city?.name,
        'countryName': _city?.country,
        'timeZone': _city?.timeZone,
        'lat': _city?.lat,
        'lon': _city?.lon,
        'h24': _h24,
        'seconds': _seconds,
        'showWeather': _showWeather,
      };

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final city = _city;
    final zone = _zone;
    return Scaffold(
      appBar: AppBar(
        leading: CloseButtonFor(adding: widget.adding),
        title: PageTitle(widget.adding ? 'Add clock widget' : 'Clock widget'),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(12, 0, 12, 24),
        children: [
          PreviewBand(
            preview: WidgetPreview(kind: 'clock', widgetId: widget.widgetId, size: _size, settings: _settings),
            sizes: clockSizes,
            selected: _size,
            onSelected: (s) => setState(() => _size = s),
            note: _loaded && city == null ? 'Pick a city below.' : null,
          ),
          const SectionLabel('City'),
          if (city != null) ...[
            GroupCard(children: [
              ListTile(
                title: Text(city.name.toUpperCase(), style: const TextStyle(fontFamily: 'BarlowCondensed', package: 'amn_widgets', fontWeight: FontWeight.w700, fontSize: 26)),
                subtitle: Text([if (city.country.isNotEmpty) city.country, if (zone != null) zone.gmt].join(' · ')),
                trailing: zone == null
                    ? null
                    : Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          Text(zone.time, style: const TextStyle(fontFamily: 'BarlowCondensed', package: 'amn_widgets', fontWeight: FontWeight.w700, fontSize: 24)),
                          Text(zone.offset, style: theme.textTheme.bodySmall),
                        ],
                      ),
              ),
            ]),
            const SizedBox(height: 8),
          ],
          CityPicker(selected: city, hint: 'Search for a city', showZones: true, h24: _h24, onSelected: _select),
          const SectionLabel('Format'),
          GroupCard(children: [
            ListTile(
              title: const Text('Time format'),
              subtitle: const Text("Starts from your phone's setting"),
              trailing: SegmentedButton<bool>(
                showSelectedIcon: false,
                segments: const [ButtonSegment(value: true, label: Text('24h')), ButtonSegment(value: false, label: Text('12h'))],
                selected: {_h24},
                onSelectionChanged: (v) {
                  setState(() => _h24 = v.first);
                  _describe();
                },
              ),
            ),
            SwitchListTile(
              title: const Text('Show weather'),
              subtitle: Text(_showWeather && _weatherAt != null
                  ? '${updatedLabel(_weatherAt)} · updates every hour · Open-Meteo'
                  : "The city's weather art, colors and temperature · updates every hour"),
              value: _showWeather,
              onChanged: (v) => setState(() => _showWeather = v),
            ),
            SwitchListTile(
              title: const Text('Show seconds'),
              subtitle: const Text('2×1 and 2×2 only · uses a little more battery'),
              value: _seconds,
              onChanged: (v) => setState(() => _seconds = v),
            ),
          ]),
        ],
      ),
      bottomNavigationBar: BottomAction(
        label: widget.adding ? 'Add to home screen' : 'Save',
        // A clock needs a city before it can be added.
        onPressed: _loaded && city?.timeZone != null ? () => WidgetBridge.save(widget.widgetId, 'clock', _settings) : null,
      ),
    );
  }
}
