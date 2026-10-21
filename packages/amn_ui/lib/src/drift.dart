import 'dart:async';
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/scheduler.dart';
import 'package:flutter/widgets.dart';

import 'skies.dart';
import 'tilt.dart';

/// Runs the drift for the whole app: the sky's clock, the tilt, how muted the
/// sky is, the grain's breathing and the accent picked from the near glow.
///
/// Painting listens to [frame] (about 30 times a second); widgets that only
/// care about the accent or the sky's name listen to the controller itself,
/// which notifies only when those change.
class DriftController extends ChangeNotifier {
  DriftController({Brightness brightness = Brightness.light, bool reduceMotion = false})
      // ignore: prefer_initializing_formals
      : _brightness = brightness,
        // ignore: prefer_initializing_formals
        _reduceMotion = reduceMotion,
        // Start somewhere new each launch.
        _t = (DateTime.now().millisecondsSinceEpoch / 1000) % 100000 {
    _compute();
    _publish(force: true);
  }

  static const _fps = 30;
  static const _parallax = .07;

  /// Ticks whenever the sky should repaint.
  Listenable get frame => _frame;
  final _frame = _Frame();
  final _tilt = Tilt();
  final _clock = Stopwatch();
  Timer? _timer;
  double _last = 0;

  double _t;
  Brightness _brightness;
  bool _reduceMotion;
  int _muters = 0;
  double _mute = 0;
  Offset _tiltNow = Offset.zero;

  /// Grain strength between 0 and 1; breathes in and out, never moves.
  double grain = .6;

  late SkyFrame sky;
  late Color accent;
  late Sky current;

  Brightness get brightness => _brightness;
  double get mute => _mute;

  set brightness(Brightness b) {
    if (b == _brightness) return;
    _brightness = b;
    _compute();
    _publish(force: true);
    notifyListeners();
    _paint();
  }

  set reduceMotion(bool v) {
    if (v == _reduceMotion) return;
    _reduceMotion = v;
    if (v) {
      _tilt.stop();
    } else if (_timer != null) {
      _tilt.start();
    }
  }

  /// Pages with a purpose mute the sky while they're open; see [DriftMute].
  void muteIn() => _muters++;
  void muteOut() => _muters = math.max(0, _muters - 1);

  void start() {
    if (_timer != null) return;
    _clock.start();
    _last = _clock.elapsedMicroseconds / 1e6;
    _timer = Timer.periodic(const Duration(microseconds: 1000000 ~/ _fps), (_) => _tick());
    if (!_reduceMotion) _tilt.start();
  }

  void stop() {
    _timer?.cancel();
    _timer = null;
    _clock.stop();
    _tilt.stop();
  }

  void _tick() {
    final now = _clock.elapsedMicroseconds / 1e6;
    final dt = math.min(now - _last, .25);
    _last = now;

    final target = _muters > 0 ? 1.0 : 0.0;
    _mute += (target - _mute) * (1 - math.exp(-dt * 3));
    if ((target - _mute).abs() < .002) _mute = target;

    if (!_reduceMotion) {
      // Muted pages drift at 40% speed too.
      _t += dt * (1 - .6 * _mute);
      _tiltNow += (_tilt.value - _tiltNow) * (1 - math.exp(-dt * 5));
      grain = _breath(now);
    }
    _compute();
    _publish();
    _paint();
  }

  // Two slow sines with unrelated periods: the grain fades in and out on an
  // uneven rhythm, between barely there and fully there.
  static double _breath(double s) {
    final w = .6 * math.sin(2 * math.pi * s / 7.3) + .4 * math.sin(2 * math.pi * s / 11.9 + 1);
    return .15 + .85 * (w + 1) / 2;
  }

  void _compute() {
    final skies = Skies.of(_brightness);
    final layers = [for (var i = 0; i < 4; i++) DriftMath.layer(i, _t, skies)];
    sky = SkyFrame(
      base: layers[0],
      glows: [
        for (var i = 1; i < 4; i++)
          () {
            final o = DriftMath.orbit(i, _t);
            final d = DriftMath.depths[i] * _parallax;
            return Glow(
              center: Offset(o.x + _tiltNow.dx * d, o.y + _tiltNow.dy * d),
              radius: o.r,
              inner: i.isOdd ? layers[i].top : layers[i].bottom,
              outer: i.isOdd ? layers[i].bottom : layers[i].top,
            );
          }(),
      ],
      mute: _mute,
      tone: _brightness == Brightness.dark ? const Color(0xFF0B0C13) : const Color(0xFFEEF0F6),
    );
    _near = layers[3];
  }

  late LayerColor _near;

  // The accent follows the near glow, but only in steps big enough to see,
  // and holds still while a muted page is open so it stays easy to focus.
  void _publish({bool force = false}) {
    var changed = false;
    final next = accentFor(_near.top, _brightness);
    if (force || (_mute < .5 && _hueGap(next, accent) >= 14)) {
      accent = next;
      changed = true;
    }
    if (force || _near.current != current) {
      current = _near.current;
      changed = true;
    }
    if (changed && !force) notifyListeners();
  }

  static double _hueGap(Color a, Color b) {
    final d = (HSLColor.fromColor(a).hue - HSLColor.fromColor(b).hue).abs();
    return math.min(d, 360 - d);
  }

  void _paint() => _frame.tick();

  @override
  void dispose() {
    stop();
    _frame.dispose();
    super.dispose();
  }
}

class _Frame extends ChangeNotifier {
  void tick() => notifyListeners();
}

class SkyFrame {
  const SkyFrame({required this.base, required this.glows, required this.mute, required this.tone});

  final LayerColor base;
  final List<Glow> glows;
  final double mute;
  final Color tone;
}

class Glow {
  const Glow({required this.center, required this.radius, required this.inner, required this.outer});

  /// Fractions of the screen; [radius] is a fraction of the longer side.
  final Offset center;
  final double radius;
  final Color inner;
  final Color outer;
}

/// Puts a [DriftController] above the app and keeps it in step with the
/// app's lifecycle, the phone's dark mode and its reduce-motion setting.
class DriftScope extends StatefulWidget {
  const DriftScope({super.key, required this.child});

  final Widget child;

  @override
  State<DriftScope> createState() => _DriftScopeState();
}

class _DriftScopeState extends State<DriftScope> with WidgetsBindingObserver {
  late final DriftController _drift;
  late final AppLifecycleListener _life;

  ui.PlatformDispatcher get _platform => WidgetsBinding.instance.platformDispatcher;

  @override
  void initState() {
    super.initState();
    _drift = DriftController(
      brightness: _platform.platformBrightness,
      reduceMotion: _platform.accessibilityFeatures.disableAnimations,
    );
    WidgetsBinding.instance.addObserver(this);
    _life = AppLifecycleListener(onResume: _drift.start, onHide: _drift.stop);
    final state = SchedulerBinding.instance.lifecycleState;
    if (state == null || state == AppLifecycleState.resumed) _drift.start();
  }

  @override
  void didChangePlatformBrightness() => _drift.brightness = _platform.platformBrightness;

  @override
  void didChangeAccessibilityFeatures() => _drift.reduceMotion = _platform.accessibilityFeatures.disableAnimations;

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _life.dispose();
    _drift.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Drift(notifier: _drift, child: widget.child);
}

/// Reads the app's [DriftController]. `Drift.of` rebuilds when the accent or
/// the sky's name changes; `Drift.read` doesn't.
class Drift extends InheritedNotifier<DriftController> {
  const Drift({super.key, required super.notifier, required super.child});

  static DriftController of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<Drift>()!.notifier!;

  static DriftController read(BuildContext context) =>
      (context.getElementForInheritedWidgetOfExactType<Drift>()!.widget as Drift).notifier!;
}

/// Mutes the sky while this widget is on screen, for pages with a purpose
/// (edit pages, the lab). The sky fades to a calmer, greyer version of itself
/// and slows down; it comes back when the page closes.
class DriftMute extends StatefulWidget {
  const DriftMute({super.key, required this.child});

  final Widget child;

  @override
  State<DriftMute> createState() => _DriftMuteState();
}

class _DriftMuteState extends State<DriftMute> {
  DriftController? _drift;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_drift != null) return;
    _drift = Drift.read(context)..muteIn();
  }

  @override
  void dispose() {
    _drift?.muteOut();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}

/// The sky itself, filling its box. Put it behind everything once (see
/// [DriftBackground]).
class DriftSky extends StatefulWidget {
  const DriftSky({super.key});

  @override
  State<DriftSky> createState() => _DriftSkyState();
}

class _DriftSkyState extends State<DriftSky> {
  static Future<ui.FragmentProgram>? _program;
  ui.FragmentShader? _shader;

  @override
  void initState() {
    super.initState();
    (_program ??= ui.FragmentProgram.fromAsset('packages/amn_ui/shaders/drift.frag')).then((p) {
      if (mounted) setState(() => _shader = p.fragmentShader());
    });
  }

  @override
  void dispose() {
    _shader?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final drift = Drift.read(context);
    return RepaintBoundary(
      child: CustomPaint(painter: _SkyPainter(drift, _shader), size: Size.infinite),
    );
  }
}

class _SkyPainter extends CustomPainter {
  _SkyPainter(this.drift, this.shader) : super(repaint: drift.frame);

  final DriftController drift;
  final ui.FragmentShader? shader;

  @override
  void paint(Canvas canvas, Size size) {
    final s = drift.sky;
    final rect = Offset.zero & size;
    final m = math.max(size.width, size.height);
    final shader = this.shader;
    if (shader == null) {
      // Until the shader loads (first frame or two): the base wash alone.
      canvas.drawRect(
        rect,
        Paint()..shader = ui.Gradient.linear(Offset.zero, Offset(size.width * .27, size.height), [s.base.top, s.base.bottom]),
      );
      return;
    }
    var i = 0;
    void f(double v) => shader.setFloat(i++, v);
    void c(Color v) {
      f(v.r);
      f(v.g);
      f(v.b);
    }

    f(size.width);
    f(size.height);
    c(s.base.top);
    c(s.base.bottom);
    for (final g in s.glows) {
      f(g.center.dx * size.width);
      f(g.center.dy * size.height);
      f(g.radius * m);
      c(g.inner);
      c(g.outer);
    }
    f(s.mute);
    c(s.tone);
    canvas.drawRect(rect, Paint()..shader = shader);
  }

  @override
  bool shouldRepaint(_SkyPainter old) => old.shader != shader;
}

/// The sky behind [child], with a shared backdrop for every glass surface.
/// Use it as `MaterialApp.builder`.
class DriftBackground extends StatelessWidget {
  const DriftBackground({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) => Stack(
        fit: StackFit.expand,
        children: [const DriftSky(), BackdropGroup(child: child)],
      );
}
