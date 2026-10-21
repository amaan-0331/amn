import 'dart:async';
import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import 'drift.dart';
import 'theme.dart';

/// Frosted glass over the sky: blur, a light or dark tint, a 1 px edge, a soft
/// highlight along the top and grain that fades in and out (it never moves).
class Glass extends StatelessWidget {
  const Glass({super.key, required this.child, this.radius = 26, this.padding = EdgeInsets.zero, this.blur = true});

  final Widget child;
  final double radius;
  final EdgeInsetsGeometry padding;

  /// Off for glass that only ever sits on the sky itself (nothing scrolls
  /// under it), where a blur of a soft gradient looks the same as none.
  final bool blur;

  @override
  Widget build(BuildContext context) {
    final g = AmnGlass.of(context);
    final r = BorderRadius.circular(radius);
    Widget body = CustomPaint(
      painter: _GlassPainter(Drift.read(context), g, radius),
      child: Padding(padding: padding, child: child),
    );
    if (blur) body = BackdropFilter.grouped(filter: AmnGlass.filter, child: body);
    return ClipRRect(borderRadius: r, child: Material(type: MaterialType.transparency, child: body));
  }
}

/// The background for app bars: blurred always (invisible over the sky), and
/// tinted once something has scrolled under it.
class GlassBar extends StatelessWidget {
  const GlassBar({super.key});

  @override
  Widget build(BuildContext context) =>
      ClipRect(child: BackdropFilter.grouped(filter: AmnGlass.filter, child: const SizedBox.expand()));
}

class _GlassPainter extends CustomPainter {
  _GlassPainter(this.drift, this.g, this.radius) : super(repaint: drift.frame);

  final DriftController drift;
  final AmnGlass g;
  final double radius;

  @override
  void paint(Canvas canvas, Size size) {
    final rrect = RRect.fromRectAndRadius(Offset.zero & size, Radius.circular(radius));
    canvas.drawRRect(rrect, Paint()..color = g.tint);

    final noise = Grain.image;
    if (noise != null) {
      final k = Grain.tile / Grain.size;
      canvas.drawRRect(
        rrect,
        Paint()
          ..shader = ImageShader(noise, TileMode.repeated, TileMode.repeated, Matrix4.diagonal3Values(k, k, 1).storage)
          ..blendMode = BlendMode.overlay
          ..color = Color.fromRGBO(0, 0, 0, g.grain * drift.grain),
      );
    }

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

/// A tile of grey noise, made once at startup. Shown at [tile] logical px.
abstract final class Grain {
  static const size = 96;
  static const tile = 72.0;
  static ui.Image? image;

  static Future<void> load() async {
    if (image != null) return;
    final rnd = math.Random(7);
    final px = Uint8List(size * size * 4);
    for (var i = 0; i < px.length; i += 4) {
      final v = rnd.nextInt(256);
      px[i] = px[i + 1] = px[i + 2] = v;
      px[i + 3] = 255;
    }
    final done = Completer<ui.Image>();
    ui.decodeImageFromPixels(px, size, size, ui.PixelFormat.rgba8888, done.complete);
    image = await done.future;
  }
}
