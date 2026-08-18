"""Freeze the author's plains corpus as a verifiable baseline, before we start editing it.

Two jobs in one snapshot, and the second is the one that is easy to forget.

**Insurance.** Writing an NBT is exactly how four of these files were destroyed once
(`.gitattributes` lost `*.nbt binary` and a catch-all `text eol=lf` inflated them into
garbage). Three of the four are unrecoverable in every blob git holds AND in the built
jars. So before anything writes into `plains/` we take a byte-exact copy with hashes, and
the hashes are the point: a copy you cannot verify is a rumour.

**The calibration baseline.** Every checker in `tools/` runs `--calibrate` against these 121
readable files, and the repo's rule is that a metric must be quiet on the author's work
before it is believed — that is how three of five `check_fabric` tests were caught being
wrong. Edit the corpus in place and that stops working: you can no longer tell "the metric
is wrong" from "I changed the reference". So the checkers should read the baseline, not the
live folder, and this snapshot is what they read.

Run:  python snapshot_corpus.py            # create or refresh, then verify
      python snapshot_corpus.py --verify    # verify only, change nothing
"""

import argparse
import gzip
import hashlib
import shutil
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
LIVE = REPO / "common/src/main/resources/data/burg/structure/plains"
BASELINE = REPO / "corpus_baseline/plains"
MANIFEST = REPO / "corpus_baseline/MANIFEST.sha256"

# Named, not silently skipped. `gzip -t` PASSES on these — a real decompress is what fails,
# so any sweep that only tests the container reports a clean corpus that is not clean.
KNOWN_CORRUPT = {
    "houses/house_3_lvl6.nbt",
    "jobs/merchant_shop_lvl1.nbt",
    "jobs/wheat_farm_lvl5.nbt",
    "starters/settlement_lvl1.nbt",
}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest()


def readable(path: Path) -> bool:
    """Whether the NBT actually inflates. Not whether gzip says the container is fine."""
    try:
        gzip.decompress(path.read_bytes())
        return True
    except Exception:
        return False


def relpaths() -> list[str]:
    return sorted(
        str(p.relative_to(LIVE)).replace("\\", "/")
        for p in LIVE.rglob("*")
        if p.is_file()
    )


def snapshot() -> None:
    if BASELINE.exists():
        # Refresh rather than rebuild: an existing baseline may already be the reference a
        # checker is calibrated against, and silently replacing it is the failure this file
        # exists to prevent. Report the difference and stop.
        drift = compare()
        if drift:
            print(f"! baseline already exists and {len(drift)} file(s) differ from live:")
            for r in drift[:20]:
                print(f"    {r}")
            print("  Refusing to overwrite. Delete corpus_baseline/ deliberately if that is"
                  " what you mean, or keep it — it is the pre-edit reference.")
            sys.exit(1)
        print(f"= baseline already matches live ({len(relpaths())} files), nothing to do")
        return

    rels = relpaths()
    lines, corrupt = [], []
    for rel in rels:
        src, dst = LIVE / rel, BASELINE / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        digest = sha256(dst)
        if sha256(src) != digest:
            print(f"! copy mismatch on {rel}")
            sys.exit(1)
        ok = readable(src) if rel.endswith(".nbt") else True
        if not ok:
            corrupt.append(rel)
        lines.append(f"{digest}  {rel}{'' if ok else '  # UNREADABLE'}")

    MANIFEST.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"+ {len(rels)} files copied to {BASELINE.relative_to(REPO)}")
    print(f"+ manifest: {MANIFEST.relative_to(REPO)}")
    print(f"  unreadable in the baseline: {len(corrupt)}")
    for c in corrupt:
        flag = "known" if c in KNOWN_CORRUPT else "NEW -- was not corrupt before"
        print(f"    {c}  ({flag})")
    unseen = KNOWN_CORRUPT - set(corrupt)
    if unseen:
        print(f"  ! expected corrupt but read fine: {sorted(unseen)}")


def compare() -> list[str]:
    """Baseline paths whose bytes differ from live, or that exist on only one side."""
    live, base = set(relpaths()), set(
        str(p.relative_to(BASELINE)).replace("\\", "/")
        for p in BASELINE.rglob("*") if p.is_file()
    )
    drift = sorted((live ^ base))
    for rel in sorted(live & base):
        if sha256(LIVE / rel) != sha256(BASELINE / rel):
            drift.append(rel)
    return drift


def verify() -> None:
    if not MANIFEST.exists():
        print("! no manifest -- run without --verify first")
        sys.exit(1)
    bad = 0
    for line in MANIFEST.read_text(encoding="utf-8").splitlines():
        digest, rest = line.split("  ", 1)
        rel = rest.split("  #")[0]
        path = BASELINE / rel
        if not path.exists():
            print(f"! missing from baseline: {rel}")
            bad += 1
        elif sha256(path) != digest:
            print(f"! baseline file changed since the manifest: {rel}")
            bad += 1
    print(f"{'!' if bad else '='} baseline verified, {bad} problem(s)")

    drift = compare()
    if drift:
        print(f"\n{len(drift)} file(s) now differ between live plains/ and the baseline:")
        for r in drift:
            print(f"    {r}")
        print("  That is expected once we start editing -- it is the record of what we changed.")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--verify", action="store_true", help="check the baseline, change nothing")
    args = ap.parse_args()
    verify() if args.verify else snapshot()
