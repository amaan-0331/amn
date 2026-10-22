import 'dart:math' as math;

import 'package:flutter/scheduler.dart';

/// Temporary: logs frame timings every 3 s when built with --dart-define=PERF=true.
void startPerfProbe() {
  if (!const bool.fromEnvironment('PERF')) return;
  final build = <double>[], raster = <double>[];
  var last = DateTime.now();
  SchedulerBinding.instance.addTimingsCallback((List<FrameTiming> ts) {
    for (final t in ts) {
      build.add(t.buildDuration.inMicroseconds / 1000);
      raster.add(t.rasterDuration.inMicroseconds / 1000);
    }
    final now = DateTime.now();
    if (now.difference(last).inMilliseconds < 3000 || build.isEmpty) return;
    String q(List<double> v, double p) => (v..sort())[math.min(v.length - 1, (v.length * p).floor())].toStringAsFixed(1);
    final slow = [for (var i = 0; i < build.length; i++) if (build[i] > 8.3 || raster[i] > 8.3) 1].length;
    // ignore: avoid_print
    print('[perf] n=${build.length} build p50=${q(build, .5)} p90=${q(build, .9)} max=${q(build, 1)} '
        'raster p50=${q(raster, .5)} p90=${q(raster, .9)} max=${q(raster, 1)} over8ms=$slow');
    build.clear();
    raster.clear();
    last = now;
  });
}
