import {
  useState,
  type Dispatch,
  type FormEvent,
  type ReactNode,
  type SetStateAction,
} from 'react';
import {
  ChevronDown,
  ChevronRight,
  Moon,
  RotateCcw,
  Save,
  Settings2,
  Sun,
  X,
} from 'lucide-react';
import { LIGHTING_PRESETS, type SunlightPreset } from '../lodestone/lighting';

export const VIEWER_SETTINGS_STORAGE_KEY = 'burg-studio:viewer-settings';

export type ViewerSettingsMode = 'day' | 'night' | 'custom';

export type CustomViewerPreset = {
  name: string;
  preset: Partial<SunlightPreset>;
};

export type ViewerSettingsState = {
  mode: ViewerSettingsMode;
  overrides: Partial<SunlightPreset>;
  customPresets: CustomViewerPreset[];
};

type RgbColor = [number, number, number];

type ViewerSettingsPanelProps = {
  settings: ViewerSettingsState;
  effectivePreset: SunlightPreset;
  onSettingsChange: Dispatch<SetStateAction<ViewerSettingsState>>;
  onClose: () => void;
};

type SettingsGroupProps = {
  title: string;
  children: ReactNode;
};

type RangeControlProps = {
  label: string;
  value: number;
  min: number;
  max: number;
  step: number;
  decimals?: number;
  onChange: (value: number) => void;
};

type ColorControlProps = {
  label: string;
  value: RgbColor;
  onChange: (value: RgbColor) => void;
};

function createDefaultViewerSettings(): ViewerSettingsState {
  return {
    mode: 'day',
    overrides: {},
    customPresets: [],
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isUnknownArray(value: unknown): value is unknown[] {
  return Array.isArray(value);
}

function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function isBoolean(value: unknown): value is boolean {
  return typeof value === 'boolean';
}

function isOptional(value: unknown, validator: (candidate: unknown) => boolean): boolean {
  return value === undefined || validator(value);
}

function isRgbColor(value: unknown): value is RgbColor {
  return isUnknownArray(value)
    && value.length === 3
    && value.every(isFiniteNumber);
}

function isStarsPreset(value: unknown): boolean {
  return isRecord(value)
    && isOptional(value.enabled, isBoolean)
    && isOptional(value.density, isFiniteNumber)
    && isOptional(value.brightness, isFiniteNumber);
}

function isSkyPreset(value: unknown): boolean {
  return isRecord(value)
    && isOptional(value.zenithColor, isRgbColor)
    && isOptional(value.horizonColor, isRgbColor)
    && isOptional(value.groundColor, isRgbColor)
    && isOptional(value.sunGlowColor, isRgbColor)
    && isOptional(value.sunGlowIntensity, isFiniteNumber)
    && isOptional(value.sunGlowExponent, isFiniteNumber)
    && isOptional(value.stars, isStarsPreset);
}

function isFogPreset(value: unknown): boolean {
  return isRecord(value)
    && isOptional(value.color, isRgbColor)
    && isOptional(value.density, isFiniteNumber)
    && isOptional(value.heightFalloff, isFiniteNumber);
}

function isSunlightPreset(value: unknown): value is Partial<SunlightPreset> {
  return isRecord(value)
    && isOptional(value.direction, isRgbColor)
    && isOptional(value.color, isRgbColor)
    && isOptional(value.ambientColor, isRgbColor)
    && isOptional(value.fillColor, isRgbColor)
    && isOptional(value.rimColor, isRgbColor)
    && isOptional(value.intensity, isFiniteNumber)
    && isOptional(value.ambientIntensity, isFiniteNumber)
    && isOptional(value.fillIntensity, isFiniteNumber)
    && isOptional(value.rimIntensity, isFiniteNumber)
    && isOptional(value.exposure, isFiniteNumber)
    && isOptional(value.sky, isSkyPreset)
    && isOptional(value.fog, isFogPreset);
}

function isViewerSettingsMode(value: unknown): value is ViewerSettingsMode {
  return value === 'day' || value === 'night' || value === 'custom';
}

function isCustomViewerPreset(value: unknown): value is CustomViewerPreset {
  return isRecord(value)
    && typeof value.name === 'string'
    && value.name.trim().length > 0
    && isSunlightPreset(value.preset);
}

function copyDirection(direction: NonNullable<SunlightPreset['direction']>): RgbColor {
  return [direction[0], direction[1], direction[2]];
}

export function mergeSunlightPreset(
  base: Partial<SunlightPreset>,
  overrides: Partial<SunlightPreset>,
): SunlightPreset {
  const direction = overrides.direction ?? base.direction;
  const stars = base.sky?.stars === undefined && overrides.sky?.stars === undefined
    ? undefined
    : { ...base.sky?.stars, ...overrides.sky?.stars };
  const sky = base.sky === undefined && overrides.sky === undefined
    ? undefined
    : {
        ...base.sky,
        ...overrides.sky,
        ...(stars === undefined ? {} : { stars }),
      };
  const fog = base.fog === undefined && overrides.fog === undefined
    ? undefined
    : { ...base.fog, ...overrides.fog };

  return {
    ...base,
    ...overrides,
    ...(direction === undefined ? {} : { direction: copyDirection(direction) }),
    ...(sky === undefined ? {} : { sky }),
    ...(fog === undefined ? {} : { fog }),
  };
}

function cloneSunlightPreset(preset: Partial<SunlightPreset>): SunlightPreset {
  return mergeSunlightPreset({}, preset);
}

export function resolveEffectivePreset(settings: ViewerSettingsState): SunlightPreset {
  const base = settings.mode === 'night' ? LIGHTING_PRESETS.night : LIGHTING_PRESETS.day;
  return mergeSunlightPreset(base, settings.overrides);
}

function parseViewerSettings(value: unknown): ViewerSettingsState | null {
  if (!isRecord(value)
    || !isViewerSettingsMode(value.mode)
    || !isSunlightPreset(value.overrides)
    || !isUnknownArray(value.customPresets)
    || !value.customPresets.every(isCustomViewerPreset)) {
    return null;
  }

  return {
    mode: value.mode,
    overrides: cloneSunlightPreset(value.overrides),
    customPresets: value.customPresets.map(customPreset => ({
      name: customPreset.name,
      preset: cloneSunlightPreset(customPreset.preset),
    })),
  };
}

export function loadViewerSettings(): ViewerSettingsState {
  if (typeof localStorage === 'undefined') {
    return createDefaultViewerSettings();
  }

  const stored = localStorage.getItem(VIEWER_SETTINGS_STORAGE_KEY);
  if (stored === null) {
    return createDefaultViewerSettings();
  }

  try {
    const parsed: unknown = JSON.parse(stored);
    return parseViewerSettings(parsed) ?? createDefaultViewerSettings();
  } catch (error) {
    console.warn('Unable to restore viewer settings', error);
    return createDefaultViewerSettings();
  }
}

export function persistViewerSettings(settings: ViewerSettingsState): void {
  try {
    localStorage.setItem(VIEWER_SETTINGS_STORAGE_KEY, JSON.stringify(settings));
  } catch (error) {
    console.warn('Unable to persist viewer settings', error);
  }
}

function resolveDirection(direction: SunlightPreset['direction']): RgbColor {
  return direction === undefined ? [0, 1, 0] : copyDirection(direction);
}

function resolveColor(value: RgbColor | undefined, fallback: RgbColor | undefined): RgbColor {
  return value ?? fallback ?? [0, 0, 0];
}

function resolveNumber(value: number | undefined, fallback: number | undefined): number {
  return value ?? fallback ?? 0;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function rgbToHex(color: RgbColor): string {
  return `#${color
    .map(component => Math.round(clamp(component, 0, 1) * 255).toString(16).padStart(2, '0'))
    .join('')}`;
}

function hexToRgb(hex: string): RgbColor {
  return [
    Number.parseInt(hex.slice(1, 3), 16) / 255,
    Number.parseInt(hex.slice(3, 5), 16) / 255,
    Number.parseInt(hex.slice(5, 7), 16) / 255,
  ];
}

export function ViewerSettingsPanel({
  settings,
  effectivePreset,
  onSettingsChange,
  onClose,
}: ViewerSettingsPanelProps) {
  const [showSaveForm, setShowSaveForm] = useState(false);
  const [presetName, setPresetName] = useState('');
  const dayPreset = LIGHTING_PRESETS.day;
  const direction = resolveDirection(effectivePreset.direction ?? dayPreset.direction);
  const sunColor = resolveColor(effectivePreset.color, dayPreset.color);
  const ambientColor = resolveColor(effectivePreset.ambientColor, dayPreset.ambientColor);
  const fillColor = resolveColor(effectivePreset.fillColor, dayPreset.fillColor);
  const rimColor = resolveColor(effectivePreset.rimColor, dayPreset.rimColor);
  const sky = effectivePreset.sky;
  const daySky = dayPreset.sky;
  const stars = sky?.stars;
  const dayStars = daySky?.stars;
  const fog = effectivePreset.fog;
  const dayFog = dayPreset.fog;
  const starsEnabled = stars?.enabled ?? dayStars?.enabled ?? false;

  function updateOverrides(patch: Partial<SunlightPreset>): void {
    onSettingsChange(current => ({
      ...current,
      overrides: mergeSunlightPreset(current.overrides, patch),
    }));
  }

  function updateDirection(axis: 0 | 1 | 2, value: number): void {
    const nextDirection: RgbColor = [direction[0], direction[1], direction[2]];
    nextDirection[axis] = value;
    updateOverrides({ direction: nextDirection });
  }

  function applyBuiltIn(mode: 'day' | 'night'): void {
    onSettingsChange(current => ({
      ...current,
      mode,
      overrides: {},
    }));
  }

  function resetOverrides(): void {
    onSettingsChange(current => ({
      ...current,
      mode: current.mode === 'custom' ? 'day' : current.mode,
      overrides: {},
    }));
  }

  function applyCustomPreset(customPreset: CustomViewerPreset): void {
    onSettingsChange(current => ({
      ...current,
      mode: 'custom',
      overrides: cloneSunlightPreset(customPreset.preset),
    }));
  }

  function handleSavePreset(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();
    const name = presetName.trim();
    if (name.length === 0) {
      return;
    }

    const preset = cloneSunlightPreset(effectivePreset);
    const normalizedName = name.toLowerCase();
    onSettingsChange(current => {
      const exists = current.customPresets.some(
        customPreset => customPreset.name.toLowerCase() === normalizedName,
      );
      const customPresets = exists
        ? current.customPresets.map(customPreset => (
            customPreset.name.toLowerCase() === normalizedName
              ? { name, preset }
              : customPreset
          ))
        : [...current.customPresets, { name, preset }];

      return {
        mode: 'custom',
        overrides: preset,
        customPresets,
      };
    });
    setPresetName('');
    setShowSaveForm(false);
  }

  return (
    <aside className="viewer-settings-panel" aria-label="Viewer settings">
      <div className="viewer-settings-header">
        <h3><Settings2 className="icon" />Viewer settings</h3>
        <button
          type="button"
          className="viewer-settings-close"
          onClick={onClose}
          aria-label="Close viewer settings"
        >
          <X className="icon" />
        </button>
      </div>

      <div className="viewer-settings-scroll">
        <div className="settings-presets">
          <div className="settings-preset-row">
            <button
              type="button"
              className={`settings-preset-chip ${settings.mode === 'day' ? 'active' : ''}`}
              onClick={() => applyBuiltIn('day')}
            >
              <Sun className="icon" />
              Day
            </button>
            <button
              type="button"
              className={`settings-preset-chip ${settings.mode === 'night' ? 'active' : ''}`}
              onClick={() => applyBuiltIn('night')}
            >
              <Moon className="icon" />
              Night
            </button>
            <button type="button" className="settings-preset-chip" onClick={resetOverrides}>
              <RotateCcw className="icon" />
              Reset
            </button>
            <button
              type="button"
              className={`settings-preset-chip ${showSaveForm ? 'active' : ''}`}
              onClick={() => setShowSaveForm(current => !current)}
            >
              <Save className="icon" />
              Save…
            </button>
          </div>

          {settings.customPresets.length > 0 && (
            <div className="settings-preset-row settings-custom-preset-row">
              {settings.customPresets.map(customPreset => (
                <button
                  key={customPreset.name}
                  type="button"
                  className="settings-preset-chip custom"
                  onClick={() => applyCustomPreset(customPreset)}
                >
                  {customPreset.name}
                </button>
              ))}
            </div>
          )}

          {showSaveForm && (
            <form className="settings-save-row" onSubmit={handleSavePreset}>
              <input
                autoFocus
                type="text"
                value={presetName}
                maxLength={40}
                placeholder="Preset name"
                aria-label="Custom preset name"
                onChange={event => setPresetName(event.currentTarget.value)}
              />
              <button
                type="submit"
                className="settings-save-confirm"
                disabled={presetName.trim().length === 0}
                aria-label="Save custom preset"
              >
                <Save className="icon" />
              </button>
              <button
                type="button"
                className="settings-save-cancel"
                onClick={() => {
                  setPresetName('');
                  setShowSaveForm(false);
                }}
                aria-label="Cancel custom preset"
              >
                <X className="icon" />
              </button>
            </form>
          )}
        </div>

        <SettingsGroup title="Sun">
          <div className="settings-subheading">Direction</div>
          <RangeControl
            label="X"
            value={direction[0]}
            min={-1}
            max={1}
            step={0.01}
            onChange={value => updateDirection(0, value)}
          />
          <RangeControl
            label="Y"
            value={direction[1]}
            min={-1}
            max={1}
            step={0.01}
            onChange={value => updateDirection(1, value)}
          />
          <RangeControl
            label="Z"
            value={direction[2]}
            min={-1}
            max={1}
            step={0.01}
            onChange={value => updateDirection(2, value)}
          />
          <ColorControl label="Color" value={sunColor} onChange={color => updateOverrides({ color })} />
          <RangeControl
            label="Intensity"
            value={resolveNumber(effectivePreset.intensity, dayPreset.intensity)}
            min={0}
            max={2}
            step={0.01}
            onChange={intensity => updateOverrides({ intensity })}
          />
        </SettingsGroup>

        <SettingsGroup title="Ambient">
          <ColorControl
            label="Color"
            value={ambientColor}
            onChange={color => updateOverrides({ ambientColor: color })}
          />
          <RangeControl
            label="Intensity"
            value={resolveNumber(effectivePreset.ambientIntensity, dayPreset.ambientIntensity)}
            min={0}
            max={2}
            step={0.01}
            onChange={ambientIntensity => updateOverrides({ ambientIntensity })}
          />
        </SettingsGroup>

        <SettingsGroup title="Fill & Rim">
          <ColorControl
            label="Fill color"
            value={fillColor}
            onChange={color => updateOverrides({ fillColor: color })}
          />
          <RangeControl
            label="Fill intensity"
            value={resolveNumber(effectivePreset.fillIntensity, dayPreset.fillIntensity)}
            min={0}
            max={2}
            step={0.01}
            onChange={fillIntensity => updateOverrides({ fillIntensity })}
          />
          <ColorControl
            label="Rim color"
            value={rimColor}
            onChange={color => updateOverrides({ rimColor: color })}
          />
          <RangeControl
            label="Rim intensity"
            value={resolveNumber(effectivePreset.rimIntensity, dayPreset.rimIntensity)}
            min={0}
            max={2}
            step={0.01}
            onChange={rimIntensity => updateOverrides({ rimIntensity })}
          />
        </SettingsGroup>

        <SettingsGroup title="Sky">
          <ColorControl
            label="Zenith"
            value={resolveColor(sky?.zenithColor, daySky?.zenithColor)}
            onChange={zenithColor => updateOverrides({ sky: { zenithColor } })}
          />
          <ColorControl
            label="Horizon"
            value={resolveColor(sky?.horizonColor, daySky?.horizonColor)}
            onChange={horizonColor => updateOverrides({ sky: { horizonColor } })}
          />
          <ColorControl
            label="Ground"
            value={resolveColor(sky?.groundColor, daySky?.groundColor)}
            onChange={groundColor => updateOverrides({ sky: { groundColor } })}
          />
          <ColorControl
            label="Sun glow"
            value={resolveColor(sky?.sunGlowColor, daySky?.sunGlowColor)}
            onChange={sunGlowColor => updateOverrides({ sky: { sunGlowColor } })}
          />
          <RangeControl
            label="Glow intensity"
            value={resolveNumber(sky?.sunGlowIntensity, daySky?.sunGlowIntensity)}
            min={0}
            max={2}
            step={0.01}
            onChange={sunGlowIntensity => updateOverrides({ sky: { sunGlowIntensity } })}
          />
        </SettingsGroup>

        <SettingsGroup title="Stars">
          <div className="settings-row settings-toggle-row">
            <span>Enabled</span>
            <button
              type="button"
              className={`settings-switch ${starsEnabled ? 'enabled' : ''}`}
              role="switch"
              aria-checked={starsEnabled}
              onClick={() => updateOverrides({ sky: { stars: { enabled: !starsEnabled } } })}
            >
              <span className="settings-switch-track"><span /></span>
              <span>{starsEnabled ? 'On' : 'Off'}</span>
            </button>
          </div>
          {starsEnabled && (
            <>
              <RangeControl
                label="Density"
                value={resolveNumber(stars?.density, dayStars?.density)}
                min={0}
                max={1}
                step={0.01}
                onChange={density => updateOverrides({ sky: { stars: { density } } })}
              />
              <RangeControl
                label="Brightness"
                value={resolveNumber(stars?.brightness, dayStars?.brightness)}
                min={0}
                max={2}
                step={0.01}
                onChange={brightness => updateOverrides({ sky: { stars: { brightness } } })}
              />
            </>
          )}
        </SettingsGroup>

        <SettingsGroup title="Fog">
          <ColorControl
            label="Color"
            value={resolveColor(fog?.color, dayFog?.color)}
            onChange={color => updateOverrides({ fog: { color } })}
          />
          <RangeControl
            label="Density"
            value={resolveNumber(fog?.density, dayFog?.density)}
            min={0}
            max={0.01}
            step={0.00001}
            decimals={5}
            onChange={density => updateOverrides({ fog: { density } })}
          />
          <RangeControl
            label="Height falloff"
            value={resolveNumber(fog?.heightFalloff, dayFog?.heightFalloff)}
            min={0}
            max={0.01}
            step={0.00001}
            decimals={5}
            onChange={heightFalloff => updateOverrides({ fog: { heightFalloff } })}
          />
        </SettingsGroup>

        <SettingsGroup title="Exposure">
          <RangeControl
            label="Exposure"
            value={resolveNumber(effectivePreset.exposure, dayPreset.exposure)}
            min={0}
            max={2}
            step={0.01}
            onChange={exposure => updateOverrides({ exposure })}
          />
        </SettingsGroup>
      </div>
    </aside>
  );
}

function SettingsGroup({ title, children }: SettingsGroupProps) {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <section className={`settings-group ${collapsed ? 'settings-section-collapsed' : ''}`}>
      <button
        type="button"
        className="settings-group-trigger"
        aria-expanded={!collapsed}
        onClick={() => setCollapsed(current => !current)}
      >
        <span>{title}</span>
        {collapsed
          ? <ChevronRight className="icon" />
          : <ChevronDown className="icon" />}
      </button>
      {!collapsed && <div className="settings-group-content">{children}</div>}
    </section>
  );
}

function RangeControl({
  label,
  value,
  min,
  max,
  step,
  decimals = 2,
  onChange,
}: RangeControlProps) {
  return (
    <label className="settings-row">
      <span>{label}</span>
      <input
        className="settings-slider"
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={event => onChange(Number(event.currentTarget.value))}
      />
      <output>{value.toFixed(decimals)}</output>
    </label>
  );
}

function ColorControl({ label, value, onChange }: ColorControlProps) {
  const hex = rgbToHex(value);

  return (
    <label className="settings-row settings-color-row">
      <span>{label}</span>
      <input
        className="settings-color"
        type="color"
        value={hex}
        onChange={event => onChange(hexToRgb(event.currentTarget.value))}
      />
      <output>{hex.toUpperCase()}</output>
    </label>
  );
}
