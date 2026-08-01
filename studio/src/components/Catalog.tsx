import { useState, useEffect, useCallback } from 'react';
import { FolderTree, Folder, FileBox, Shirt, ChevronRight } from 'lucide-react';
import { fetchStructureTree, fetchSkinList, fetchStructureNbt, getSkinDataUrl, type TreeNode, type SkinEntry } from '../catalog/api';

type Tab = 'structures' | 'skins';

type CatalogProps = {
  onLoadStructure: (buffer: ArrayBuffer, fileName: string, filePath: string) => void;
  selectedSkin: string | null;
  onSelectSkin: (path: string | null) => void;
};

export function Catalog({ onLoadStructure, selectedSkin, onSelectSkin }: CatalogProps) {
  const [tab, setTab] = useState<Tab>('structures');
  const [tree, setTree] = useState<TreeNode | null>(null);
  const [skins, setSkins] = useState<SkinEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [skinFilter, setSkinFilter] = useState<string>('all');
  const [search, setSearch] = useState('');

  useEffect(() => {
    if (tab === 'structures' && !tree) {
      setLoading(true);
      fetchStructureTree().then(t => { setTree(t); setError(null); })
        .catch(e => setError(e.message))
        .finally(() => setLoading(false));
    }
    if (tab === 'skins' && skins.length === 0) {
      setLoading(true);
      fetchSkinList().then(s => { setSkins(s); setError(null); })
        .catch(e => setError(e.message))
        .finally(() => setLoading(false));
    }
  }, [tab]);

  const handleStructureClick = useCallback(async (node: TreeNode) => {
    if (node.type !== 'file') return;
    try {
      setError(null);
      const buf = await fetchStructureNbt(node.path);
      onLoadStructure(buf, node.name, node.path);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  }, [onLoadStructure]);

  const categories = ['all', ...new Set(skins.map(s => s.category))];
  const filteredSkins = skins.filter(s => {
    if (skinFilter !== 'all' && s.category !== skinFilter) return false;
    if (search && !s.name.toLowerCase().includes(search.toLowerCase())) return false;
    return true;
  });

  return (
    <div className="catalog">
      <div className="catalog-tabs">
        <button
          className={tab === 'structures' ? 'active' : ''}
          onClick={() => setTab('structures')}
        >
          <FolderTree className="icon" />
          Structures
        </button>
        <button
          className={tab === 'skins' ? 'active' : ''}
          onClick={() => setTab('skins')}
        >
          <Shirt className="icon" />
          Skins
        </button>
      </div>

      {error && <div className="catalog-error">{error}</div>}

      {tab === 'structures' && (
        <div className="catalog-content">
          {loading && <p className="catalog-loading">Scanning…</p>}
          {tree && (
            <StructureTree node={tree} depth={0} search={search} onFileClick={handleStructureClick} />
          )}
        </div>
      )}

      {tab === 'skins' && (
        <div className="catalog-content">
          <select
            className="skin-filter"
            value={skinFilter}
            onChange={e => setSkinFilter(e.target.value)}
          >
            {categories.map(c => <option key={c} value={c}>{c}</option>)}
          </select>
          <input
            className="skin-search"
            type="text"
            placeholder="Search…"
            aria-label="Search structures"
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
          {loading && <p className="catalog-loading">Scanning…</p>}
          <div className="skin-grid">
            {filteredSkins.map(s => (
              <SkinThumb
                key={s.path}
                entry={s}
                selected={selectedSkin === s.path}
                onSelect={onSelectSkin}
              />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function SkinThumb({ entry, selected, onSelect }: {
  entry: SkinEntry;
  selected: boolean;
  onSelect: (path: string) => void;
}) {
  const [url, setUrl] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    getSkinDataUrl(entry.path).then(u => { if (!cancelled) setUrl(u); });
    return () => { cancelled = true; };
  }, [entry.path]);

  return (
    <button
      className={`skin-thumb ${selected ? 'selected' : ''}`}
      onClick={() => onSelect(entry.path)}
      title={entry.name}
    >
      {url && <img src={url} alt={entry.name} loading="lazy" />}
      <span className="skin-name">{entry.name.replace('.png', '')}</span>
    </button>
  );
}

function StructureTree({
  node, depth, search, onFileClick,
}: {
  node: TreeNode;
  depth: number;
  search: string;
  onFileClick: (node: TreeNode) => void;
}) {
  const [collapsed, setCollapsed] = useState(false);

  if (node.type === 'file') {
    if (search && !node.name.toLowerCase().includes(search.toLowerCase())) return null;
    return (
      <button
        className="tree-file"
        style={{ paddingLeft: `${depth * 16 + 8}px` }}
        onClick={() => onFileClick(node)}
        title={`${node.path} (${formatSize(node.size)})`}
      >
        <FileBox className="tree-icon" />
        <span className="tree-label">{node.name}</span>
        {node.size && <span className="tree-size">{formatSize(node.size)}</span>}
      </button>
    );
  }

  const children = node.children ?? [];
  const visibleChildren = search
    ? children.filter(c => matchesSearch(c, search))
    : children;
  if (search && visibleChildren.length === 0 && node.type === 'dir') return null;

  const hasVisibleChildren = visibleChildren.length > 0;

  // Root node (depth 0) — no folder header, just render children directly.
  if (depth === 0) {
    return (
      <div className="tree-root">
        {visibleChildren.map(child => (
          <StructureTree
            key={child.path}
            node={child}
            depth={depth + 1}
            search={search}
            onFileClick={onFileClick}
          />
        ))}
      </div>
    );
  }

  return (
    <div className={`tree-block ${collapsed ? 'collapsed' : 'open'}`}>
      <button
        className="tree-folder"
        style={{ paddingLeft: `${depth * 16 + 4}px` }}
        onClick={() => setCollapsed(!collapsed)}
      >
        <ChevronRight className={`tree-arrow ${collapsed ? 'collapsed' : ''}`} />
        <Folder className="tree-icon" />
        <span className="tree-label">{node.name}</span>
        <span className="tree-count">{countFiles(node)}</span>
      </button>
      {hasVisibleChildren && !collapsed && (
        <div className="tree-children">
          {visibleChildren.map(child => (
            <StructureTree
              key={child.path}
              node={child}
              depth={depth + 1}
              search={search}
              onFileClick={onFileClick}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function matchesSearch(node: TreeNode, search: string): boolean {
  if (node.type === 'file') return node.name.toLowerCase().includes(search.toLowerCase());
  return (node.children ?? []).some(c => matchesSearch(c, search));
}

function countFiles(node: TreeNode): number {
  if (node.type === 'file') return 1;
  return (node.children ?? []).reduce((sum, c) => sum + countFiles(c), 0);
}

function formatSize(bytes?: number): string {
  if (!bytes) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
