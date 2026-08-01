import { invoke } from '@tauri-apps/api/core';

export type TreeNode = {
  name: string;
  path: string;
  type: 'dir' | 'file';
  size?: number;
  children?: TreeNode[];
};

export type SkinEntry = {
  name: string;
  path: string;
  category: string;
  size: number;
};

export async function fetchStructureTree(): Promise<TreeNode> {
  return invoke<TreeNode>('list_structures');
}

export async function fetchSkinList(): Promise<SkinEntry[]> {
  return invoke<SkinEntry[]>('list_skins');
}

export async function fetchStructureNbt(relPath: string): Promise<ArrayBuffer> {
  const bytes = await invoke<number[]>('read_nbt', { path: relPath });
  return new Uint8Array(bytes).buffer;
}

export async function saveStructureNbt(relPath: string, data: ArrayBuffer): Promise<void> {
  const bytes = Array.from(new Uint8Array(data));
  await invoke<void>('write_nbt', { path: relPath, data: bytes });
}

export async function fetchPackFile(file: string): Promise<ArrayBuffer> {
  const bytes = await invoke<number[]>('read_pack_file', { file });
  return new Uint8Array(bytes).buffer;
}

export async function fetchNonSelfCulling(): Promise<string> {
  return invoke<string>('read_non_self_culling');
}

/** Load the Lodestone default pack, patched, returning Resources ready for the renderer. */
export async function loadPackedResources(): Promise<{
  assetsJson: string;
  atlasPng: ArrayBuffer;
  nonSelfCulling: string;
  opaque: string;
  transparent: string;
  emissive: string;
}> {
  const [assetsJsonBytes, atlasPng, nonSelfCulling, opaque, transparent, emissive] = await Promise.all([
    fetchPackFile('assets.json'),
    fetchPackFile('atlas.png'),
    fetchNonSelfCulling(),
    fetchPackFile('block-flags/opaque.txt'),
    fetchPackFile('block-flags/transparent.txt'),
    fetchPackFile('block-flags/emissive.json'),
  ]);
  return {
    assetsJson: new TextDecoder().decode(new Uint8Array(assetsJsonBytes)),
    atlasPng,
    nonSelfCulling,
    opaque: new TextDecoder().decode(new Uint8Array(opaque)),
    transparent: new TextDecoder().decode(new Uint8Array(transparent)),
    emissive: new TextDecoder().decode(new Uint8Array(emissive)),
  };
}

/** Convert skin path to a data URL for <img> display (since we read via IPC, not HTTP). */
let skinCache = new Map<string, string>();

export async function getSkinDataUrl(path: string): Promise<string> {
  if (skinCache.has(path)) return skinCache.get(path)!;
  const bytes = await invoke<number[]>('read_skin', { path });
  const blob = new Blob([new Uint8Array(bytes)], { type: 'image/png' });
  const url = URL.createObjectURL(blob);
  skinCache.set(path, url);
  return url;
}

export function clearSkinCache() {
  skinCache.forEach(url => URL.revokeObjectURL(url));
  skinCache = new Map();
}
