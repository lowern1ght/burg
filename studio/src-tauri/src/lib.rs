use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};

/// Resolve the repo root: two levels up from this app's project root (studio/).
/// In dev: studio/src-tauri/ → studio/ → repo root.
/// In production: we bundle a config file or use an env var. For now: dev-first.
fn repo_root() -> PathBuf {
    let manifest_dir = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
    // studio/src-tauri → studio → repo
    manifest_dir
        .parent() // studio/
        .and_then(|p| p.parent()) // repo
        .map(|p| p.to_path_buf())
        .unwrap_or_else(|| PathBuf::from(".."))
}

fn structures_root() -> PathBuf {
    repo_root()
        .join("common")
        .join("src")
        .join("main")
        .join("resources")
        .join("data")
        .join("onceuponatown")
        .join("structure")
}

fn skins_root() -> PathBuf {
    repo_root()
        .join("common")
        .join("src")
        .join("main")
        .join("resources")
        .join("assets")
        .join("onceuponatown")
        .join("textures")
        .join("entity")
        .join("npc")
}

#[derive(Serialize, Deserialize, Clone)]
pub struct TreeNode {
    name: String,
    path: String,
    #[serde(rename = "type")]
    node_type: String, // "dir" | "file"
    #[serde(skip_serializing_if = "Option::is_none")]
    size: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    children: Option<Vec<TreeNode>>,
}

fn scan_dir(dir: &Path, rel: &str) -> Result<TreeNode, String> {
    let name = dir
        .file_name()
        .map(|n| n.to_string_lossy().to_string())
        .unwrap_or_default();
    let mut node = TreeNode {
        name,
        path: rel.to_string(),
        node_type: "dir".to_string(),
        size: None,
        children: Some(Vec::new()),
    };

    let entries = fs::read_dir(dir).map_err(|e| e.to_string())?;
    let mut dirs: Vec<_> = Vec::new();
    let mut files: Vec<_> = Vec::new();

    for entry in entries.flatten() {
        let entry_path = entry.path();
        let entry_name = entry.file_name().to_string_lossy().to_string();
        let child_rel = if rel.is_empty() {
            entry_name.clone()
        } else {
            format!("{}/{}", rel, entry_name)
        };

        if entry_path.is_dir() {
            dirs.push((entry_path, child_rel));
        } else if entry_path.extension().map(|e| e == "nbt").unwrap_or(false) {
            let size = entry.metadata().map(|m| m.len()).ok();
            files.push(TreeNode {
                name: entry_name,
                path: child_rel,
                node_type: "file".to_string(),
                size,
                children: None,
            });
        }
    }

    // Sort: dirs first, then files; both alphabetical with numeric awareness
    dirs.sort_by(|a, b| a.1.cmp(&b.1));
    files.sort_by(|a, b| natural_cmp(&a.name, &b.name));

    let children = node.children.as_mut().unwrap();
    for (d_path, d_rel) in dirs {
        if let Ok(sub) = scan_dir(&d_path, &d_rel) {
            children.push(sub);
        }
    }
    children.extend(files);

    Ok(node)
}

fn natural_cmp(a: &str, b: &str) -> std::cmp::Ordering {
    // Simple numeric-aware comparison: split into chunks of digits and non-digits.
    let mut a_iter = split_numeric(a);
    let mut b_iter = split_numeric(b);
    loop {
        match (a_iter.next(), b_iter.next()) {
            (None, None) => return std::cmp::Ordering::Equal,
            (None, _) => return std::cmp::Ordering::Less,
            (_, None) => return std::cmp::Ordering::Greater,
            (Some(Chunk::Num(x)), Some(Chunk::Num(y))) => match x.cmp(&y) {
                std::cmp::Ordering::Equal => continue,
                ord => return ord,
            },
            (Some(Chunk::Text(x)), Some(Chunk::Text(y))) => match x.cmp(&y) {
                std::cmp::Ordering::Equal => continue,
                ord => return ord,
            },
            (Some(Chunk::Num(_)), _) => return std::cmp::Ordering::Less,
            (_, Some(Chunk::Num(_))) => return std::cmp::Ordering::Greater,
        }
    }
}

enum Chunk {
    Num(u64),
    Text(String),
}

fn split_numeric(s: &str) -> std::vec::IntoIter<Chunk> {
    let mut chunks = Vec::new();
    let mut buf = String::new();
    let mut is_num = false;

    for ch in s.chars() {
        let ch_is_num = ch.is_ascii_digit();
        if !buf.is_empty() && ch_is_num != is_num {
            if is_num {
                if let Ok(n) = buf.parse::<u64>() {
                    chunks.push(Chunk::Num(n));
                }
            } else {
                chunks.push(Chunk::Text(buf.clone()));
            }
            buf.clear();
        }
        is_num = ch_is_num;
        buf.push(ch);
    }
    if !buf.is_empty() {
        if is_num {
            if let Ok(n) = buf.parse::<u64>() {
                chunks.push(Chunk::Num(n));
            }
        } else {
            chunks.push(Chunk::Text(buf));
        }
    }
    chunks.into_iter()
}

#[tauri::command]
fn list_structures() -> Result<TreeNode, String> {
    scan_dir(&structures_root(), "")
}

#[derive(Serialize)]
pub struct SkinEntry {
    name: String,
    path: String,
    category: String,
    size: u64,
}

#[tauri::command]
fn list_skins() -> Result<Vec<SkinEntry>, String> {
    let root = skins_root();
    let entries = fs::read_dir(&root).map_err(|e| e.to_string())?;
    let mut skins = Vec::new();

    for entry in entries.flatten() {
        let path = entry.path();
        if !path.is_file() {
            continue;
        }
        let name = entry.file_name().to_string_lossy().to_string();
        if !name.ends_with(".png") {
            continue;
        }
        let size = entry.metadata().map(|m| m.len()).unwrap_or(0);

        let category = if name.starts_with("citizen_body") {
            "body"
        } else if name.starts_with("citizen_hair") {
            "hair"
        } else if name.starts_with("citizen_beard") {
            "beard"
        } else if name.starts_with("citizen_headwear") {
            "headwear"
        } else if name.ends_with("_clothes.png") {
            "clothes"
        } else if name == "citizen_trim.png" {
            "trim"
        } else if name == "default_skin.png" {
            "reference"
        } else {
            "misc"
        };

        skins.push(SkinEntry {
            name: name.clone(),
            path: name,
            category: category.to_string(),
            size,
        });
    }

    skins.sort_by(|a, b| natural_cmp(&a.name, &b.name));
    Ok(skins)
}

#[tauri::command]
fn read_nbt(path: String) -> Result<Vec<u8>, String> {
    let full = resolve_structure_path(&path)?;
    fs::read(&full).map_err(|e| format!("{}: {}", path, e))
}

#[tauri::command]
fn read_nbt_from_path(path: String) -> Result<Vec<u8>, String> {
    let p = PathBuf::from(&path);
    if !p.is_file() {
        return Err(format!("not a file: {}", path));
    }
    fs::read(&p).map_err(|e| format!("{}: {}", path, e))
}

fn resolve_structure_path(rel: &str) -> Result<PathBuf, String> {
    let root = structures_root();
    let full = root.join(rel);
    let canonical = full.canonicalize().map_err(|e| e.to_string())?;
    let root_canonical = root.canonicalize().map_err(|e| e.to_string())?;
    if !canonical.starts_with(&root_canonical) {
        return Err("Forbidden".to_string());
    }
    Ok(canonical)
}

#[tauri::command]
fn read_skin(path: String) -> Result<Vec<u8>, String> {
    let root = skins_root();
    let full = root.join(&path);
    let canonical = full.canonicalize().map_err(|e| e.to_string())?;
    let root_canonical = root.canonicalize().map_err(|e| e.to_string())?;
    if !canonical.starts_with(&root_canonical) {
        return Err("Forbidden".to_string());
    }
    fs::read(&canonical).map_err(|e| format!("{}: {}", path, e))
}

#[tauri::command]
fn write_nbt(path: String, data: Vec<u8>) -> Result<(), String> {
    let full = resolve_structure_path(&path)?;
    fs::write(&full, &data).map_err(|e| format!("{}: {}", path, e))
}

/// Save bytes to a file the user picks via a save dialog.
#[tauri::command]
async fn save_nbt_as(
    app: tauri::AppHandle,
    default_name: String,
    data: Vec<u8>,
) -> Result<bool, String> {
    use tauri_plugin_dialog::DialogExt;

    let (tx, rx) = std::sync::mpsc::channel::<Option<std::path::PathBuf>>();
    app.dialog()
        .file()
        .set_title("Save NBT structure")
        .add_filter("NBT structure", &["nbt"])
        .set_file_name(&default_name)
        .save_file(move |path| {
            let _ = tx.send(path.and_then(|p| p.as_path().map(|p| p.to_path_buf())));
        });

    let chosen = rx.recv().map_err(|e| e.to_string())?;
    match chosen {
        Some(p) => {
            fs::write(&p, &data).map_err(|e| format!("{}: {}", p.display(), e))?;
            Ok(true)
        }
        None => Ok(false),
    }
}

/// Serve the Lodestone default-pack files. The pack is in node_modules, which
/// is not accessible from the Tauri Rust backend in production. We stream it
/// via this command for the non-self-culling patch and any other pack assets.
#[tauri::command]
fn read_pack_file(file: String) -> Result<Vec<u8>, String> {
    let manifest_dir = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
    // studio/src-tauri → studio → node_modules
    let pack_root = manifest_dir
        .parent() // studio/
        .map(|p| p.join("node_modules").join("@mattzh72").join("lodestone").join("assets").join("default-pack"))
        .ok_or("cannot resolve pack root")?;

    let full = pack_root.join(&file);
    let canonical = full.canonicalize().map_err(|e| e.to_string())?;
    let root_canonical = pack_root.canonicalize().map_err(|e| e.to_string())?;
    if !canonical.starts_with(&root_canonical) {
        return Err("Forbidden".to_string());
    }
    fs::read(&canonical).map_err(|e| format!("{}: {}", file, e))
}

/// The non-self-culling patch: read the original file, append every non-cube
/// block id from assets.json. Same logic as the Vite middleware, now in Rust.
#[tauri::command]
fn read_non_self_culling() -> Result<String, String> {
    let manifest_dir = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
    let pack_root = manifest_dir
        .parent()
        .map(|p| p.join("node_modules").join("@mattzh72").join("lodestone").join("assets").join("default-pack"))
        .ok_or("cannot resolve pack root")?;

    let original = fs::read_to_string(pack_root.join("block-flags").join("non_self_culling.txt"))
        .map_err(|e| e.to_string())?
        .trim_end()
        .to_string();

    let assets_json = fs::read_to_string(pack_root.join("assets.json"))
        .map_err(|e| e.to_string())?;
    let assets: serde_json::Value = serde_json::from_str(&assets_json).map_err(|e| e.to_string())?;

    let suffixes = [
        "_slab", "_stairs", "_fence", "_fence_gate", "_wall", "_pane", "_trapdoor", "_door",
        "_bars", "_carpet", "_bed", "_sign", "_button", "_pressure_plate", "_rod", "_chain",
        "_lantern", "_torch", "_ladder", "_candle", "_grate",
    ];
    let exact = ["ladder", "iron_bars", "lantern", "chain", "glass_pane", "scaffolding"];

    let mut ids: Vec<String> = Vec::new();
    if let Some(blockstates) = assets.get("blockstates").and_then(|v| v.as_object()) {
        for key in blockstates.keys() {
            if exact.contains(&key.as_str())
                || suffixes.iter().any(|s| key.ends_with(s))
            {
                ids.push(format!("minecraft:{}", key));
            }
        }
    }
    ids.sort();
    ids.dedup();

    Ok(format!("{}\n{}\n", original, ids.join("\n")))
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_shell::init())
        .setup(|app| {
            if cfg!(debug_assertions) {
                app.handle().plugin(
                    tauri_plugin_log::Builder::default()
                        .level(log::LevelFilter::Info)
                        .build(),
                )?;
            }
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            list_structures,
            list_skins,
            read_nbt,
            read_nbt_from_path,
            read_skin,
            write_nbt,
            save_nbt_as,
            read_pack_file,
            read_non_self_culling,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
