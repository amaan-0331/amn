import 'dart:async';
import 'dart:ui';

import 'package:sensors_plus/sensors_plus.dart';

/// How far the phone has tilted from the way it's usually held, from -1 to 1
/// on each axis. The "usual" pose slowly follows the phone, so lying in bed
/// or sitting up both settle back to centre after a few seconds.
class Tilt {
  StreamSubscription<AccelerometerEvent>? _sub;
  Offset _fast = Offset.zero;
  Offset? _slow;

  Offset value = Offset.zero;

  void start() {
    _sub ??= accelerometerEventStream(samplingPeriod: SensorInterval.gameInterval).listen(
      _onEvent,
      // No accelerometer (emulator, some tablets): no tilt.
      onError: (_) => stop(),
      cancelOnError: true,
    );
  }

  void stop() {
    _sub?.cancel();
    _sub = null;
    _slow = null;
    value = Offset.zero;
  }

  void _onEvent(AccelerometerEvent e) {
    final g = Offset(e.x, e.y);
    if (_slow == null) _fast = g;
    _fast += (g - _fast) * .2;
    final slow = _slow = _slow == null ? g : _slow! + (g - _slow!) * .008;
    final d = (_fast - slow) / 3;
    // Right side down slides the glows right; top toward you slides them down.
    value = Offset(-d.dx.clamp(-1.0, 1.0), -d.dy.clamp(-1.0, 1.0));
  }
}
