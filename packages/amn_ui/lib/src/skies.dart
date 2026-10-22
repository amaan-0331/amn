import 'dart:math' as math;
import 'dart:ui';

import 'package:flutter/painting.dart' show HSLColor;

/// One widget sky: the two gradient stops the weather widget paints for a
/// condition at a time of day.
class Sky {
  const Sky(this.top, this.bottom, this.condition, this.phase);

  final Color top;
  final Color bottom;
  final String condition;
  final String phase;

  /// "rain at dusk", "clear night".
  String get name => phase == 'night' ? '${condition.toLowerCase()} night' : phase == 'day' ? '${condition.toLowerCase()} by day' : '${condition.toLowerCase()} at $phase';
}

/// The 28 widget skies (7 conditions × dawn, day, dusk, night), with the same
/// table and dawn/dusk/dark rules as the widgets' Palettes.kt.
abstract final class Skies {
  static final List<Sky> light = _build(dark: false);
  static final List<Sky> dark = _build(dark: true);

  static List<Sky> of(Brightness b) => b == Brightness.dark ? dark : light;

  static const _phases = ['dawn', 'day', 'dusk', 'night'];

  static const _table = <String, Map<String, List<int>>>{
    'Clear': {
      'day': [0xFFFFD25A, 0xFFFF8F3A],
      'night': [0xFF101B52, 0xFF2C2775],
      'dawn': [0xFFFFB59A, 0xFFFFDFAE],
      'dusk': [0xFFC2456B, 0xFF3E2A7A],
    },
    'Partly cloudy': {'day': [0xFF8CCBFF, 0xFFFFD889], 'night': [0xFF17265A, 0xFF3B4380]},
    'Cloudy': {'day': [0xFFC9D3E2, 0xFF8E9DB5], 'night': [0xFF2C3446, 0xFF161B26]},
    'Rain': {'day': [0xFF4E70AE, 0xFF2D4677], 'night': [0xFF1C2A48, 0xFF0B1324]},
    'Storm': {'day': [0xFF4A3B7A, 0xFF1D1838], 'night': [0xFF1E1236, 0xFF08060F]},
    'Snow': {'day': [0xFFEEF4FF, 0xFFBCD0EE], 'night': [0xFF2D3D63, 0xFF141E37]},
    'Fog': {'day': [0xFFE2DCD1, 0xFFADA699], 'night': [0xFF3B3934, 0xFF1E1C19]},
  };

  static const _night = Color(0xFF0A0C15);

  static List<Sky> _build({required bool dark}) => [
        for (final MapEntry(key: cond, value: c) in _table.entries)
          for (final phase in _phases) _sky(cond, c, phase, dark),
      ];

  static Sky _sky(String cond, Map<String, List<int>> c, String phase, bool dark) {
    late Color top, bot;
    final own = c[phase];
    if (own != null) {
      top = Color(own[0]);
      bot = Color(own[1]);
    } else {
      top = Color(c['day']![0]);
      bot = Color(c['day']![1]);
      if (phase == 'dawn') {
        top = mix(top, const Color(0xFFFF9E7A), .38);
        bot = mix(bot, const Color(0xFFFFD9A8), .25);
      } else if (phase == 'dusk') {
        top = mix(top, const Color(0xFFC2456B), .35);
        bot = mix(bot, const Color(0xFF3E2A7A), .45);
      }
    }
    if (dark && phase != 'night') {
      final soft = phase == 'dawn' || phase == 'dusk';
      top = mix(top, _night, soft ? .32 : .55);
      bot = mix(bot, _night, soft ? .40 : .62);
    }
    return Sky(top, bot, cond, phase);
  }
}

Color mix(Color a, Color b, double t) => Color.lerp(a, b, t)!;

/// The drift: four layers, each walking through the skies on its own clock.
/// Clocks of 37/29/23/17 s share no common beat, and each layer's next sky
/// comes from a seeded hash, so the whole never repeats in practice.
abstract final class DriftMath {
  static const periods = [37.0, 29.0, 23.0, 17.0];
  static const depths = [0.0, .4, .7, 1.0];

  static double _hash(int i, int n) {
    final x = math.sin(i * 127.1 + n * 311.7 + 7.3) * 43758.5453;
    return x - x.floorToDouble();
  }

  /// Sky index for layer [i] in step [n]; never the one it is leaving.
  static int pick(int i, int n, int count) {
    var a = (_hash(i, n) * count).floor();
    final prev = (_hash(i, n - 1) * count).floor();
    if (a == prev) a = (a + 1 + (_hash(i + 9, n) * (count - 1)).floor()) % count;
    return a;
  }

  static double _smooth(double x) => x * x * (3 - 2 * x);

  static LayerColor layer(int i, double t, List<Sky> skies) {
    final p = periods[i];
    final n = (t / p).floor();
    final f = _smooth(t / p - n);
    final from = skies[pick(i, n, skies.length)];
    final to = skies[pick(i, n + 1, skies.length)];
    return LayerColor(mix(from.top, to.top, f), mix(from.bottom, to.bottom, f), from, to, f);
  }

  /// Where glow [i] sits (fractions of the screen) and its radius (fraction
  /// of the longer side). Two sines per axis with unrelated periods.
  static ({double x, double y, double r}) orbit(int i, double t) {
    const tau = math.pi * 2;
    final a = 41.0 + i * 13, b = 19.0 + i * 7, c = 53.0 + i * 11, d = 27.0 + i * 5;
    return (
      x: .5 + .32 * math.sin(tau * t / a + i * 2.1) + .13 * math.sin(tau * t / b + i),
      y: .46 + .30 * math.cos(tau * t / c + i * 1.3) + .10 * math.sin(tau * t / d + .7 * i),
      r: (.55 + .13 * math.sin(tau * t / (31 + i * 5) + i)) * (1 - .12 * i),
    );
  }
}

class LayerColor {
  const LayerColor(this.top, this.bottom, this.from, this.to, this.f);

  final Color top;
  final Color bottom;
  final Sky from;
  final Sky to;
  final double f;

  /// The sky this layer is mostly showing right now.
  Sky get current => f < .5 ? from : to;
}

/// The accent: the near glow's hue, saturated and set to a lightness that
/// reads on the glass (0.40 on light, 0.72 on dark).
Color accentFor(Color c, Brightness b) {
  final hsl = HSLColor.fromColor(c);
  return hsl.withSaturation(math.max(hsl.saturation, .55)).withLightness(b == Brightness.dark ? .72 : .40).toColor();
}

/// Text on the accent.
Color onAccent(Brightness b) => b == Brightness.dark ? const Color(0xFF11131C) : const Color(0xFFFFFFFF);
