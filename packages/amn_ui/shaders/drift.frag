#version 460 core

// The drifting sky: a linear base wash and three radial glows, all colors
// and positions computed in Dart (drift.dart) and passed in per frame.

#include <flutter/runtime_effect.glsl>

precision highp float;

uniform vec2 uSize;
uniform vec3 uBaseTop;
uniform vec3 uBaseBot;
// Each glow: center (px), radius (px), then its inner and outer colors.
uniform vec3 uGlow1;
uniform vec3 uIn1;
uniform vec3 uOut1;
uniform vec3 uGlow2;
uniform vec3 uIn2;
uniform vec3 uOut2;
uniform vec3 uGlow3;
uniform vec3 uIn3;
uniform vec3 uOut3;
// 0 = full sky, 1 = muted for pages with a purpose.
uniform float uMute;
uniform vec3 uTone;

out vec4 fragColor;

// Same stops as the design: inner at 85%, outer at 38% by 0.55, gone by 1.
vec3 glow(vec3 color, vec2 p, vec3 g, vec3 inner, vec3 outer) {
  float d = clamp(length(p - g.xy) / g.z, 0.0, 1.0);
  vec3 c;
  float a;
  if (d < 0.55) {
    float k = d / 0.55;
    c = mix(inner, outer, k);
    a = mix(0.85, 0.38, k);
  } else {
    float k = (d - 0.55) / 0.45;
    c = outer;
    a = mix(0.38, 0.0, k);
  }
  return mix(color, c, a);
}

float hash(vec2 p) {
  return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
  vec2 p = FlutterFragCoord().xy;
  vec2 dir = vec2(uSize.x * 0.27, uSize.y);
  float t = clamp(dot(p, dir) / dot(dir, dir), 0.0, 1.0);
  vec3 c = mix(uBaseTop, uBaseBot, t);
  c = glow(c, p, uGlow1, uIn1, uOut1);
  c = glow(c, p, uGlow2, uIn2, uOut2);
  c = glow(c, p, uGlow3, uIn3, uOut3);

  float lum = dot(c, vec3(0.2126, 0.7152, 0.0722));
  c = mix(c, vec3(lum), 0.5 * uMute);
  c = mix(c, uTone, 0.55 * uMute);

  // Dither so slow gradients don't band.
  c += (hash(p) - 0.5) / 255.0;
  fragColor = vec4(c, 1.0);
}
