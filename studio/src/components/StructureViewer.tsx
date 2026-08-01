import { useEffect, useRef, useState, forwardRef, useImperativeHandle } from 'react';
import {
  NbtFile,
  Structure,
  ThreeStructureRenderer,
  type Resources,
} from '@mattzh72/lodestone';
import { OrbitCamera } from '../lodestone/OrbitCamera';
import type { SunlightPreset } from '../lodestone/lighting';
import { loadResources } from '../lodestone/pack-loader';

export type ViewerHandle = {
  getModifiedNbt: () => ArrayBuffer | null;
  getStructure: () => Structure | null;
};

export type ViewerProps = {
  nbtBuffer: ArrayBuffer | null;
  fileName: string | null;
  sunlight: SunlightPreset;
  onStatsChange: (stats: { size: [number, number, number]; blocks: number } | null) => void;
};

export const StructureViewer = forwardRef<ViewerHandle, ViewerProps>(function StructureViewer(
  { nbtBuffer, sunlight, onStatsChange },
  ref,
) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const rendererRef = useRef<ThreeStructureRenderer | null>(null);
  const cameraRef = useRef<OrbitCamera | null>(null);
  const structureRef = useRef<Structure | null>(null);
  const frameRef = useRef(0);
  const packRef = useRef<Resources | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const requestRender = useRef(() => {
    if (frameRef.current) return;
    frameRef.current = requestAnimationFrame(() => {
      frameRef.current = 0;
      const r = rendererRef.current;
      const cam = cameraRef.current;
      if (!r || !cam) return;
      const state = cam.getCamera();
      r.lookAt(state.position, state.target);
      r.drawStructure();
    });
  });

  useImperativeHandle(ref, () => ({
    getModifiedNbt: () => {
      if (!structureRef.current) return null;
      return structureRef.current.writeNbt().buffer as ArrayBuffer;
    },
    getStructure: () => structureRef.current,
  }), []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const resources = await loadResources();
        if (!cancelled) {
          packRef.current = resources;
          setLoading(false);
        }
      } catch (err) {
        if (!cancelled) {
          setError(`Failed to load resource pack: ${err}`);
          setLoading(false);
        }
      }
    })();
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (!nbtBuffer || !canvasRef.current || !packRef.current) return;

    const canvas = canvasRef.current;
    let renderer: ThreeStructureRenderer | null = null;
    let camera: OrbitCamera | null = null;

    (async () => {
      try {
        setError(null);
        const bytes = new Uint8Array(nbtBuffer);
        const nbtFile = NbtFile.read(bytes);
        const structure = Structure.fromNbt(nbtFile.root);

        structureRef.current = structure;
        const size = structure.getSize();
        onStatsChange({
          size: [size[0], size[1], size[2]],
          blocks: structure.getBlocks().length,
        });

        rendererRef.current?.dispose();
        cameraRef.current?.dispose();

        renderer = new ThreeStructureRenderer(canvas, structure, packRef.current!, {
          chunkSize: 16,
          drawDistance: 1000,
          useInvisibleBlockBuffer: false,
          asyncBuild: true,
          asyncChunkBuildTimeMs: 8,
          sunlight,
        });
        rendererRef.current = renderer;

        const center: [number, number, number] = [
          size[0] / 2,
          Math.max(1, size[1] / 2.5),
          size[2] / 2,
        ];
        const distance = Math.max(18, Math.max(size[0], size[1], size[2]) * 1.9);

        camera = new OrbitCamera(canvas, center, distance, requestRender.current);
        cameraRef.current = camera;

        const rect = canvas.getBoundingClientRect();
        if (rect.width > 0 && rect.height > 0) {
          renderer.setViewport(0, 0, rect.width, rect.height, Math.min(window.devicePixelRatio, 1.5));
        }

        await renderer.whenReady();
        requestRender.current();
      } catch (err) {
        setError(err instanceof Error ? err.message : String(err));
      }
    })();

    return () => {
      if (frameRef.current) cancelAnimationFrame(frameRef.current);
      renderer?.dispose();
      camera?.dispose();
    };
  }, [nbtBuffer, packRef.current]);

  useEffect(() => {
    if (rendererRef.current) {
      rendererRef.current.setSunlight(sunlight);
      requestRender.current();
    }
  }, [sunlight]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const observer = new ResizeObserver(() => {
      const r = rendererRef.current;
      if (!r) return;
      const rect = canvas.getBoundingClientRect();
      if (rect.width > 0 && rect.height > 0) {
        r.setViewport(0, 0, rect.width, rect.height, Math.min(window.devicePixelRatio, 1.5));
        requestRender.current();
      }
    });
    observer.observe(canvas);
    return () => observer.disconnect();
  }, []);

  useEffect(() => () => {
    rendererRef.current?.dispose();
    cameraRef.current?.dispose();
  }, []);

  return (
    <div className="viewer-container">
      <canvas ref={canvasRef} className="viewer-canvas" />
      {(loading || error) && (
        <div className="viewer-overlay">
          {loading && <div className="loader-spinner" />}
          {error && <p className="viewer-error">{error}</p>}
        </div>
      )}
      {nbtBuffer && (
        <div className="viewer-hud">
          <div className="hud-cam-hint">
            <kbd>Drag</kbd> orbit
            <kbd>Mid</kbd> pan
            <kbd>Wheel</kbd> zoom
            <kbd>WASD</kbd> move
          </div>
        </div>
      )}
    </div>
  );
});
