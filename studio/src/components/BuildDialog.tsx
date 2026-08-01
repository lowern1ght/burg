import { useState, useEffect } from 'react';
import { Button, TextArea, Label, Dialog, Modal, ModalOverlay } from 'react-aria-components';
import { Hammer, X } from 'lucide-react';
import type { FullReport } from '../engine';
import type { Structure } from '@mattzh72/lodestone';

type Props = {
  onResult: (result: { structure: Structure; report: FullReport; nbt: Uint8Array; name: string }) => void;
  onClose: () => void;
};

const STARTER_YAML = `name: "Watchtower (starter)"
version: 1

structure:
  footprint: [5, 5]
  height: 4
  origin: [0, 0, 0]

floors:
  - range: [0, 3]
    material: "oak_planks"
    layout: "residential"

devices:
  - kind: door
    side: south
    floor: 1
  - kind: torch
    pos: [2, 2, 2]

rules:
  guard:
    - no_floating_roof
    - no_slab_rider

output:
  name: "watchtower_starter"
  path: "military/watchtower/"
`;

export function BuildDialog({ onResult, onClose }: Props) {
  const [yaml, setYaml] = useState(STARTER_YAML);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [progress, setProgress] = useState<string | null>(null);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !busy) onClose();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [busy, onClose]);

  const handleBuild = async () => {
    setError(null);
    setProgress('Parsing YAML…');
    setBusy(true);
    try {
      const dsl = await import('../dsl');
      const plan = dsl.loadPlan(yaml);
      setProgress('Validating…');
      const validation = dsl.validatePlan(plan);
      if (!validation.ok) {
        const msgs = validation.errors.map(e => `  - [${e.rule}] ${e.message}`).join('\n');
        throw new Error(`Validation failed:\n${msgs}`);
      }
      setProgress('Generating structure…');
      const result = dsl.generateStructure(plan);
      setProgress('Running checker pipeline…');
      const { runChecksOnStructure } = await import('../engine');
      const report = runChecksOnStructure(result.structure);
      const nbt = result.structure.writeNbt();
      const name = plan.output?.name ?? 'built_structure';
      onResult({ structure: result.structure, report, nbt, name });
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
      setProgress(null);
    } finally {
      setBusy(false);
    }
  };

  return (
    <ModalOverlay isOpen className="dialog-overlay">
      <Modal className="dialog">
        <Dialog className="dialog-inner">
          <header className="dialog-header">
            <h2><Hammer className="icon" />Build from YAML spec</h2>
            <Button className="dialog-close" onPress={onClose} isDisabled={busy} aria-label="Close">
              <X className="icon" />
            </Button>
          </header>

          <div className="dialog-body">
            <Label className="dialog-label">Plan YAML</Label>
            <TextArea
              className="dialog-textarea"
              value={yaml}
              onChange={e => setYaml(e.target.value)}
              disabled={busy}
              rows={20}
            />

            {progress && (
              <div className="dialog-progress">{progress}</div>
            )}
            {error && (
              <pre className="dialog-error">{error}</pre>
            )}
          </div>

          <footer className="dialog-footer">
            <Button className="header-btn ghost" onPress={onClose} isDisabled={busy}>
              Cancel
            </Button>
            <Button
              className="header-btn primary"
              onPress={handleBuild}
              isDisabled={busy}
            >
              {busy ? 'Building…' : 'Build'}
            </Button>
          </footer>
        </Dialog>
      </Modal>
    </ModalOverlay>
  );
}
