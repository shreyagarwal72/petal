#!/usr/bin/env python3
"""
Forensic Integrity Audit & Stress Test Suite for Milestone 3
Petal Browser UI Redesign (Production Polish & M3 Expressive)
Author: teamwork_auditor_m3_1
"""

import hashlib
import json
import os
import re
import subprocess
import sys

REPO_DIR = "/data/data/com.termux/files/home/petal"
BASELINE_COMMIT = "4bc4bed2e15298a5fd9a55c1d8e3493ce15c4200"

IMMUTABLE_HASHES = {
    "app/src/main/java/com/petal/browser/compose/splash/PetalSplashScreen.kt":
        "57e77b9b75eae37ca858a61383dfb926844d05bd602bea031e4b08720782c78c",
    "app/src/main/java/com/petal/browser/ui/containment/PetalLiquidGlass.kt":
        "f0c71a778e06352829dc10c86144a33614aed5a12c2059e451fa829b529044b1",
    "app/src/main/java/com/petal/browser/ui/containment/PetalMotion.kt":
        "d9a26988285fd0f88fc4c43fc36fe581da7628e97c6ff4b64716204af2afcd79",
    "app/src/main/java/com/petal/browser/ui/containment/PetalSettingsSection.kt":
        "37fbd4fd2bec033ccec8cf6ec6b6a31a5bfc3e2261b2abb5f9f1df63ee390c49",
}

PATCH_FILES = [
    ("app/src/main/java/com/petal/browser/browser/PetalTabViewController.kt", ["trackNavigation", "isHistoryPending", "restoreLocationSeen", "navUrls"]),
    ("app/src/main/java/com/petal/browser/engine/gecko/PetalTabSuspender.kt", ["controller.destroy(keepThumbnail = true)"]),
    ("app/src/main/java/com/petal/browser/unit/PetalCacheManager.kt", ["TabThumbnailCache.trimMemory", "registrableDomain", "Flags.APP_TEMP_CACHE"]),
    ("app/src/main/java/com/petal/browser/unit/PetalTabSessionManager.kt", ["capturedNavTrails", "PetalTabStateStore.writeNav", "TabThumbnailCache.retainOnly"]),
    ("app/src/main/java/com/petal/browser/unit/PetalTabStateStore.kt", ["writeNav", "readNav", "navFileName", "NAV_EXT"]),
    ("app/src/main/java/com/petal/browser/unit/TabThumbnailCache.java", ["retainOnly", "trimMemory", "WRITES_SINCE_TRIM", "MAX_PREVIEW_WIDTH"]),
]

M3_DIRECTORIES = [
    "app/src/main/java/com/petal/browser/compose/tabs",
    "app/src/main/java/com/petal/browser/compose/home",
    "app/src/main/java/com/petal/browser/compose/incognito",
    "app/src/main/java/com/petal/browser/compose/pdf",
    "app/src/main/java/com/petal/browser/compose/extensions",
    "app/src/main/java/com/petal/browser/compose/downloads",
    "app/src/main/java/com/petal/browser/compose/file",
    "app/src/main/java/com/petal/browser/compose/reader",
    "app/src/main/java/com/petal/browser/compose/bookmarks",
    "app/src/main/java/com/petal/browser/compose/history",
    "app/src/main/java/com/petal/browser/compose/settings/screens",
]


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


def test_immutability():
    print("=== TEST 1: Strict Immutability Verification ===")
    all_passed = True
    for rel_path, expected_hash in IMMUTABLE_HASHES.items():
        full_path = os.path.join(REPO_DIR, rel_path)
        actual_hash = sha256_file(full_path)
        if actual_hash == expected_hash:
            print(f"  [PASS] {rel_path} SHA256: {actual_hash[:16]}... matches baseline")
        else:
            print(f"  [FAIL] {rel_path} SHA256 mismatch! Got: {actual_hash}, expected: {expected_hash}")
            all_passed = False
    return all_passed


def test_containment_boundary():
    print("\n=== TEST 2: Containment Boundary Enforcement ===")
    res = subprocess.run(
        ["git", "diff", "--name-only", BASELINE_COMMIT, "--", "app/src/main/java/com/petal/browser/ui/containment/"],
        cwd=REPO_DIR, capture_output=True, text=True, check=True
    )
    changed = [f for f in res.stdout.splitlines() if f]
    print(f"  Files modified in ui/containment: {changed}")
    allowed = {
        "app/src/main/java/com/petal/browser/ui/containment/PetalContainment.kt",
        "app/src/main/java/com/petal/browser/ui/containment/PetalTransientSurfaces.kt",
    }
    unauthorized = set(changed) - allowed
    if unauthorized:
        print(f"  [FAIL] Unauthorized files modified in ui/containment: {unauthorized}")
        return False
    print("  [PASS] Only authorized containment files modified.")
    return True


def test_patches_integrity():
    print("\n=== TEST 3: Pre-Merged Patches Integrity ===")
    all_passed = True
    for rel_path, symbols in PATCH_FILES:
        full_path = os.path.join(REPO_DIR, rel_path)
        if not os.path.exists(full_path):
            print(f"  [FAIL] Missing patch file: {rel_path}")
            all_passed = False
            continue
        with open(full_path, "r", encoding="utf-8") as f:
            content = f.read()
        missing = [s for s in symbols if s not in content]
        if missing:
            print(f"  [FAIL] {rel_path} missing patch symbols: {missing}")
            all_passed = False
        else:
            print(f"  [PASS] {rel_path} contains all expected patch symbols {symbols}")
    return all_passed


def test_m3_files_cleanliness():
    print("\n=== TEST 4: Milestone 3 Direct Static Analysis ===")
    border_re = re.compile(r"\bBorderStroke\s*\(")
    mb_re = re.compile(r"(\.border\s*\(|Modifier\.border\s*\()")
    ot_re = re.compile(r"\bOutlinedTextField\s*\(")
    ob_re = re.compile(r"\bOutlinedButton\s*\(")
    oc_re = re.compile(r"\bOutlinedCard\s*\(")
    shape_re = re.compile(r"PetalMaterialShapes\.(\w+)")

    all_passed = True
    file_count = 0
    total_lines = 0

    for d in M3_DIRECTORIES:
        full_d = os.path.join(REPO_DIR, d)
        for root, _, files in os.walk(full_d):
            for file in files:
                if not file.endswith(".kt"):
                    continue
                file_count += 1
                abs_path = os.path.join(root, file)
                rel_path = os.path.relpath(abs_path, REPO_DIR)
                with open(abs_path, "r", encoding="utf-8") as f:
                    lines = f.readlines()
                total_lines += len(lines)

                for idx, line in enumerate(lines, 1):
                    s = line.strip()
                    if s.startswith("//") or s.startswith("/*") or s.startswith("*") or s.startswith("import "):
                        continue

                    # Borders
                    if border_re.search(line):
                        if "PetalTabGridSwitcher.kt" in rel_path and ("isHoveredForMerge" in line or "tab.isSelected" in line):
                            pass
                        else:
                            print(f"  [FAIL] Unauthorized BorderStroke at {rel_path}:{idx}: {s}")
                            all_passed = False

                    if mb_re.search(line):
                        if "PetalTabGridSwitcher.kt" in rel_path and "border(borderStroke" in line:
                            pass
                        else:
                            print(f"  [FAIL] Unauthorized Modifier.border at {rel_path}:{idx}: {s}")
                            all_passed = False

                    # Outlines
                    if ot_re.search(line):
                        print(f"  [FAIL] Unauthorized OutlinedTextField at {rel_path}:{idx}: {s}")
                        all_passed = False
                    if ob_re.search(line):
                        print(f"  [FAIL] Unauthorized OutlinedButton at {rel_path}:{idx}: {s}")
                        all_passed = False
                    if oc_re.search(line):
                        print(f"  [FAIL] Unauthorized OutlinedCard at {rel_path}:{idx}: {s}")
                        all_passed = False

                    # Shapes
                    for m in shape_re.finditer(line):
                        sh = m.group(1)
                        if sh in ("allShapes", "PREF_EXPRESSIVE_SHAPES_ENABLED", "ShapeEntry"):
                            continue
                        if "PetalIncognitoHomeScreen.kt" in rel_path and sh == "Cookie12Sided":
                            continue
                        print(f"  [FAIL] Unauthorized PetalMaterialShapes.{sh} at {rel_path}:{idx}: {s}")
                        all_passed = False

    print(f"  Audited {file_count} files ({total_lines} lines of code) across M3 scope.")
    if all_passed:
        print("  [PASS] All M3 files have ZERO unauthorized borders, outlines, or shapes.")
    return all_passed


def test_anti_facade_integrity():
    print("\n=== TEST 5: Anti-Facade and Code Integrity Analysis ===")
    all_passed = True

    # Check for empty composables or stub returns in M3 git diff
    proc = subprocess.run(
        ["git", "diff", BASELINE_COMMIT, "--"] + [os.path.join(REPO_DIR, d) for d in M3_DIRECTORIES],
        cwd=REPO_DIR, capture_output=True, text=True, check=True
    )
    diff = proc.stdout

    suspicious_patterns = [
        (re.compile(r"^\+\s*TODO\b", re.M), "TODO() stub"),
        (re.compile(r"^\+\s*throw\s+NotImplementedError\b", re.M), "NotImplementedError"),
        (re.compile(r"^\+\s*return\s+null\b", re.M), "Stubbed return null"),
        (re.compile(r"@Composable\s+fun\s+\w+\([^)]*\)\s*\{\s*\}", re.M), "Empty composable function body"),
        (re.compile(r"//\s*(dummy|stub|fake|bypass|mock|cheat)", re.IGNORECASE | re.M), "Suspicious dummy/mock comment"),
    ]

    for pat, desc in suspicious_patterns:
        matches = pat.findall(diff)
        if matches:
            print(f"  [FAIL] Detected {desc} in M3 diff: count={len(matches)}")
            all_passed = False
        else:
            print(f"  [PASS] 0 instances of {desc}")

    return all_passed


def test_audit_tool_gate():
    print("\n=== TEST 6: Official Audit Tool Gate Execution ===")
    proc = subprocess.run(
        ["python3", "scripts/audit_ui_integrity.py", "--json"],
        cwd=REPO_DIR, capture_output=True, text=True, check=False
    )
    data = json.loads(proc.stdout)
    violations = data.get("violations", [])

    m3_violations = []
    for v in violations:
        file = v["file"]
        if any(file.startswith(d) for d in M3_DIRECTORIES):
            m3_violations.append(v)

    containment_violations = [v for v in violations if v["category"] in ("Containment", "SplashScreen")]

    print(f"  Repository total violations: {data.get('total_violations')}")
    print(f"  Milestone 3 scope violations: {len(m3_violations)}")
    print(f"  Containment/Splash violations: {len(containment_violations)}")

    if m3_violations:
        print(f"  [FAIL] Violations found in M3 scope: {m3_violations}")
        return False
    if containment_violations:
        print(f"  [FAIL] Violations found in Containment/Splash: {containment_violations}")
        return False

    print("  [PASS] Official audit tool reports 0 violations in M3 scope and 0 in Containment/Splash.")
    return True


def main():
    print("================================================================================")
    print(" PETAL BROWSER - FORENSIC INTEGRITY AUDIT (MILESTONE 3)")
    print("================================================================================")
    results = [
        test_immutability(),
        test_containment_boundary(),
        test_patches_integrity(),
        test_m3_files_cleanliness(),
        test_anti_facade_integrity(),
        test_audit_tool_gate(),
    ]

    print("\n" + "=" * 80)
    if all(results):
        print(" FORENSIC VERDICT: CLEAN")
        print(" All checks PASSED with 0 violations, 0 integrity bypasses, 0 facades.")
        print("=" * 80)
        sys.exit(0)
    else:
        print(" FORENSIC VERDICT: INTEGRITY VIOLATION")
        print(" One or more integrity checks FAILED.")
        print("=" * 80)
        sys.exit(1)


if __name__ == "__main__":
    main()
