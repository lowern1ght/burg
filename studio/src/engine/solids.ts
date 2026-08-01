/**
 * Shape predicates — ported from tools/structures/solids.py.
 *
 * One source of truth for "what volume does a block occupy", derived from
 * appearance.shapeOf(). Every geometric bug in this repo's history came from
 * the gap between "the block exists" and "the block fills its cell".
 */

import { type BlockInfo, shapeOf } from './appearance';

const PLATE_H = 0.14;
const FLAT_H = 0.09;

export function fillsCell(b: BlockInfo | null): boolean {
  if (b === null) return false;
  return shapeOf(b)[0] === 'full';
}

export function topFace(b: BlockInfo | null): number | null {
  if (b === null) return null;
  const [kind, param] = shapeOf(b);
  switch (kind) {
    case 'full': return 1.0;
    case 'slab': return param === 'top' ? 1.0 : 0.5;
    case 'stairs': return 1.0;
    case 'plate': return param === 'top' ? 1.0 : PLATE_H;
    case 'flat': return FLAT_H;
    case 'post':
    case 'door': return 1.0;
    default: return null;
  }
}

export function carriesAbove(b: BlockInfo | null): boolean {
  const t = topFace(b);
  return t !== null && t >= 1.0;
}

export function halfStep(b: BlockInfo | null): boolean {
  if (b === null) return false;
  const [kind, param] = shapeOf(b);
  return kind === 'slab' && param !== 'top' && b.id.endsWith('_slab');
}

export function fullFootprint(b: BlockInfo | null): boolean {
  if (b === null) return false;
  const [kind, param] = shapeOf(b);
  if (kind === 'full') return true;
  if (kind === 'slab' || kind === 'plate') return param === 'top';
  return false;
}

export function sideAttached(b: BlockInfo | null): boolean {
  if (b === null) return false;
  const n = b.id;
  return (
    n.endsWith('_trapdoor') || n.endsWith('_wall_sign') ||
    n.endsWith('_wall_banner') || n.endsWith('_torch') ||
    ['ladder', 'wall_torch', 'lantern', 'chain', 'tripwire_hook',
     'lily_pad', 'vine', 'short_grass', 'grass', 'flower_pot'].includes(n)
  );
}

export function isRail(b: BlockInfo | null): boolean {
  if (b === null) return false;
  return ['_fence', '_fence_gate', '_wall', '_pane', '_bars'].some(s => b.id.endsWith(s));
}

export function isRoofMaterial(b: BlockInfo | null): boolean {
  if (b === null) return false;
  return b.id.endsWith('_slab') || b.id.endsWith('_stairs');
}

export function perchTop(b: BlockInfo | null): number | null {
  if (b === null) return null;
  if (isRail(b)) return 1.5;
  return topFace(b);
}
