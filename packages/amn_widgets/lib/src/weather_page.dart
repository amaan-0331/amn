import 'package:flutter/material.dart';

import 'bridge.dart';
import 'cities.dart';
import 'ui.dart';

/// Edit page for a weather widget. See the spec's "Edit pages" section.
class WeatherConfigPage extends StatefulWidget {
  const WeatherConfigPage({super.key, required this.widgetId, required this.adding});

  final int widgetId;
  final bool adding;

  @override
  State<WeatherConfigPage> createState() => _WeatherConfigPageState();
}

class _WeatherConfigPageState extends State<WeatherConfigPage> {
  bool _loaded = false;
  bool _useGps = true;
  City _city = kCities.first;
  bool _fahrenheit = false;
  bool _inches = false;
  PreviewSize _size = weatherSizes[1];

  @override
  void initState() {
    super.initState();
    WidgetBridge.getSettings(widget.widgetId, 'weather').then((m) {
      if (!mounted) return;
      setState(() {
        _useGps = m['useGps'] as bool? ?? true;
        final name = m['cityName'] as String?;
        _city = kCities.firstWhere((c) => c.name == name,
            orElse: () => City(name ?? 'Berlin', '', (m['lat'] as num?)?.toDouble() ?? 52.52, (m['lon'] as num?)?.toDouble() ?? 13.41));
        _fahrenheit = m['fahrenheit'] as bool? ?? false;
        _inches = m['inches'] as bool? ?? false;
        _loaded = true;
      });
    });
  }

  Map<String, Object?> get _settings => {
        'useGps': _useGps,
        'cityName': _city.name,
        'lat': _city.lat,
        'lon': _city.lon,
        'fahrenheit': _fahrenheit,
        'inches': _inches,
      };

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        leading: CloseButtonFor(adding: widget.adding),
        title: Text(widget.adding ? 'Add weather widget' : 'Weather widget', style: pageTitleStyle(context)),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(12, 0, 12, 24),
        children: [
          PreviewBand(
            preview: WidgetPreview(kind: 'weather', size: _size, settings: _settings),
            sizes: weatherSizes,
            selected: _size,
            onSelected: (s) => setState(() => _size = s),
          ),
          const SectionLabel('Location'),
          SegmentedButton<bool>(
            showSelectedIcon: false,
            segments: const [
              ButtonSegment(value: true, label: Text('Current location')),
              ButtonSegment(value: false, label: Text('Choose a city')),
            ],
            selected: {_useGps},
            onSelectionChanged: (v) => setState(() => _useGps = v.first),
          ),
          const SizedBox(height: 8),
          if (_useGps)
            const GroupCard(children: [
              // TODO: permission flow (approximate, then "Allow all the time") and the found city name.
              ListTile(
                leading: Icon(Icons.near_me),
                title: Text('Uses where you are'),
                subtitle: Text('Needs approximate location, allowed all the time, because refreshes run in the background.'),
              ),
            ])
          else
            CityPicker(selected: _city, onSelected: (c) => setState(() => _city = c)),
          const SectionLabel('Units · all weather widgets'),
          GroupCard(children: [
            ListTile(
              title: const Text('Temperature'),
              trailing: SegmentedButton<bool>(
                showSelectedIcon: false,
                segments: const [ButtonSegment(value: false, label: Text('°C')), ButtonSegment(value: true, label: Text('°F'))],
                selected: {_fahrenheit},
                onSelectionChanged: (v) => setState(() => _fahrenheit = v.first),
              ),
            ),
            ListTile(
              title: const Text('Rain and snow'),
              subtitle: const Text('Snow shows in cm or in'),
              trailing: SegmentedButton<bool>(
                showSelectedIcon: false,
                segments: const [ButtonSegment(value: false, label: Text('mm')), ButtonSegment(value: true, label: Text('in'))],
                selected: {_inches},
                onSelectionChanged: (v) => setState(() => _inches = v.first),
              ),
            ),
          ]),
          if (!widget.adding) ...[
            const SectionLabel('Data'),
            const GroupCard(children: [
              // TODO: real update time and refreshNow once the Open-Meteo job exists.
              ListTile(
                title: Text('Sample data'),
                subtitle: Text('Live weather from Open-Meteo is the next step. Then it refreshes about every 30 minutes.'),
              ),
            ]),
          ],
        ],
      ),
      bottomNavigationBar: BottomAction(
        label: widget.adding ? 'Add to home screen' : 'Save',
        onPressed: _loaded ? () => WidgetBridge.save(widget.widgetId, 'weather', _settings) : null,
      ),
    );
  }
}
