import 'package:flutter/material.dart';

import 'bridge.dart';

/// Widget lab panel: shows how the weather widget's summary line is picked.
///
/// 1. Urgent rules are checked in order; the first that applies wins.
/// 2. Otherwise every flavor rule that applies is a candidate: seed mod candidates picks one.
/// 3. Then a phrasing: (seed ÷ candidates, rounded down) mod phrasings, from those short enough for the size.
class SummaryEngine extends StatefulWidget {
  const SummaryEngine({
    super.key,
    required this.condition,
    required this.hour,
    required this.h24,
    required this.fahrenheit,
    required this.seed,
    required this.onSeed,
  });

  final String condition;
  final int hour;
  final bool h24;
  final bool fahrenheit;
  final int seed;
  final ValueChanged<int> onSeed;

  @override
  State<SummaryEngine> createState() => _SummaryEngineState();
}

class _SummaryEngineState extends State<SummaryEngine> {
  Map<String, Object?>? _data;
  String _key = '';

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _fetch();
  }

  @override
  void didUpdateWidget(SummaryEngine old) {
    super.didUpdateWidget(old);
    _fetch();
  }

  void _fetch() {
    final key = '${widget.condition}|${widget.hour}|${widget.h24}|${widget.fahrenheit}|${widget.seed}';
    if (key == _key) return;
    _key = key;
    WidgetBridge.explainSummary({
      'condition': widget.condition,
      'hour': widget.hour,
      'h24': widget.h24,
      'fahrenheit': widget.fahrenheit,
      'seed': widget.seed,
    }).then((d) {
      if (mounted && key == _key) setState(() => _data = d);
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final d = _data;
    final rules = (d?['rules'] as List?)?.cast<Map>() ?? const [];
    final winner = d?['winner'] as String?;
    final urgent = rules.where((r) => r['urgent'] == true).toList();
    final flavors = rules.where((r) => r['urgent'] != true && r['id'] != 'steady').toList();
    final steady = rules.where((r) => r['id'] == 'steady').firstOrNull;
    final urgentWon = urgent.any((r) => r['id'] == winner);
    final candidates = flavors.where((r) => ((r['fitting'] as List?) ?? const []).isNotEmpty).toList();
    final winnerRule = rules.where((r) => r['id'] == winner).firstOrNull;
    final seed = widget.seed;
    final cc = d?['candidateCount'] as int? ?? 0;
    final ci = d?['candidateIndex'] as int? ?? -1;
    final vc = d?['variantCount'] as int? ?? 0;
    final vi = d?['variantIndex'] as int? ?? 0;

    return Container(
      margin: const EdgeInsets.only(top: 20),
      padding: const EdgeInsets.fromLTRB(16, 14, 16, 16),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(24),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [scheme.primaryContainer.withValues(alpha: .55), scheme.surfaceContainerHigh],
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            const Text('SUMMARY ENGINE',
                style: TextStyle(fontFamily: 'BarlowCondensed', package: 'amn_ui', fontWeight: FontWeight.w700, fontSize: 22, letterSpacing: .5)),
            const Spacer(),
            IconButton.filledTonal(icon: const Icon(Icons.chevron_left), tooltip: 'Previous line', onPressed: () => widget.onSeed(seed - 1)),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 6),
              child: Text('seed $seed', style: const TextStyle(fontFeatures: [FontFeature.tabularFigures()])),
            ),
            IconButton.filledTonal(icon: const Icon(Icons.chevron_right), tooltip: 'Next line', onPressed: () => widget.onSeed(seed + 1)),
          ]),
          const SizedBox(height: 6),
          // The result, animated as the seed changes.
          AnimatedSwitcher(
            duration: const Duration(milliseconds: 320),
            transitionBuilder: (child, anim) => FadeTransition(
              opacity: anim,
              child: SlideTransition(position: Tween(begin: const Offset(0, .25), end: Offset.zero).animate(anim), child: child),
            ),
            child: Text(
              d?['text'] as String? ?? '…',
              key: ValueKey(d?['text']),
              style: const TextStyle(fontFamily: 'BarlowCondensed', package: 'amn_ui', fontWeight: FontWeight.w700, fontSize: 34, height: 1.1),
            ),
          ),
          if (winner != null)
            Padding(
              padding: const EdgeInsets.only(top: 2, bottom: 6),
              child: Text(
                urgentWon ? 'Rule “$winner”: urgent, so it goes first' : winner == 'steady' ? 'Nothing notable, so a calm line' : 'Rule “$winner”: picked by rotation',
                style: theme.textTheme.bodySmall,
              ),
            ),
          _Step(
            n: 1,
            title: 'Urgent rules, in order',
            note: urgentWon ? 'First match wins' : 'None apply, so the rotation decides',
            child: Column(children: [
              for (final r in urgent) _RuleRow(rule: r, winner: winner == r['id']),
            ]),
          ),
          _Step(
            n: 2,
            title: 'Rotation',
            note: urgentWon
                ? 'Skipped: an urgent rule won'
                : cc == 0
                    ? 'No flavor rule applies'
                    : 'seed $seed mod $cc candidates = #${ci + 1}',
            child: Wrap(spacing: 6, runSpacing: 6, children: [
              for (final r in flavors)
                _RuleChip(
                  label: r['id'] as String,
                  tooltip: r['description'] as String,
                  applies: candidates.contains(r),
                  picked: !urgentWon && r['id'] == winner,
                ),
            ]),
          ),
          _Step(
            n: 3,
            title: 'Phrasing',
            note: vc == 0
                ? ''
                : urgentWon || winner == 'steady'
                    ? 'seed $seed mod $vc phrasings = #${vi + 1}'
                    : '$seed ÷ $cc = ${seed ~/ cc}, then ${seed ~/ cc} mod $vc phrasings = #${vi + 1}',
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              for (final v in ((winnerRule ?? steady)?['variants'] as List?)?.cast<String>() ?? const <String>[])
                _PhraseRow(text: v, chosen: v == d?['text'], fits: (((winnerRule ?? steady)?['fitting'] as List?) ?? const []).contains(v)),
            ]),
          ),
          _Step(
            n: 4,
            title: 'Each size',
            note: 'Same seed, only phrasings that fit',
            last: true,
            child: Column(children: [
              for (final s in ((d?['sizes'] as List?) ?? const []).cast<Map>())
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 3),
                  child: Row(children: [
                    SizedBox(width: 88, child: Text('${s['label']}', style: theme.textTheme.labelMedium)),
                    SizedBox(width: 44, child: Text('≤${s['maxLen']}', style: theme.textTheme.labelSmall?.copyWith(color: scheme.onSurfaceVariant))),
                    Expanded(child: Text('${s['text']}', style: const TextStyle(fontWeight: FontWeight.w600))),
                  ]),
                ),
            ]),
          ),
        ],
      ),
    );
  }
}

/// A numbered step with a line down to the next one.
class _Step extends StatelessWidget {
  const _Step({required this.n, required this.title, required this.note, required this.child, this.last = false});

  final int n;
  final String title;
  final String note;
  final Widget child;
  final bool last;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return IntrinsicHeight(
      child: Row(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Column(children: [
          const SizedBox(height: 10),
          CircleAvatar(radius: 12, backgroundColor: scheme.primary, child: Text('$n', style: TextStyle(color: scheme.onPrimary, fontSize: 12, fontWeight: FontWeight.w700))),
          if (!last) Expanded(child: Container(width: 2, margin: const EdgeInsets.symmetric(vertical: 4), color: scheme.primary.withValues(alpha: .35))),
        ]),
        const SizedBox(width: 12),
        Expanded(
          child: Padding(
            padding: const EdgeInsets.only(top: 10, bottom: 6),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
              if (note.isNotEmpty) Text(note, style: TextStyle(fontSize: 12, color: scheme.primary, fontFeatures: const [FontFeature.tabularFigures()])),
              const SizedBox(height: 6),
              child,
            ]),
          ),
        ),
      ]),
    );
  }
}

class _RuleRow extends StatelessWidget {
  const _RuleRow({required this.rule, required this.winner});

  final Map rule;
  final bool winner;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final applies = rule['variants'] != null;
    return AnimatedContainer(
      duration: const Duration(milliseconds: 250),
      margin: const EdgeInsets.only(bottom: 3),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
      decoration: BoxDecoration(
        color: winner ? scheme.primary.withValues(alpha: .18) : Colors.transparent,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(children: [
        Icon(
          winner ? Icons.bolt : applies ? Icons.check_circle_outline : Icons.remove_circle_outline,
          size: 18,
          color: winner ? scheme.primary : applies ? scheme.onSurface : scheme.outline,
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text('${rule['description']}',
              style: TextStyle(fontSize: 13, color: applies ? scheme.onSurface : scheme.outline, fontWeight: winner ? FontWeight.w700 : FontWeight.w400)),
        ),
        if (winner) Text('WINS', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w800, color: scheme.primary, letterSpacing: .8)),
      ]),
    );
  }
}

class _RuleChip extends StatelessWidget {
  const _RuleChip({required this.label, required this.tooltip, required this.applies, required this.picked});

  final String label;
  final String tooltip;
  final bool applies;
  final bool picked;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Tooltip(
      message: tooltip,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 250),
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          color: picked ? scheme.primary : applies ? scheme.secondaryContainer : Colors.transparent,
          borderRadius: BorderRadius.circular(99),
          border: Border.all(color: applies || picked ? Colors.transparent : scheme.outlineVariant),
        ),
        child: Text(label,
            style: TextStyle(
              fontSize: 12.5,
              fontWeight: picked ? FontWeight.w700 : FontWeight.w500,
              color: picked ? scheme.onPrimary : applies ? scheme.onSecondaryContainer : scheme.outline,
              decoration: applies || picked ? null : TextDecoration.lineThrough,
            )),
      ),
    );
  }
}

class _PhraseRow extends StatelessWidget {
  const _PhraseRow({required this.text, required this.chosen, required this.fits});

  final String text;
  final bool chosen;
  final bool fits;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(children: [
        SizedBox(width: 22, child: chosen ? Icon(Icons.arrow_right_alt, size: 20, color: scheme.primary) : null),
        Expanded(
          child: Text(text,
              style: TextStyle(
                fontWeight: chosen ? FontWeight.w700 : FontWeight.w400,
                color: chosen ? scheme.primary : fits ? scheme.onSurface : scheme.outline,
                decoration: fits ? null : TextDecoration.lineThrough,
              )),
        ),
        Text('${text.length}', style: TextStyle(fontSize: 11, color: scheme.outline, fontFeatures: const [FontFeature.tabularFigures()])),
      ]),
    );
  }
}
