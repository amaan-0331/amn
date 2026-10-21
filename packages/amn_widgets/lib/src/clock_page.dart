import 'package:flutter/material.dart';

import 'bridge.dart';
import 'cities.dart';
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
  PreviewSize _size = clockSizes[2];

  @override
  void initState() {
    super.initState();
    WidgetBridge.getSettings(widget.widgetId, 'clock').then((m) {
      if (!mounted) return;
      setState(() {
        final tz = m['timeZone'] as String?;
        final name = m['cityName'] as String?;
        if (tz != null && name != null) {
          _city = kCities.firstWhere((c) => c.name == name,
              orElse: () => City(name, m['countryName'] as String? ?? '', (m['lat'] as num?)?.toDouble() ?? 0, (m['lon'] as num?)?.toDouble() ?? 0, tz));
        }
        _h24 = m['h24'] as bool? ?? true;
        _seconds = m['seconds'] as bool? ?? false;
        _loaded = true;
      });
    });
  }

  Map<String, Object?> get _settings => {
        'cityName': _city?.name,
        'countryName': _city?.country,
        'timeZone': _city?.timeZone,
        'lat': _city?.lat,
        'lon': _city?.lon,
        'h24': _h24,
        'seconds': _seconds,
      };

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final city = _city;
    return Scaffold(
      appBar: AppBar(
        leading: CloseButtonFor(adding: widget.adding),
        title: Text(widget.adding ? 'Add clock widget' : 'Clock widget', style: pageTitleStyle(context)),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(12, 0, 12, 24),
        children: [
          PreviewBand(
            preview: WidgetPreview(kind: 'clock', size: _size, settings: _settings),
            sizes: clockSizes,
            selected: _size,
            onSelected: (s) => setState(() => _size = s),
          ),
          const SectionLabel('City'),
          if (city != null) ...[
            GroupCard(children: [
              ListTile(
                title: Text(city.name.toUpperCase(), style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)),
                subtitle: Text('${city.country} · ${city.timeZone}'),
              ),
            ]),
            const SizedBox(height: 8),
          ],
          CityPicker(selected: city, hint: 'Search for a city or country', onSelected: (c) => setState(() => _city = c)),
          const SectionLabel('Format'),
          GroupCard(children: [
            ListTile(
              title: const Text('Time format'),
              subtitle: const Text("Starts from your phone's setting"),
              trailing: SegmentedButton<bool>(
                showSelectedIcon: false,
                segments: const [ButtonSegment(value: true, label: Text('24h')), ButtonSegment(value: false, label: Text('12h'))],
                selected: {_h24},
                onSelectionChanged: (v) => setState(() => _h24 = v.first),
              ),
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
        onPressed: _loaded && city != null ? () => WidgetBridge.save(widget.widgetId, 'clock', _settings) : null,
      ),
    );
  }
}
