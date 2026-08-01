import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'node:path';
import fs from 'node:fs';
import { createReadStream } from 'node:fs';

const defaultPackRoot = path.resolve(
  __dirname,
  'node_modules',
  '@mattzh72',
  'lodestone',
  'assets',
  'default-pack',
);

const repoRoot = path.resolve(__dirname, '..');
const structuresRoot = path.join(
  repoRoot, 'common', 'src', 'main', 'resources', 'data', 'onceuponatown', 'structure',
);
const skinsRoot = path.join(
  repoRoot, 'common', 'src', 'main', 'resources', 'assets', 'onceuponatown', 'textures', 'entity', 'npc',
);

const NON_CUBE_SUFFIXES = [
  '_slab', '_stairs', '_fence', '_fence_gate', '_wall', '_pane', '_trapdoor', '_door',
  '_bars', '_carpet', '_bed', '_sign', '_button', '_pressure_plate', '_rod', '_chain',
  '_lantern', '_torch', '_ladder', '_candle', '_grate',
];
const NON_CUBE_EXACT = ['ladder', 'iron_bars', 'lantern', 'chain', 'glass_pane', 'scaffolding'];

async function extraNonSelfCulling(packRoot: string): Promise<string> {
  const index = JSON.parse(
    await fs.promises.readFile(path.join(packRoot, 'assets.json'), 'utf8'),
  );
  const ids = new Set<string>();
  for (const key of Object.keys(index.blockstates ?? {})) {
    if (NON_CUBE_EXACT.includes(key) ||
        NON_CUBE_SUFFIXES.some(suffix => key.endsWith(suffix))) {
      ids.add(`minecraft:${key}`);
    }
  }
  return [...ids].join('\n');
}

function contentType(filePath: string): string {
  if (filePath.endsWith('.json')) return 'application/json';
  if (filePath.endsWith('.png')) return 'image/png';
  if (filePath.endsWith('.txt')) return 'text/plain; charset=utf-8';
  return 'application/octet-stream';
}

function lodestonePackPlugin() {
  return {
    name: 'lodestone-default-pack',
    configureServer(server: any) {
      server.middlewares.use('/default-pack/', async (req: any, res: any) => {
        try {
          const requestUrl = new URL(req.url ?? '/', 'http://localhost');
          const relativePath = decodeURIComponent(requestUrl.pathname.replace(/^\/+/, ''));
          const filePath = path.resolve(defaultPackRoot, relativePath);
          const allowedRoot = `${defaultPackRoot}${path.sep}`;
          if (filePath !== defaultPackRoot && !filePath.startsWith(allowedRoot)) {
            res.statusCode = 403;
            res.end('Forbidden');
            return;
          }
          await fs.promises.access(filePath);
          if (filePath.endsWith('non_self_culling.txt')) {
            const original = await fs.promises.readFile(filePath, 'utf8');
            const extra = await extraNonSelfCulling(defaultPackRoot);
            res.statusCode = 200;
            res.setHeader('Content-Type', 'text/plain; charset=utf-8');
            res.end(original.trimEnd() + '\n' + extra + '\n');
            return;
          }
          res.statusCode = 200;
          res.setHeader('Content-Type', contentType(filePath));
          createReadStream(filePath).pipe(res);
        } catch {
          res.statusCode = 404;
          res.end('Not found');
        }
      });
    },
  };
}

// ---- catalog: scan structure + skin folders, serve individual files ----

type TreeNode = {
  name: string;
  path: string;
  type: 'dir' | 'file';
  size?: number;
  children?: TreeNode[];
};

async function scanDir(dirPath: string, relPath: string): Promise<TreeNode> {
  const name = path.basename(dirPath);
  const node: TreeNode = { name, path: relPath, type: 'dir', children: [] };
  const entries = await fs.promises.readdir(dirPath, { withFileTypes: true });
  for (const entry of entries.sort((a, b) => {
    if (a.isDirectory() !== b.isDirectory()) return a.isDirectory() ? -1 : 1;
    return a.name.localeCompare(b.name, undefined, { numeric: true });
  })) {
    const childRel = relPath ? `${relPath}/${entry.name}` : entry.name;
    if (entry.isDirectory()) {
      node.children!.push(await scanDir(path.join(dirPath, entry.name), childRel));
    } else if (entry.name.endsWith('.nbt')) {
      const stat = await fs.promises.stat(path.join(dirPath, entry.name));
      node.children!.push({ name: entry.name, path: childRel, type: 'file', size: stat.size });
    }
  }
  return node;
}

function sendJson(res: any, data: unknown) {
  res.statusCode = 200;
  res.setHeader('Content-Type', 'application/json');
  res.end(JSON.stringify(data));
}

function catalogPlugin() {
  return {
    name: 'burg-catalog',
    configureServer(server: any) {
      // Structure tree: GET /catalog/structures → JSON tree
      server.middlewares.use('/catalog/structures', async (_req: any, res: any) => {
        try {
          const tree = await scanDir(structuresRoot, '');
          sendJson(res, tree);
        } catch (err) {
          res.statusCode = 500;
          res.end(String(err));
        }
      });

      // Skin list: GET /catalog/skins → JSON array
      server.middlewares.use('/catalog/skins', async (_req: any, res: any) => {
        try {
          const entries = await fs.promises.readdir(skinsRoot, { withFileTypes: true });
          const files = entries
            .filter(e => e.isFile() && e.name.endsWith('.png'))
            .sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }));
          const items = files.map(f => {
            const stat = fs.statSync(path.join(skinsRoot, f.name));
            let category = 'misc';
            if (f.name.startsWith('citizen_body')) category = 'body';
            else if (f.name.startsWith('citizen_hair')) category = 'hair';
            else if (f.name.startsWith('citizen_beard')) category = 'beard';
            else if (f.name.startsWith('citizen_headwear')) category = 'headwear';
            else if (f.name.endsWith('_clothes.png')) category = 'clothes';
            else if (f.name === 'citizen_trim.png') category = 'trim';
            else if (f.name === 'default_skin.png') category = 'reference';
            return { name: f.name, path: f.name, category, size: stat.size };
          });
          sendJson(res, items);
        } catch (err) {
          res.statusCode = 500;
          res.end(String(err));
        }
      });

      // Serve NBT file by relative path: GET /file/structure?path=military/watchtower/watchtower.nbt
      server.middlewares.use('/file/structure', async (req: any, res: any) => {
        try {
          const requestUrl = new URL(req.url ?? '/', 'http://localhost');
          const relPath = requestUrl.searchParams.get('path');
          if (!relPath) { res.statusCode = 400; res.end('Missing path'); return; }
          const filePath = path.resolve(structuresRoot, relPath);
          if (!filePath.startsWith(structuresRoot + path.sep) && filePath !== structuresRoot) {
            res.statusCode = 403; res.end('Forbidden'); return;
          }
          await fs.promises.access(filePath);
          res.statusCode = 200;
          res.setHeader('Content-Type', 'application/octet-stream');
          createReadStream(filePath).pipe(res);
        } catch {
          res.statusCode = 404; res.end('Not found');
        }
      });

      // Serve skin PNG by relative path: GET /file/skin?path=citizen_body_00.png
      server.middlewares.use('/file/skin', async (req: any, res: any) => {
        try {
          const requestUrl = new URL(req.url ?? '/', 'http://localhost');
          const relPath = requestUrl.searchParams.get('path');
          if (!relPath) { res.statusCode = 400; res.end('Missing path'); return; }
          const filePath = path.resolve(skinsRoot, relPath);
          if (!filePath.startsWith(skinsRoot + path.sep) && filePath !== skinsRoot) {
            res.statusCode = 403; res.end('Forbidden'); return;
          }
          await fs.promises.access(filePath);
          res.statusCode = 200;
          res.setHeader('Content-Type', 'image/png');
          createReadStream(filePath).pipe(res);
        } catch {
          res.statusCode = 404; res.end('Not found');
        }
      });

      // Block palette: GET /catalog/blocks → JSON of common buildable blocks
      server.middlewares.use('/catalog/blocks', async (_req: any, res: any) => {
        try {
          const assets = JSON.parse(
            await fs.promises.readFile(path.join(defaultPackRoot, 'assets.json'), 'utf8'),
          );
          const blockIds = Object.keys(assets.blockstates ?? {}).sort();
          const items = blockIds
            .filter(id => !id.startsWith('minecraft:'))
            .map(id => ({ id, name: id.replace(/_/g, ' ') }))
            .concat(
              blockIds
                .filter(id => id.startsWith('minecraft:'))
                .map(id => ({ id, name: id.replace('minecraft:', '').replace(/_/g, ' ') })),
            );
          sendJson(res, items);
        } catch (err) {
          res.statusCode = 500;
          res.end(String(err));
        }
      });
    },
  };
}

export default defineConfig({
  plugins: [react(), lodestonePackPlugin(), catalogPlugin()],
  server: {
    port: 8921,
    strictPort: true,
  },
  test: {
    environment: 'node',
    include: ['src/**/*.{test,spec}.ts?(x)'],
  },
});
