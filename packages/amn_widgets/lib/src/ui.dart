import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'bridge.dart';
import 'cities.dart';

/// A widget size the preview can show, in dp.
class PreviewSize {
  const PreviewSize(this.label, this.width, this.height);

  final String label;
  final double width;
  final double height;
}

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
  const WidgetPreview({super.key, required this.kind, required this.size, required this.settings});

  final String kind;
  final PreviewSize size;
  final Map<String, Object?> settings;

  @override
  State<WidgetPreview> createState() => _WidgetPreviewState();
}

class _WidgetPreviewState extends State<WidgetPreview> {
  int? _viewId;

  Map<String, Object?> get _args => {
        'kind': widget.kind,
        'width': widget.size.width,
        'height': widget.size.height,
        'settings': widget.settings,
      };

  @override
  void didUpdateWidget(WidgetPreview old) {
    super.didUpdateWidget(old);
    final id = _viewId;
    if (id != null && (old.size != widget.size || !mapEquals(old.settings, widget.settings))) {
      WidgetBridge.previewChanged(id, _args);
    }
  }

  @override
  Widget build(BuildContext context) {
    // Flutter logical pixels match Android dp, so the sizes carry over directly.
    return SizedBox(
      width: widget.size.width,
      height: widget.size.height,
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

/// Preview on a wallpaper-like band, with size chips underneath.
class PreviewBand extends StatelessWidget {
  const PreviewBand({super.key, required this.preview, required this.sizes, required this.selected, required this.onSelected});

  final Widget preview;
  final List<PreviewSize> sizes;
  final PreviewSize selected;
  final ValueChanged<PreviewSize> onSelected;

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      padding: const EdgeInsets.fromLTRB(12, 22, 12, 14),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(28),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: dark
              ? const [Color(0xFF1D2742), Color(0xFF1A1722), Color(0xFF3A2618)]
              : const [Color(0xFFC3D3F2), Color(0xFFE2D6E4), Color(0xFFF1CFA9)],
        ),
      ),
      child: Column(
        children: [
          SizedBox(height: 290, child: Center(child: preview)),
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

/// A rounded group of rows.
class GroupCard extends StatelessWidget {
  const GroupCard({super.key, required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Card.filled(
      margin: EdgeInsets.zero,
      color: Theme.of(context).colorScheme.surfaceContainerLowest,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      clipBehavior: Clip.antiAlias,
      child: Column(children: children),
    );
  }
}

/// Search field plus up to five matching cities.
class CityPicker extends StatefulWidget {
  const CityPicker({super.key, required this.selected, required this.onSelected, this.hint = 'Search for a city'});

  final City? selected;
  final ValueChanged<City> onSelected;
  final String hint;

  @override
  State<CityPicker> createState() => _CityPickerState();
}

class _CityPickerState extends State<CityPicker> {
  String _query = '';

  @override
  Widget build(BuildContext context) {
    final results = searchCities(_query);
    final scheme = Theme.of(context).colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SearchBar(
          hintText: widget.hint,
          leading: const Icon(Icons.search),
          elevation: const WidgetStatePropertyAll(0),
          backgroundColor: WidgetStatePropertyAll(scheme.surfaceContainerLowest),
          onChanged: (v) => setState(() => _query = v),
        ),
        const SizedBox(height: 8),
        GroupCard(
          children: results.isEmpty
              ? [
                  Padding(
                    padding: const EdgeInsets.all(16),
                    child: Text('No city matches “${_query.trim()}”. Check the spelling, or try a larger city nearby.'),
                  ),
                ]
              : [
                  for (final c in results)
                    ListTile(
                      title: Text(c.name, style: const TextStyle(fontWeight: FontWeight.w600)),
                      subtitle: Text(c.country),
                      selected: c.name == widget.selected?.name,
                      selectedTileColor: scheme.primaryContainer.withValues(alpha: .5),
                      trailing: c.name == widget.selected?.name ? Icon(Icons.check, color: scheme.primary) : null,
                      onTap: () => widget.onSelected(c),
                    ),
                ],
        ),
      ],
    );
  }
}

/// Close (adding) or back (editing). Either way nothing is saved.
class CloseButtonFor extends StatelessWidget {
  const CloseButtonFor({super.key, required this.adding});

  final bool adding;

  @override
  Widget build(BuildContext context) => IconButton(
        icon: Icon(adding ? Icons.close : Icons.arrow_back),
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

TextStyle? pageTitleStyle(BuildContext context) =>
    Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800, letterSpacing: -.2);
