import { useState, useCallback, useRef, useEffect, useMemo } from 'react';
import { Button, ToggleButton, Toolbar } from 'react-aria-components';
import { invoke } from '@tauri-apps/api/core';
import { open } from '@tauri-apps/plugin-shell';
import { getCurrentWindow } from '@tauri-apps/api/window';
import {
  Hexagon,
  Hammer,
  ShieldCheck,
  Sun,
  Moon,
  Save,
  Check,
  ExternalLink,
  Box,
  Minus,
  Square,
  Copy,
  Settings2,
  X,
} from 'lucide-react';
import { StructureViewer, type ViewerHandle } from './components/StructureViewer';
import {
  ViewerSettingsPanel,
  loadViewerSettings,
  persistViewerSettings,
  resolveEffectivePreset,
  type ViewerSettingsMode,
} from './components/ViewerSettingsPanel';
import { Catalog } from './components/Catalog';
import { CheckerPanel } from './components/CheckerPanel';
import { BuildDialog } from './components/BuildDialog';
import { getSkinDataUrl } from './catalog/api';
import type { FullReport } from './engine';
import './index.css';

const isMac = navigator.platform.toUpperCase().includes('MAC');

type Stats = { size: [number, number, number]; blocks: number };

function App() {
  const [nbtBuffer, setNbtBuffer] = useState<ArrayBuffer | null>(null);
  const [fileName, setFileName] = useState<string | null>(null);
  const [, setFilePath] = useState<string | null>(null);
  const [viewerSettings, setViewerSettings] = useState(loadViewerSettings);
  const [stats, setStats] = useState<Stats | null>(null);
  const [selectedSkin, setSelectedSkin] = useState<string | null>(null);
  const [skinUrl, setSkinUrl] = useState<string | null>(null);
  const [checkReport, setCheckReport] = useState<FullReport | null>(null);
  const [showChecks, setShowChecks] = useState(false);
  const [showViewerSettings, setShowViewerSettings] = useState(false);
  const [showBuildDialog, setShowBuildDialog] = useState(false);
  const viewerRef = useRef<ViewerHandle>(null);
  const effectivePreset = useMemo(
    () => resolveEffectivePreset(viewerSettings),
    [viewerSettings],
  );

  const loadStructure = useCallback((buffer: ArrayBuffer, name: string, _path: string) => {
    setNbtBuffer(buffer);
    setFileName(name);
    setShowChecks(false);
    setCheckReport(null);
  }, []);

  const handleLightingChange = useCallback((mode: 'day' | 'night') => {
    setViewerSettings(current => ({
      ...current,
      mode,
      overrides: {},
    }));
  }, []);

  const handleCheck = useCallback(() => {
    const structure = viewerRef.current?.getStructure();
    if (!structure) return;
    import('./engine').then(({ runChecksOnStructure }) => {
      const report = runChecksOnStructure(structure);
      setCheckReport(report);
      setShowChecks(true);
    });
  }, []);

  const handleSave = useCallback(async () => {
    const nbt = viewerRef.current?.getModifiedNbt();
    if (!nbt || !fileName) return;
    try {
      const data = Array.from(new Uint8Array(nbt));
      const name = fileName.endsWith('.nbt') ? fileName : `${fileName}.nbt`;
      await invoke('save_nbt_as', { defaultName: name, data });
    } catch (err) {
      console.error('Save failed:', err);
    }
  }, [fileName]);

  useEffect(() => {
    persistViewerSettings(viewerSettings);
  }, [viewerSettings]);

  useEffect(() => {
    if (selectedSkin) {
      getSkinDataUrl(selectedSkin).then(setSkinUrl);
    } else {
      setSkinUrl(null);
    }
  }, [selectedSkin]);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setShowChecks(false);
        setShowViewerSettings(false);
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, []);

  return (
    <div className="app">
      <Header
        hasStructure={!!nbtBuffer}
        lighting={viewerSettings.mode}
        showViewerSettings={showViewerSettings}
        onLightingChange={handleLightingChange}
        onViewerSettingsChange={setShowViewerSettings}
        onCheck={handleCheck}
        onSave={handleSave}
        onBuild={() => setShowBuildDialog(true)}
      />

      <main className="app-main">
        <aside className="sidebar-left">
          <Catalog
            onLoadStructure={loadStructure}
            selectedSkin={selectedSkin}
            onSelectSkin={setSelectedSkin}
          />

          {stats && (
            <div className="info-panel">
              <h3>Structure</h3>
              <dl>
                <dt>Size</dt>
                <dd>{stats.size[0]} × {stats.size[1]} × {stats.size[2]}</dd>
                <dt>Blocks</dt>
                <dd>{stats.blocks.toLocaleString()}</dd>
                {fileName && (<><dt>File</dt><dd className="file-path">{fileName}</dd></>)}
              </dl>
            </div>
          )}
        </aside>

        <div className="viewer-area">
          {nbtBuffer ? (
            <StructureViewer
              ref={viewerRef}
              nbtBuffer={nbtBuffer}
              fileName={fileName}
              sunlight={effectivePreset}
              onStatsChange={setStats}
            />
          ) : (
            <EmptyState />
          )}

          {showViewerSettings && (
            <ViewerSettingsPanel
              settings={viewerSettings}
              effectivePreset={effectivePreset}
              onSettingsChange={setViewerSettings}
              onClose={() => setShowViewerSettings(false)}
            />
          )}
        </div>

        {showChecks && (
          <CheckerPanel report={checkReport} onClose={() => setShowChecks(false)} />
        )}

        {showBuildDialog && (
          <BuildDialog
            onClose={() => setShowBuildDialog(false)}
            onResult={async ({ structure, report, nbt, name }) => {
              setCheckReport(report);
              setShowChecks(true);
              const buf = nbt.buffer.slice(nbt.byteOffset, nbt.byteOffset + nbt.byteLength) as ArrayBuffer;
              setNbtBuffer(buf);
              setFileName(`${name}.nbt`);
              setFilePath(null);
              void structure;
            }}
          />
        )}

        {selectedSkin && (
          <aside className="skin-panel">
            <div className="skin-panel-header">
              <h3><Box className="icon" />Skin</h3>
              <Button
                className="skin-close"
                onPress={() => setSelectedSkin(null)}
                aria-label="Close skin preview"
              >
                <ExternalLink className="icon" />
              </Button>
            </div>
            {skinUrl && <img className="skin-preview-img" src={skinUrl} alt={selectedSkin} />}
            <p className="skin-preview-name">{selectedSkin}</p>
          </aside>
        )}
      </main>
    </div>
  );
}

function Header({
  hasStructure,
  lighting,
  showViewerSettings,
  onLightingChange,
  onViewerSettingsChange,
  onCheck,
  onSave,
  onBuild,
}: {
  hasStructure: boolean;
  lighting: ViewerSettingsMode;
  showViewerSettings: boolean;
  onLightingChange: (mode: 'day' | 'night') => void;
  onViewerSettingsChange: (show: boolean) => void;
  onCheck: () => void;
  onSave: () => void;
  onBuild: () => void;
}) {
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [isMaximized, setIsMaximized] = useState(false);
  const appWindow = getCurrentWindow();

  useEffect(() => {
    let unlisten: (() => void) | undefined;
    appWindow.isMaximized().then(setIsMaximized).catch(() => {});
    appWindow.onResized(({ payload }) => {
      setIsMaximized(Boolean((payload as { maximized?: boolean }).maximized));
    }).then(fn => { unlisten = fn; }).catch(() => {});
    return () => { unlisten?.(); };
  }, [appWindow]);

  const handleSaveClick = async () => {
    setSaving(true);
    await onSave();
    setSaving(false);
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  };

  const openInBlockbench = () => {
    open('https://blockbench.net/').catch(() => {
      window.open('https://blockbench.net/features?id=nbt_structure_editor', '_blank');
    });
  };

  return (
    <header className="app-header">
      <div className="header-drag-region">
        <div className="header-brand">
          <span className="brand-mark"><Hexagon size={18} strokeWidth={2} /></span>
          <h1>Burg Studio<span className="brand-sub">v0.1</span></h1>
        </div>

        <Toolbar className="header-actions">
          {hasStructure && (
            <>
              <Button className="header-btn" onPress={onBuild}>
                <Hammer className="icon" />
                <span>Build</span>
              </Button>

              <ToggleButton
                className="header-btn"
                isSelected={lighting === 'night'}
                onChange={() => onLightingChange(lighting === 'night' ? 'day' : 'night')}
                aria-label={lighting === 'night' ? 'Switch to day lighting' : 'Switch to night lighting'}
              >
                {lighting === 'night' ? <Moon className="icon" /> : <Sun className="icon" />}
              </ToggleButton>

              <Button className="header-btn" onPress={onCheck}>
                <ShieldCheck className="icon" />
                <span>Check</span>
              </Button>

              <Button className="header-btn ghost" onPress={openInBlockbench}>
                <span>Blockbench</span>
                <ExternalLink className="icon-sm" />
              </Button>
            </>
          )}

          <ToggleButton
            className="header-btn"
            isSelected={showViewerSettings}
            onChange={onViewerSettingsChange}
            aria-label={showViewerSettings ? 'Close viewer settings' : 'Open viewer settings'}
          >
            <Settings2 className="icon" />
          </ToggleButton>

          {hasStructure && (
            <Button
              className={`header-btn primary ${saved ? 'saved' : ''}`}
              onPress={handleSaveClick}
              isDisabled={saving}
            >
              {saving ? <Save className="icon" /> : saved ? <Check className="icon" /> : <Save className="icon" />}
              <span>{saving ? 'Saving…' : saved ? 'Saved' : 'Save As'}</span>
            </Button>
          )}
        </Toolbar>
      </div>

      {!isMac && (
        <div className="window-controls">
          <button
            className="win-btn"
            onClick={() => appWindow.minimize()}
            aria-label="Minimize"
            tabIndex={-1}
          >
            <Minus size={12} strokeWidth={2} />
          </button>
          <button
            className="win-btn"
            onClick={() => appWindow.toggleMaximize()}
            aria-label={isMaximized ? 'Restore' : 'Maximize'}
            tabIndex={-1}
          >
            {isMaximized ? <Copy size={11} strokeWidth={2} /> : <Square size={11} strokeWidth={2} />}
          </button>
          <button
            className="win-btn close"
            onClick={() => appWindow.close()}
            aria-label="Close"
            tabIndex={-1}
          >
            <X size={13} strokeWidth={2} />
          </button>
        </div>
      )}
    </header>
  );
}

function EmptyState() {
  return (
    <div className="empty-state">
      <div className="empty-icon"><Hexagon size={56} strokeWidth={1.25} /></div>
      <h2>No structure loaded</h2>
      <p>Pick one from the catalog, or drag a <code>.nbt</code> file here.</p>
      <p className="empty-hint">
        Structures live in <code>common/src/main/resources/data/onceuponatown/structure/</code>
      </p>
    </div>
  );
}

export default App;
