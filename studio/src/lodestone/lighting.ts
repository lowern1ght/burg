import { vec3 } from 'gl-matrix';

export type SunlightPreset = {
  direction?: vec3;
  color?: [number, number, number];
  ambientColor?: [number, number, number];
  fillColor?: [number, number, number];
  rimColor?: [number, number, number];
  intensity?: number;
  ambientIntensity?: number;
  fillIntensity?: number;
  rimIntensity?: number;
  exposure?: number;
  sky?: {
    zenithColor?: [number, number, number];
    horizonColor?: [number, number, number];
    groundColor?: [number, number, number];
    sunGlowColor?: [number, number, number];
    sunGlowIntensity?: number;
    sunGlowExponent?: number;
    stars?: { enabled?: boolean; density?: number; brightness?: number };
  };
  fog?: {
    color?: [number, number, number];
    density?: number;
    heightFalloff?: number;
  };
};

export const LIGHTING_PRESETS: Record<'day' | 'night', SunlightPreset> = {
  day: {
    direction: vec3.fromValues(0.35, 0.85, 0.25),
    color: [1.0, 0.75, 0.45],
    ambientColor: [0.25, 0.4, 0.6],
    fillColor: [0.35, 0.28, 0.5],
    rimColor: [1.0, 0.55, 0.25],
    intensity: 1.15,
    ambientIntensity: 0.62,
    fillIntensity: 0.32,
    rimIntensity: 0.35,
    exposure: 1.08,
    sky: {
      zenithColor: [0.24, 0.48, 0.84],
      horizonColor: [0.78, 0.9, 1.0],
      groundColor: [0.18, 0.2, 0.22],
      sunGlowColor: [1.0, 0.45, 0.15],
      sunGlowIntensity: 0.6,
      sunGlowExponent: 6.0,
      stars: { enabled: false, density: 0.003, brightness: 0.6 },
    },
    fog: {
      color: [0.78, 0.86, 0.94],
      density: 0.00012,
      heightFalloff: 0.001,
    },
  },
  night: {
    direction: vec3.fromValues(-0.32, 0.5, -0.42),
    color: [0.62, 0.72, 1.0],
    ambientColor: [0.34, 0.43, 0.72],
    fillColor: [0.13, 0.18, 0.36],
    rimColor: [0.5, 0.64, 1.0],
    intensity: 0.28,
    ambientIntensity: 0.52,
    fillIntensity: 0.2,
    rimIntensity: 0.42,
    exposure: 0.92,
    sky: {
      zenithColor: [0.015, 0.025, 0.08],
      horizonColor: [0.055, 0.085, 0.18],
      groundColor: [0.018, 0.022, 0.038],
      sunGlowColor: [0.48, 0.6, 1.0],
      sunGlowIntensity: 0.22,
      sunGlowExponent: 20,
      stars: { enabled: true, density: 0.72, brightness: 0.92 },
    },
    fog: {
      color: [0.035, 0.05, 0.1],
      density: 0.00008,
      heightFalloff: 0.00065,
    },
  },
};
