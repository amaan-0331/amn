import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import 'skies.dart';

/// Glass and ink colours for one brightness. Read with [AmnGlass.of].
@immutable
class AmnGlass extends ThemeExtension<AmnGlass> {
  const AmnGlass({
    required this.ink,
    required this.sub,
    required this.tint,
    required this.edge,
    required this.highlight,
    required this.divider,
    required this.grain,
  });

  static const light = AmnGlass(
    ink: Color(0xFF10131D),
    sub: Color(0xFF2E3446),
    tint: Color(0x61FFFFFF),
    edge: Color(0x8CFFFFFF),
    highlight: Color(0xB3FFFFFF),
    divider: Color(0x1A10131D),
    grain: .16,
  );

  static const dark = AmnGlass(
    ink: Color(0xFFF2F3FA),
    sub: Color(0xFFC6CAD9),
    tint: Color(0x570A0C16),
    edge: Color(0x24FFFFFF),
    highlight: Color(0x2EFFFFFF),
    divider: Color(0x1AFFFFFF),
    grain: .22,
  );

  /// Blur 22 and saturation 150%, shared by every glass surface.
  static final ui.ImageFilter filter = ui.ImageFilter.compose(
    outer: const ColorFilter.matrix(_saturate150),
    inner: ui.ImageFilter.blur(sigmaX: 22, sigmaY: 22, tileMode: TileMode.mirror),
  );

  static const _saturate150 = <double>[
    1.3936, -0.3576, -0.0360, 0, 0, //
    -0.1064, 1.1424, -0.0360, 0, 0, //
    -0.1064, -0.3576, 1.4640, 0, 0, //
    0, 0, 0, 1, 0,
  ];

  static AmnGlass of(BuildContext context) => Theme.of(context).extension<AmnGlass>()!;

  final Color ink;
  final Color sub;
  final Color tint;
  final Color edge;
  final Color highlight;
  final Color divider;

  /// Peak grain opacity; the grain breathes between about 15% and 100% of it.
  final double grain;

  @override
  AmnGlass copyWith() => this;

  @override
  AmnGlass lerp(AmnGlass? other, double t) => t < .5 || other == null ? this : other;
}

/// Display type: Barlow Condensed Bold, the widgets' numerals.
abstract final class AmnType {
  static TextStyle display(double size, {Color? color, double? height, double letterSpacing = 0}) => TextStyle(
        fontFamily: 'BarlowCondensed',
        package: 'amn_ui',
        fontWeight: FontWeight.w700,
        fontSize: size,
        height: height,
        color: color,
        letterSpacing: letterSpacing,
        fontFeatures: const [FontFeature.tabularFigures()],
      );
}

abstract final class AmnTheme {
  static ThemeData build(Brightness brightness, Color accent) {
    final g = brightness == Brightness.dark ? AmnGlass.dark : AmnGlass.light;
    final scheme = ColorScheme.fromSeed(seedColor: accent, brightness: brightness).copyWith(
      primary: accent,
      onPrimary: onAccent(brightness),
      onSurface: g.ink,
      onSurfaceVariant: g.sub,
      outlineVariant: g.divider,
      secondaryContainer: accent.withValues(alpha: .26),
      onSecondaryContainer: g.ink,
    );
    final base = ThemeData(colorScheme: scheme, useMaterial3: true);
    final text = base.textTheme.apply(bodyColor: g.ink, displayColor: g.ink);
    return base.copyWith(
      extensions: [g],
      scaffoldBackgroundColor: Colors.transparent,
      textTheme: text.copyWith(
        displayLarge: AmnType.display(96, color: g.ink, height: .9),
        displayMedium: AmnType.display(64, color: g.ink, height: .95),
        displaySmall: AmnType.display(44, color: g.ink, height: 1),
        headlineMedium: AmnType.display(30, color: g.ink, letterSpacing: .2),
        headlineSmall: AmnType.display(24, color: g.ink),
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: WidgetStateColor.resolveWith((s) => s.contains(WidgetState.scrolledUnder) ? g.tint : Colors.transparent),
        foregroundColor: g.ink,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
      ),
      dividerTheme: DividerThemeData(color: g.divider, space: 1, thickness: 1),
      listTileTheme: ListTileThemeData(iconColor: g.sub, subtitleTextStyle: text.bodyMedium?.copyWith(color: g.sub)),
      segmentedButtonTheme: SegmentedButtonThemeData(
        style: ButtonStyle(
          side: WidgetStatePropertyAll(BorderSide(color: g.edge)),
          backgroundColor: WidgetStateColor.resolveWith((s) => s.contains(WidgetState.selected) ? accent : g.tint),
          foregroundColor: WidgetStateColor.resolveWith((s) => s.contains(WidgetState.selected) ? onAccent(brightness) : g.ink),
          iconColor: WidgetStateColor.resolveWith((s) => s.contains(WidgetState.selected) ? onAccent(brightness) : g.ink),
        ),
      ),
      searchBarTheme: SearchBarThemeData(
        backgroundColor: WidgetStatePropertyAll(g.tint),
        elevation: const WidgetStatePropertyAll(0),
        side: WidgetStatePropertyAll(BorderSide(color: g.edge)),
      ),
      pageTransitionsTheme: PageTransitionsTheme(
        builders: {for (final p in TargetPlatform.values) p: const DriftPageTransitionsBuilder()},
      ),
    );
  }
}

/// Pages are see-through, so a slide would show both at once. Instead the old
/// page fades out while the new one fades in and rises a little.
class DriftPageTransitionsBuilder extends PageTransitionsBuilder {
  const DriftPageTransitionsBuilder();

  static final _in = CurveTween(curve: Curves.easeOutCubic);
  static final _rise = Tween(begin: const Offset(0, .025), end: Offset.zero).chain(_in);
  static final _out = Tween(begin: 1.0, end: 0.0).chain(CurveTween(curve: const Interval(0, .6, curve: Curves.easeOut)));

  @override
  Duration get transitionDuration => const Duration(milliseconds: 420);

  @override
  Widget buildTransitions<T>(
    PageRoute<T> route,
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) =>
      FadeTransition(
        opacity: secondaryAnimation.drive(_out),
        child: FadeTransition(
          opacity: animation.drive(_in),
          child: SlideTransition(position: animation.drive(_rise), child: child),
        ),
      );
}
