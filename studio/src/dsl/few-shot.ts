import fewShotData from '../../docs/few-shot.json';

export interface FewShotTopBlock {
  id: string;
  count: number;
}

export interface FewShotExample {
  family: string;
  rung: string;
  source: string;
  footprint: [number, number];
  height: number;
  topBlocks: FewShotTopBlock[];
  materials: string[];
  devices: string[];
  rulesApplied: string[];
  evolutionFrom?: string;
  notes: string[];
}

export interface FewShotDataset {
  examples: FewShotExample[];
  coverage: string[];
}

export const FEW_SHOT: FewShotDataset = fewShotData as FewShotDataset;