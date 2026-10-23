import 'dart:async';

import 'package:fluentui_system_icons/fluentui_system_icons.dart';
import 'package:amn_ui/amn_ui.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'bridge.dart';
import 'geocoding.dart';

/// A widget size the preview can show, in dp.
class PreviewSize {
  const PreviewSize(this.label, this.width, this.height, {this.hostScale = 1});

  final String label;

  /// The size the launcher reports (and the widget lays out at).
  final double width;
  final double height;

  /// How much the launcher shrinks the result; One UI uses about 0.83.
  final double hostScale;
}

/// Sizes One UI reports on a 4-column home screen (Galaxy A55), with its 0.83 shrink.
const oneUiWeatherSizes = [
  PreviewSize('2×1', 200.5, 113, hostScale: .8333),
  PreviewSize('2×2', 200.5, 226, hostScale: .8333),
  PreviewSize('4×2', 401, 226, hostScale: .8333),
  PreviewSize('4×3', 401, 339, hostScale: .8333),
  PreviewSize('4×4', 401, 452, hostScale: .8333),
];

const oneUiClockSizes = [
  PreviewSize('1×1', 100, 113, hostScale: .8333),
  PreviewSize('2×1', 200.5, 113, hostScale: .8333),
  PreviewSize('2×2', 200.5, 226, hostScale: .8333),
  PreviewSize('3×2', 301, 226, hostScale: .8333),
  PreviewSize('3×1', 301, 113, hostScale: .8333),
];

const oneUiPrayerSizes = [PreviewSize('4×1', 401, 113, hostScale: .8333)];

const prayerSizes = [PreviewSize('4×1', 358, 103)];

const weatherSizes = [
  PreviewSize('2×1', 140, 70),
  PreviewSize('2×2', 140, 140),
  PreviewSize('4×2', 290, 140),
  PreviewSize('4×3', 290, 215),
  PreviewSize('4×4', 290, 290),
];

const clockSizes = [
  PreviewSize('1×1', 70, 70),
  PreviewSize('2×1', 140, 70),
  PreviewSize('2×2', 140, 140),
];

/// The real Glance widget, rendered natively and shown through a platform view.
/// Taps are blocked so the widget's own tap action can't fire.
class WidgetPreview extends StatefulWidget {
  const WidgetPreview({
    super.key,
    required this.kind,
    required this.widgetId,
    required this.size,
    required this.settings,
    this.revision = 0,
    this.debug,
  });

  final String kind;
  final int widgetId;
  final PreviewSize size;
  final Map<String, Object?> settings;

  /// Bump to re-render with the same settings (for example after new weather arrives).
  final int revision;

  /// Widget lab overrides: {'condition': 'rain', 'hour': 21, 'dark': true}.
  final Map<String, Object?>? debug;

  @override
  State<WidgetPreview> createState() => _WidgetPreviewState();
}

class _WidgetPreviewState extends State<WidgetPreview> {
  int? _viewId;

  Map<String, Object?> get _args => {
        'kind': widget.kind,
        'widgetId': widget.widgetId,
        'width': widget.size.width,
        'height': widget.size.height,
        'hostScale': widget.size.hostScale,
        'settings': widget.settings,
        if (widget.debug != null) 'debug': widget.debug,
      };

  @override
  void didUpdateWidget(WidgetPreview old) {
    super.didUpdateWidget(old);
    final id = _viewId;
    if (id != null && (old.size != widget.size || old.revision != widget.revision || !mapEquals(old.settings, widget.settings) || !mapEquals(old.debug, widget.debug))) {
      WidgetBridge.previewChanged(id, _args);
    }
  }

  @override
  Widget build(BuildContext context) {
    // Flutter logical pixels match Android dp, so the sizes carry over directly.
    return SizedBox(
      width: widget.size.width * widget.size.hostScale,
      height: widget.size.height * widget.size.hostScale,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(24),
        child: IgnorePointer(
          child: AndroidView(
            viewType: WidgetBridge.previewViewType,
            creationParams: _args,
            creationParamsCodec: const StandardMessageCodec(),
            onPlatformViewCreated: (id) => _viewId = id,
          ),
        ),
      ),
    );
  }
}

/// Preview on a glass band over the sky (the sky stands in for the wallpaper), with size chips underneath.
class PreviewBand extends StatelessWidget {
  const PreviewBand({super.key, required this.preview, required this.sizes, required this.selected, required this.onSelected, this.note});

  final Widget preview;
  final List<PreviewSize> sizes;
  final PreviewSize selected;
  final ValueChanged<PreviewSize> onSelected;
  final String? note;

  @override
  Widget build(BuildContext context) {
    final onBand = AmnGlass.of(context).ink;
    return Glass(
      radius: 28,
      padding: const EdgeInsets.fromLTRB(12, 22, 12, 14),
      child: Column(
        children: [
          SizedBox(height: 290, child: Center(child: preview)),
          if (note != null) ...[
            const SizedBox(height: 8),
            Text(note!, textAlign: TextAlign.center, style: TextStyle(color: onBand, fontSize: 12.5)),
          ],
          const SizedBox(height: 14),
          Wrap(
            alignment: WrapAlignment.center,
            spacing: 6,
            runSpacing: 6,
            children: [
              for (final s in sizes)
                ChoiceChip(
                  label: Text(s.label),
                  selected: s == selected,
                  showCheckmark: false,
                  onSelected: (_) => onSelected(s),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class SectionLabel extends StatelessWidget {
  const SectionLabel(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(6, 24, 6, 8),
      child: Text(
        text.toUpperCase(),
        style: theme.textTheme.labelMedium?.copyWith(letterSpacing: .8, fontWeight: FontWeight.w700, color: theme.colorScheme.onSurfaceVariant),
      ),
    );
  }
}

/// A rounded glass group of rows.
class GroupCard extends StatelessWidget {
  const GroupCard({super.key, required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Glass(radius: 22, child: Column(children: children));
  }
}

/// Search field plus up to five matching places from Open-Meteo Geocoding.
/// With [showZones], each result also shows that city's current time and offset.
class CityPicker extends StatefulWidget {
  const CityPicker({
    super.key,
    required this.selected,
    required this.onSelected,
    this.hint = 'Search for a city',
    this.showZones = false,
    this.h24 = true,
    this.geocoding,
  });

  final City? selected;
  final ValueChanged<City> onSelected;
  final String hint;
  final bool showZones;
  final bool h24;
  final Geocoding? geocoding;

  @override
  State<CityPicker> createState() => _CityPickerState();
}

class _CityPickerState extends State<CityPicker> {
  late final Geocoding _geo = widget.geocoding ?? Geocoding();
  Timer? _debounce;
  String _query = '';
  bool _loading = false;
  SearchResult? _result;
  Map<String, ZoneInfo> _zones = const {};
  int _seq = 0;

  @override
  void dispose() {
    _debounce?.cancel();
    super.dispose();
  }

  void _onChanged(String value) {
    _query = value.trim();
    _debounce?.cancel();
    if (_query.length < 2) {
      setState(() {
        _result = null;
        _loading = false;
      });
      return;
    }
    setState(() => _loading = true);
    _debounce = Timer(const Duration(milliseconds: 300), _search);
  }

  Future<void> _search() async {
    final seq = ++_seq;
    final result = await _geo.search(_query);
    var zones = const <String, ZoneInfo>{};
    if (widget.showZones) {
      final tzs = {for (final c in result.cities) ?c.timeZone}.toList();
      if (tzs.isNotEmpty) zones = await WidgetBridge.describeZones(tzs, h24: widget.h24);
    }
    if (!mounted || seq != _seq) return; // a newer search started
    setState(() {
      _result = result;
      _zones = zones;
      _loading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final result = _result;
    final Widget? body = switch (result) {
      _ when _loading => const Padding(padding: EdgeInsets.all(16), child: Center(child: CircularProgressIndicator())),
      null => null,
      SearchResult(problem: SearchProblem.offline) =>
        const _Message("You're offline. Search needs a connection. Your current city stays."),
      SearchResult(problem: SearchProblem.failed) => const _Message("Search isn't working right now. Try again in a minute."),
      SearchResult(cities: []) => _Message('No city matches “$_query”. Check the spelling, or try a larger city nearby.'),
      _ => null,
    };
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SearchBar(
          hintText: widget.hint,
          leading: const Icon(FluentIcons.search_24_regular),
          onChanged: _onChanged,
        ),
        if (body != null || (result?.cities.isNotEmpty ?? false)) ...[
          const SizedBox(height: 8),
          GroupCard(
            children: body != null
                ? [body]
                : [
                    for (final c in result!.cities)
                      ListTile(
                        title: Text(c.name, style: const TextStyle(fontWeight: FontWeight.w600)),
                        subtitle: Text(c.subtitle),
                        selected: _same(c, widget.selected),
                        selectedTileColor: scheme.primary.withValues(alpha: .16),
                        trailing: _trailing(c, scheme),
                        onTap: () => widget.onSelected(c),
                      ),
                  ],
          ),
        ],
      ],
    );
  }

  Widget? _trailing(City c, ColorScheme scheme) {
    final check = _same(c, widget.selected) ? Icon(FluentIcons.checkmark_24_regular, color: scheme.primary) : null;
    final z = c.timeZone == null ? null : _zones[c.timeZone];
    if (!widget.showZones || z == null) return check;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Text(z.time, style: const TextStyle(fontWeight: FontWeight.w700, fontFeatures: [FontFeature.tabularFigures()])),
            Text(z.offset, style: Theme.of(context).textTheme.bodySmall),
          ],
        ),
        if (check != null) ...[const SizedBox(width: 8), check],
      ],
    );
  }

  static bool _same(City a, City? b) => b != null && a.name == b.name && (a.lat - b.lat).abs() < .01 && (a.lon - b.lon).abs() < .01;
}

class _Message extends StatelessWidget {
  const _Message(this.text);

  final String text;

  @override
  Widget build(BuildContext context) => Padding(padding: const EdgeInsets.all(16), child: Text(text));
}

/// Close (adding) or back (editing). Either way nothing is saved.
class CloseButtonFor extends StatelessWidget {
  const CloseButtonFor({super.key, required this.adding});

  final bool adding;

  @override
  Widget build(BuildContext context) => IconButton(
        icon: Icon(adding ? FluentIcons.dismiss_24_regular : FluentIcons.arrow_left_24_regular),
        tooltip: adding ? 'Close without adding' : 'Back',
        onPressed: WidgetBridge.cancel,
      );
}

/// Full-width main button pinned to the bottom.
class BottomAction extends StatelessWidget {
  const BottomAction({super.key, required this.label, required this.onPressed});

  final String label;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) => SafeArea(
        minimum: const EdgeInsets.fromLTRB(16, 8, 16, 16),
        child: FilledButton(
          style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(56), textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
          onPressed: onPressed,
          child: Text(label),
        ),
      );
}

/// Page titles in Barlow Condensed, uppercase, to match the widgets.
class PageTitle extends StatelessWidget {
  const PageTitle(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) => Text(
        text.toUpperCase(),
        style: AmnType.display(28, letterSpacing: .2),
      );
}

/// "Updated 12 min ago" from an epoch-millisecond time.
String updatedLabel(int? fetchedAt, {DateTime? now}) {
  if (fetchedAt == null) return 'Not updated yet';
  final min = (now ?? DateTime.now()).difference(DateTime.fromMillisecondsSinceEpoch(fetchedAt)).inMinutes;
  if (min < 1) return 'Updated just now';
  if (min < 60) return 'Updated $min min ago';
  return 'Updated ${min ~/ 60} h ago';
}
