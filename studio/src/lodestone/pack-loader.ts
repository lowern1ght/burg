import { invoke } from '@tauri-apps/api/core';
import { createResourcesFromPack } from '@mattzh72/lodestone';
import type { Resources } from '@mattzh72/lodestone';

/** Returns the smallest power of two >= n. */
function upperPowerOfTwo(n: number): number {
  return Math.pow(2, Math.ceil(Math.log2(n)));
}

/**
 * Load the Lodestone default pack via Tauri IPC (not HTTP), apply the
 * non-self-culling patch, and return ready-to-use Resources for the renderer.
 *
 * In dev, the pack lives in studio/node_modules/@mattzh72/lodestone/assets/default-pack/.
 * The Rust backend reads it via `read_pack_file` / `read_non_self_culling`.
 */

async function parseFlags(text: string): Promise<Set<string>> {
  const set = new Set<string>();
  for (const line of text.split('\n')) {
    const trimmed = line.trim();
    if (trimmed && !trimmed.startsWith('#')) set.add(trimmed);
  }
  return set;
}

async function parseEmissive(json: string): Promise<Record<string, { intensity?: number; conditional?: string }>> {
  try {
    return JSON.parse(json);
  } catch {
    return {};
  }
}

export async function loadResources(): Promise<Resources> {
  // Fetch all pack files via Tauri IPC.
  const [
    assetsJsonBytes, atlasPngBytes,
    nonSelfCullingText, opaqueText, transparentText, emissiveJsonText,
  ] = await Promise.all([
    invoke<number[]>('read_pack_file', { file: 'assets.json' }),
    invoke<number[]>('read_pack_file', { file: 'atlas.png' }),
    invoke<string>('read_non_self_culling'),
    invoke<number[]>('read_pack_file', { file: 'block-flags/opaque.txt' }),
    invoke<number[]>('read_pack_file', { file: 'block-flags/transparent.txt' }),
    invoke<number[]>('read_pack_file', { file: 'block-flags/emissive.json' }),
  ]);

  // Parse assets.json.
  const assetsJson = new TextDecoder().decode(new Uint8Array(assetsJsonBytes));
  const assets = JSON.parse(assetsJson);

  // Decode atlas.png → ImageData via a hidden canvas.
  // Lodestone pads to a square power-of-two (upperPowerOfTwo of max dimension).
  const atlasBlob = new Blob([new Uint8Array(atlasPngBytes)], { type: 'image/png' });
  const atlasBitmap = await createImageBitmap(atlasBlob);
  const atlasSize = upperPowerOfTwo(Math.max(atlasBitmap.width, atlasBitmap.height));
  const canvas = document.createElement('canvas');
  canvas.width = atlasSize;
  canvas.height = atlasSize;
  const ctx = canvas.getContext('2d')!;
  ctx.drawImage(atlasBitmap, 0, 0);
  const imageData = ctx.getImageData(0, 0, atlasSize, atlasSize);
  atlasBitmap.close();

  // Parse block flags.
  const opaqueTextStr = new TextDecoder().decode(new Uint8Array(opaqueText));
  const transparentTextStr = new TextDecoder().decode(new Uint8Array(transparentText));
  const emissiveJsonStr = new TextDecoder().decode(new Uint8Array(emissiveJsonText));

  const opaque = await parseFlags(opaqueTextStr);
  const transparent = await parseFlags(transparentTextStr);
  const nonSelfCulling = await parseFlags(nonSelfCullingText);
  const emissive = await parseEmissive(emissiveJsonStr);

  return createResourcesFromPack({
    assets: {
      blockstates: assets.blockstates ?? {},
      models: assets.models ?? {},
      textures: assets.textures ?? {},
    },
    atlas: {
      imageData,
      atlasSize,
    },
    flags: {
      opaque,
      transparent,
      nonSelfCulling,
      emissive,
    },
  });
}
