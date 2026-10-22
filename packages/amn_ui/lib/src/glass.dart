import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import 'theme.dart';

/// Frosted glass over the sky: a light or dark tint, a 1 px edge and a soft
/// highlight along the top. Static: it never repaints while the sky moves.
class Glass extends StatelessWidget {
  const Glass({super.key, required this.child, this.radius = 26, this.padding = EdgeInsets.zero, this.blur = false});

  final Widget child;
  final double radius;
  final EdgeInsetsGeometry padding;

  /// Blur what's behind. Only worth it where content scrolls under the
  /// glass: over the sky alone a blur looks the same as none, and it has to
  /// be redone every time the sky moves.
  final bool blur;

  @override
  Widget build(BuildContext context) {
    final g = AmnGlass.of(context);
    Widget body = CustomPaint(
      painter: _GlassPainter(g, radius),
      child: Padding(padding: padding, child: child),
    );
    if (blur) body = BackdropFilter.grouped(filter: AmnGlass.filter, child: body);
    return ClipRRect(borderRadius: BorderRadius.circular(radius), child: Material(type: MaterialType.transparency, child: body));
  }
}

/// The background for app bars: clear over the sky, and a near-solid frosted
/// tint once content scrolls under it (no live blur: that would be redone
/// every time the sky moves).
class GlassBar extends StatelessWidget {
  const GlassBar({super.key});

  @override
  Widget build(BuildContext context) => const SizedBox.expand();
}

class _GlassPainter extends CustomPainter {
  const _GlassPainter(this.g, this.radius);

  final AmnGlass g;
  final double radius;

  @override
  void paint(Canvas canvas, Size size) {
    final rrect = RRect.fromRectAndRadius(Offset.zero & size, Radius.circular(radius));
    canvas.drawRRect(rrect, Paint()..color = g.tint);
    final edge = rrect.deflate(.5);
    canvas.drawRRect(edge, Paint()
      ..style = PaintingStyle.stroke
      ..color = g.edge);
    // Light catching the top edge, gone a third of the way down.
    canvas.drawRRect(
      edge,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.2
        ..shader = ui.Gradient.linear(Offset.zero, Offset(0, math.min(size.height * .33, 40)), [g.highlight, g.highlight.withValues(alpha: 0)]),
    );
  }

  @override
  bool shouldRepaint(_GlassPainter old) => old.g != g || old.radius != radius;
}
