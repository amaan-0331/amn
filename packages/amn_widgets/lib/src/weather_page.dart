import 'package:fluentui_system_icons/fluentui_system_icons.dart';
import 'package:amn_ui/amn_ui.dart';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

import 'bridge.dart';
import 'geocoding.dart';
import 'ui.dart';

enum _Access { always, whileInUse, denied, blocked }

/// Edit page for a weather widget. See the spec's "Edit pages" section.
class WeatherConfigPage extends StatefulWidget {
  const WeatherConfigPage({super.key, required this.widgetId, required this.adding});

  final int widgetId;
  final bool adding;

  @override
  State<WeatherConfigPage> createState() => _WeatherConfigPageState();
}

class _WeatherConfigPageState extends State<WeatherConfigPage> with WidgetsBindingObserver {
  bool _loaded = false;
  bool _useGps = true;
  City _city = const City(name: 'Berlin', country: 'Germany', lat: 52.52, lon: 13.41);
  bool _fahrenheit = false;
  bool _inches = false;
  PreviewSize _size = weatherSizes[1];
  _Access? _access;
  String? _placeName;
  int? _fetchedAt;
  bool _refreshing = false;
  int _revision = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  // Back from the system's location settings: check again.
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _checkAccess();
  }

  Future<void> _load() async {
    final m = await WidgetBridge.getSettings(widget.widgetId, 'weather');
    final access = await _readAccess();
    if (!mounted) return;
    setState(() {
      _city = City(
        name: m['cityName'] as String? ?? 'Berlin',
        country: '',
        lat: (m['lat'] as num?)?.toDouble() ?? 52.52,
        lon: (m['lon'] as num?)?.toDouble() ?? 13.41,
      );
      _fahrenheit = m['fahrenheit'] as bool? ?? false;
      _inches = m['inches'] as bool? ?? false;
      _placeName = m['placeName'] as String?;
      _fetchedAt = m['fetchedAt'] as int?;
      _access = access;
      // New widgets start on Current location only when permission is already there.
      _useGps = widget.adding ? access != _Access.denied && access != _Access.blocked : m['useGps'] as bool? ?? true;
      _loaded = true;
    });
  }

  static Future<_Access> _readAccess() async {
    if (await Permission.locationAlways.isGranted) return _Access.always;
    final s = await Permission.location.status;
    if (s.isGranted || s.isLimited) return _Access.whileInUse;
    if (s.isPermanentlyDenied) return _Access.blocked;
    return _Access.denied;
  }

  Future<void> _checkAccess() async {
    final a = await _readAccess();
    if (mounted) setState(() => _access = a);
  }

  /// Approximate location first, then "Allow all the time" (Android opens its settings page for that).
  Future<void> _requestAccess() async {
    if (_access == _Access.blocked) {
      await openAppSettings();
      return;
    }
    if (_access != _Access.whileInUse) {
      final s = await Permission.location.request();
      if (!(s.isGranted || s.isLimited)) return _checkAccess();
    }
    await Permission.locationAlways.request();
    await _checkAccess();
  }

  Future<void> _refresh() async {
    setState(() => _refreshing = true);
    final m = await WidgetBridge.refreshNow(widget.widgetId);
    if (!mounted) return;
    setState(() {
      _fetchedAt = m['fetchedAt'] as int? ?? _fetchedAt;
      _placeName = m['placeName'] as String? ?? _placeName;
      _refreshing = false;
      _revision++;
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

  Widget _locationCard() {
    final allowButton = TextButton(onPressed: _requestAccess, child: const Text('Allow'));
    final tile = switch (_access) {
      _Access.always => ListTile(
          leading: const Icon(FluentIcons.location_24_filled),
          title: Text(_placeName ?? 'Current location'),
          subtitle: const Text('Found by GPS · approximate location, allowed all the time'),
        ),
      _Access.whileInUse => ListTile(
          leading: const Icon(FluentIcons.location_24_regular),
          title: const Text('Allow location all the time'),
          subtitle: const Text('Refreshes run in the background, so AMN needs “Allow all the time”. Until then the widget uses your pinned city.'),
          trailing: allowButton,
        ),
      _ => ListTile(
          leading: const Icon(FluentIcons.location_off_24_regular),
          title: const Text('Location is off for AMN'),
          subtitle: const Text('Allow approximate location, or choose a city instead.'),
          trailing: allowButton,
        ),
    };
    return GroupCard(children: [tile]);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        flexibleSpace: const GlassBar(),
        leading: CloseButtonFor(adding: widget.adding),
        title: PageTitle(widget.adding ? 'Add weather widget' : 'Weather widget'),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(12, 0, 12, 24),
        children: [
          PreviewBand(
            preview: WidgetPreview(kind: 'weather', widgetId: widget.widgetId, size: _size, settings: _settings, revision: _revision),
            sizes: weatherSizes,
            selected: _size,
            onSelected: (s) => setState(() => _size = s),
            note: _loaded && _fetchedAt == null ? 'Preview shows sample weather until the first update.' : null,
          ),
          const SectionLabel('Location'),
          SegmentedButton<bool>(
            showSelectedIcon: false,
            segments: const [
              ButtonSegment(value: true, label: Text('Current location')),
              ButtonSegment(value: false, label: Text('Choose a city')),
            ],
            selected: {_useGps},
            onSelectionChanged: (v) {
              setState(() => _useGps = v.first);
              if (v.first && (_access == _Access.denied)) _requestAccess();
            },
          ),
          const SizedBox(height: 8),
          if (_useGps) ...[
            _locationCard(),
            const SizedBox(height: 8),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 6),
              child: Text('Pinned city when location is unavailable: ${_city.name}', style: Theme.of(context).textTheme.bodySmall),
            ),
          ] else
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
            GroupCard(children: [
              ListTile(
                title: Text(updatedLabel(_fetchedAt)),
                subtitle: const Text('Refreshes about every 30 minutes · weather by Open-Meteo'),
                trailing: _refreshing
                    ? const SizedBox.square(dimension: 24, child: CircularProgressIndicator(strokeWidth: 2.5))
                    : TextButton(onPressed: _refresh, child: const Text('Refresh now')),
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
