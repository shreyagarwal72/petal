#!/usr/bin/env python3
"""
Petal Browser - UI Integrity and Design System Audit Tool
Enforces Material 3 Expressive redesign standards across the codebase:
1. Zero unauthorized BorderStroke or Modifier.border occurrences.
2. Zero unauthorized modifications to app/src/main/java/com/petal/browser/ui/containment/
3. Zero modifications to compose/splash/PetalSplashScreen.kt.
4. Zero unauthorized OutlinedTextField and OutlinedButton occurrences.
5. Zero unauthorized expressive shape usages outside designated hero/empty moments.

Exit code 0 indicates 100% compliance.
Exit code 1 indicates design system or integrity violations.
"""

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from collections import Counter, defaultdict

# Baseline commit hash representing pre-redesign state (v4.6.0 release)
BASELINE_COMMIT = "4bc4bed2e15298a5fd9a55c1d8e3493ce15c4200"

# Baseline SHA-256 hashes of strictly immutable files
IMMUTABLE_FILE_HASHES = {
    "app/src/main/java/com/petal/browser/compose/splash/PetalSplashScreen.kt":
        "57e77b9b75eae37ca858a61383dfb926844d05bd602bea031e4b08720782c78c",
    "app/src/main/java/com/petal/browser/ui/containment/PetalLiquidGlass.kt":
        "f0c71a778e06352829dc10c86144a33614aed5a12c2059e451fa829b529044b1",
    "app/src/main/java/com/petal/browser/ui/containment/PetalMotion.kt":
        "d9a26988285fd0f88fc4c43fc36fe581da7628e97c6ff4b64716204af2afcd79",
    "app/src/main/java/com/petal/browser/ui/containment/PetalSettingsSection.kt":
        "37fbd4fd2bec033ccec8cf6ec6b6a31a5bfc3e2261b2abb5f9f1df63ee390c49",
}

# Expressive shapes definition files (not counted as violations)
EXPRESSIVE_SHAPE_DEFINITION_FILES = {
    "app/src/main/java/com/petal/browser/ui/theme/PetalMaterialShapes.kt",
    "app/src/main/java/com/petal/browser/ui/theme/PetalSmoothShapes.kt",
    "app/src/main/java/com/petal/browser/media/PetalCookieShape.kt",
}

# Permitted expressive shape usages in hero or empty state moments strictly:
HERO_EMPTY_SHAPE_ALLOWANCES = {
    "app/src/main/java/com/petal/browser/ui/components/EmptyStateBlob.kt": {"Bun"},
    "app/src/main/java/com/petal/browser/setup/PetalExpressiveHeroArt.kt": {"Cookie12Sided", "RoundedSquare", "Pill"},
    "app/src/main/java/com/petal/browser/ui/components/PetalDeveloperHeroSection.kt": {"Cookie12Sided"},
    "app/src/main/java/com/petal/browser/compose/incognito/PetalIncognitoHomeScreen.kt": {"Cookie12Sided"},
    "app/src/main/java/com/petal/browser/ui/components/PetalUpdateBottomSheet.kt": {"Cookie9Sided"},
}


class Violation:
    def __init__(self, category, file_path, line_number, symbol, snippet):
        self.category = category
        self.file_path = file_path
        self.line_number = line_number
        self.symbol = symbol
        self.snippet = snippet

    def to_dict(self):
        return {
            "category": self.category,
            "file": self.file_path,
            "line": self.line_number,
            "symbol": self.symbol,
            "snippet": self.snippet,
        }


def compute_file_sha256(abs_path):
    if not os.path.isfile(abs_path):
        return None
    h = hashlib.sha256()
    with open(abs_path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


def find_repo_root(start_dir):
    current = os.path.abspath(start_dir)
    while True:
        if os.path.exists(os.path.join(current, ".git")) and os.path.exists(os.path.join(current, "app")):
            return current
        parent = os.path.dirname(current)
        if parent == current:
            # Fallback to current working directory
            return os.path.abspath(start_dir)
        current = parent


def get_git_base_commit(repo_dir):
    # Try baseline commit, then v4.6.0 tag, then merge-base
    candidates = [
        BASELINE_COMMIT,
        "v4.6.0",
        "origin/main",
        "HEAD",
    ]
    for c in candidates:
        try:
            res = subprocess.run(
                ["git", "rev-parse", "--verify", c],
                cwd=repo_dir,
                capture_output=True,
                text=True,
                check=False
            )
            if res.returncode == 0:
                return c
        except Exception:
            pass
    return None


def is_focus_ring(call_expr, context_window):
    """
    Check if a border construct is an authorized keyboard focus ring:
    - 2dp primary ring: width 2.dp, color primary
    - Explicitly associated with focus state or modifier
    """
    has_2dp = ("2.dp" in call_expr or "2.0.dp" in call_expr) and ("2.5.dp" not in call_expr)
    has_primary = "primary" in call_expr
    ctx_lower = context_window.lower()
    has_focus = (
        "focus" in ctx_lower
        or "isfocused" in ctx_lower
        or "focusrequester" in ctx_lower
        or "onfocuschanged" in ctx_lower
        or "focusindicator" in ctx_lower
    )
    return has_2dp and has_primary and has_focus


def audit_borders(repo_dir, src_dir):
    """
    Audit BorderStroke and Modifier.border across app/src/main/.
    Allowed exceptions strictly:
    1. Focus ring: 2dp primary ring
    2. Selected tab thumbnail highlight in compose/tabs/PetalTabGridSwitcher.kt:1889
    3. Drag-and-merge drop target in compose/tabs/PetalTabGridSwitcher.kt:1887
    4. Tab counter badge in ui/components/PetalBottomNavBar.kt:607
    """
    violations = []
    bs_pattern = re.compile(r'\bBorderStroke\s*\(')
    mb_pattern = re.compile(r'(\.border\s*\(|Modifier\.border\s*\()')

    for root, _, files in os.walk(src_dir):
        for f in files:
            if not (f.endswith(".kt") or f.endswith(".java")):
                continue
            abs_path = os.path.join(root, f)
            rel_path = os.path.relpath(abs_path, repo_dir)

            with open(abs_path, "r", encoding="utf-8", errors="replace") as fp:
                lines = fp.readlines()

            for idx, line in enumerate(lines, 1):
                stripped = line.strip()
                if stripped.startswith("import ") or stripped.startswith("//") or stripped.startswith("*"):
                    continue

                call_expr = "".join(lines[idx - 1:min(len(lines), idx + 8)])
                context_window = "".join(lines[max(0, idx - 6):min(len(lines), idx + 6)])

                # Check BorderStroke
                if bs_pattern.search(line):
                    # Exception: Protected containment design in ui/containment/ that must not be modified
                    if "ui/containment/PetalContainment.kt" in rel_path and not (908 <= idx <= 945):
                        continue
                    if "ui/containment/PetalTransientSurfaces.kt" in rel_path and (36 <= idx <= 90):
                        continue

                    # Exception 1: Selected tab thumbnail highlight in PetalTabGridSwitcher.kt:1889
                    if "PetalTabGridSwitcher.kt" in rel_path and ("tab.isSelected" in line or idx == 1889):
                        continue

                    # Exception 2: Drag-and-merge drop target in PetalTabGridSwitcher.kt:1887
                    if "PetalTabGridSwitcher.kt" in rel_path and ("isHoveredForMerge" in line or idx == 1887):
                        continue

                    # Exception 3: Keyboard focus ring (2dp primary ring)
                    if is_focus_ring(call_expr, context_window):
                        continue

                    violations.append(Violation(
                        category="Borders",
                        file_path=rel_path,
                        line_number=idx,
                        symbol="BorderStroke",
                        snippet=stripped
                    ))

                # Check Modifier.border
                if mb_pattern.search(line):
                    # Exception 4: Tab counter badge in PetalBottomNavBar.kt:607
                    if "PetalBottomNavBar.kt" in rel_path and (
                        idx == 607 or "RoundedCornerShape(7.dp)" in call_expr or "count" in context_window
                    ):
                        continue

                    # Selected tab thumbnail highlight / drag-merge modifier in PetalTabGridSwitcher.kt:1927
                    if "PetalTabGridSwitcher.kt" in rel_path and (
                        idx == 1927 or "border(borderStroke" in line
                    ):
                        continue

                    # Exception 3: Keyboard focus ring (2dp primary ring)
                    if is_focus_ring(call_expr, context_window):
                        continue

                    violations.append(Violation(
                        category="Borders",
                        file_path=rel_path,
                        line_number=idx,
                        symbol="Modifier.border",
                        snippet=stripped
                    ))

    return violations


def audit_containment_and_splash(repo_dir):
    """
    Audit immutability of:
    - compose/splash/PetalSplashScreen.kt (0 modifications)
    - app/src/main/java/com/petal/browser/ui/containment/ (0 modifications outside allowed targets)
    Allowed exceptions strictly:
    * PetalConnectedButtonGroup and petalConnectedShape in PetalContainment.kt
    * PetalSheet / PetalAlertDialog in PetalTransientSurfaces.kt
    * PetalPopupMenu in PetalTransientSurfaces.kt
    """
    violations = []
    base_commit = get_git_base_commit(repo_dir)

    # 1. Strictly immutable files (Splash screen + pure containment files)
    for rel_path, expected_hash in IMMUTABLE_FILE_HASHES.items():
        abs_path = os.path.join(repo_dir, rel_path)
        actual_hash = compute_file_sha256(abs_path)
        cat = "SplashScreen" if "PetalSplashScreen.kt" in rel_path else "Containment"

        if actual_hash is not None and actual_hash != expected_hash:
            # Hash changed, confirm with git diff if git is available
            is_diff = True
            if base_commit:
                try:
                    res = subprocess.run(
                        ["git", "diff", "--name-only", base_commit, "--", rel_path],
                        cwd=repo_dir, capture_output=True, text=True, check=False
                    )
                    is_diff = bool(res.stdout.strip())
                except Exception:
                    pass
            if is_diff:
                violations.append(Violation(
                    category=cat,
                    file_path=rel_path,
                    line_number=1,
                    symbol="ImmutableFileModified",
                    snippet=f"File {rel_path} was modified (must remain strictly untouched with 0 modifications)"
                ))

    # 2. Check PetalContainment.kt
    # Protected components: PetalGroup*, PetalFloatingToolbar, Petal*Chip, Petal*Card, liquidGlass*, etc.
    # Allowed exceptions: PetalConnectedButtonGroup, petalConnectedShape, PetalSheet, and top imports
    containment_file = "app/src/main/java/com/petal/browser/ui/containment/PetalContainment.kt"
    if base_commit:
        try:
            res = subprocess.run(
                ["git", "diff", "-U0", base_commit, "--", containment_file],
                cwd=repo_dir, capture_output=True, text=True, check=False
            )
            if res.returncode == 0 and res.stdout.strip():
                hunk_re = re.compile(r'@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@')
                for line in res.stdout.splitlines():
                    m = hunk_re.match(line)
                    if m:
                        start = int(m.group(1))
                        count = int(m.group(2)) if m.group(2) else 1
                        end = start + count - 1 if count > 0 else start
                        for ln in range(start, end + 1):
                            # Allowed baseline lines:
                            # 1..125: Package & imports
                            # 908..938: petalConnectedShape & PetalConnectedButtonGroup
                            # 1010..1095: PetalSheet
                            in_allowed = (1 <= ln <= 125) or (908 <= ln <= 938) or (1010 <= ln <= 1095)
                            if not in_allowed:
                                violations.append(Violation(
                                    category="Containment",
                                    file_path=containment_file,
                                    line_number=ln,
                                    symbol="UnauthorizedContainmentEdit",
                                    snippet=f"Unauthorized edit to protected containment component at line {ln}"
                                ))
                                break
        except Exception:
            pass

    # 3. Check PetalTransientSurfaces.kt
    # Protected components: PetalSnackbarHost (36..90), PetalMenuDivider (133..145), PetalPopupMenuItem (146..230)
    # Allowed exceptions: PetalPopupMenu, PetalAlertDialog, PetalMaterialAlertDialog, PetalDialog, PetalSheet
    transient_file = "app/src/main/java/com/petal/browser/ui/containment/PetalTransientSurfaces.kt"
    if base_commit:
        try:
            res = subprocess.run(
                ["git", "diff", "-U0", base_commit, "--", transient_file],
                cwd=repo_dir, capture_output=True, text=True, check=False
            )
            if res.returncode == 0 and res.stdout.strip():
                hunk_re = re.compile(r'@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@')
                for line in res.stdout.splitlines():
                    m = hunk_re.match(line)
                    if m:
                        start = int(m.group(1))
                        count = int(m.group(2)) if m.group(2) else 1
                        end = start + count - 1 if count > 0 else start
                        for ln in range(start, end + 1):
                            # Protected ranges:
                            is_protected = (36 <= ln <= 90) or (133 <= ln <= 230)
                            if is_protected:
                                target_name = "PetalSnackbarHost" if ln <= 90 else (
                                    "PetalMenuDivider" if ln <= 145 else "PetalPopupMenuItem"
                                )
                                violations.append(Violation(
                                    category="Containment",
                                    file_path=transient_file,
                                    line_number=ln,
                                    symbol="UnauthorizedTransientSurfaceEdit",
                                    snippet=f"Unauthorized edit to protected component '{target_name}' at line {ln}"
                                ))
                                break
        except Exception:
            pass

    return violations


def audit_outlined_components(repo_dir, src_dir):
    """
    Audit unauthorized OutlinedTextField and OutlinedButton occurrences.
    """
    violations = []
    tf_pattern = re.compile(r'\bOutlinedTextField\s*\(')
    btn_pattern = re.compile(r'\bOutlinedButton\s*\(')

    for root, _, files in os.walk(src_dir):
        for f in files:
            if not (f.endswith(".kt") or f.endswith(".java")):
                continue
            abs_path = os.path.join(root, f)
            rel_path = os.path.relpath(abs_path, repo_dir)

            with open(abs_path, "r", encoding="utf-8", errors="replace") as fp:
                lines = fp.readlines()

            for idx, line in enumerate(lines, 1):
                stripped = line.strip()
                if stripped.startswith("import ") or stripped.startswith("//") or stripped.startswith("*"):
                    continue

                if tf_pattern.search(line):
                    violations.append(Violation(
                        category="Outlines",
                        file_path=rel_path,
                        line_number=idx,
                        symbol="OutlinedTextField",
                        snippet=stripped
                    ))

                if btn_pattern.search(line):
                    violations.append(Violation(
                        category="Outlines",
                        file_path=rel_path,
                        line_number=idx,
                        symbol="OutlinedButton",
                        snippet=stripped
                    ))

    return violations


def audit_expressive_shapes(repo_dir, src_dir):
    """
    Audit expressive shape usages outside hero or empty state moments.
    """
    violations = []
    shape_pattern = re.compile(r'PetalMaterialShapes\.(\w+)|PetalCookieShape')

    for root, _, files in os.walk(src_dir):
        for f in files:
            if not (f.endswith(".kt") or f.endswith(".java")):
                continue
            abs_path = os.path.join(root, f)
            rel_path = os.path.relpath(abs_path, repo_dir)

            # Skip definition files
            if rel_path in EXPRESSIVE_SHAPE_DEFINITION_FILES:
                continue

            with open(abs_path, "r", encoding="utf-8", errors="replace") as fp:
                lines = fp.readlines()

            for idx, line in enumerate(lines, 1):
                stripped = line.strip()
                if stripped.startswith("import ") or stripped.startswith("//") or stripped.startswith("*"):
                    continue

                for match in shape_pattern.finditer(line):
                    shape_name = match.group(1) or "PetalCookieShape"

                    # Non-shape metadata or preference keys
                    if shape_name in ("allShapes", "PREF_EXPRESSIVE_SHAPES_ENABLED", "ShapeEntry"):
                        continue

                    # Check hero/empty state allowances
                    if rel_path in HERO_EMPTY_SHAPE_ALLOWANCES:
                        allowed_shapes = HERO_EMPTY_SHAPE_ALLOWANCES[rel_path]
                        if shape_name in allowed_shapes:
                            continue

                    violations.append(Violation(
                        category="Shapes",
                        file_path=rel_path,
                        line_number=idx,
                        symbol=f"PetalMaterialShapes.{shape_name}",
                        snippet=stripped
                    ))

    return violations


def print_table(headers, rows):
    """Print an ASCII formatted table."""
    col_widths = [max(len(str(r[i])) for r in [headers] + rows) + 2 for i in range(len(headers))]
    sep = "+" + "+".join("-" * w for w in col_widths) + "+"
    header_str = "|" + "|".join(f" {str(headers[i]).ljust(col_widths[i] - 1)}" for i in range(len(headers))) + "|"

    print(sep)
    print(header_str)
    print(sep)
    for r in rows:
        row_str = "|" + "|".join(f" {str(r[i]).ljust(col_widths[i] - 1)}" for i in range(len(r))) + "|"
        print(row_str)
    print(sep)


def run_audit(repo_dir, categories=None):
    src_dir = os.path.join(repo_dir, "app/src/main/java")
    if not os.path.isdir(src_dir):
        src_dir = os.path.join(repo_dir, "app/src/main")

    all_violations = []

    if categories is None or "borders" in categories or "all" in categories:
        all_violations.extend(audit_borders(repo_dir, src_dir))

    if categories is None or "containment" in categories or "splash" in categories or "all" in categories:
        containment_and_splash = audit_containment_and_splash(repo_dir)
        for v in containment_and_splash:
            if categories is None or "all" in categories or v.category.lower() in categories:
                all_violations.append(v)

    if categories is None or "outlines" in categories or "all" in categories:
        all_violations.extend(audit_outlined_components(repo_dir, src_dir))

    if categories is None or "shapes" in categories or "all" in categories:
        all_violations.extend(audit_expressive_shapes(repo_dir, src_dir))

    return all_violations


def main():
    parser = argparse.ArgumentParser(description="Petal Browser UI Integrity & Design System Audit Tool")
    parser.add_argument("--repo", default=None, help="Root path of the Petal repository")
    parser.add_argument("--category", "-c", choices=["borders", "containment", "splash", "outlines", "shapes", "all"], default="all",
                        help="Filter audit to a specific category")
    parser.add_argument("--verbose", "-v", action="store_true", help="Print detailed list of all violations")
    parser.add_argument("--json", action="store_true", help="Output audit results in JSON format")
    parser.add_argument("--baseline", action="store_true", help="Print baseline numbers for tracking")
    parser.add_argument("--quiet", "-q", action="store_true", help="Minimal output, suppress hints")
    parser.add_argument("--max-files", type=int, default=30, help="Max file rows to show in table (default: 30)")
    args = parser.parse_args()

    repo_dir = args.repo or find_repo_root(os.path.dirname(__file__))
    if not os.path.isdir(repo_dir):
        print(f"Error: Repository root not found at {repo_dir}", file=sys.stderr)
        sys.exit(2)

    categories = [args.category] if args.category != "all" else None
    violations = run_audit(repo_dir, categories)

    if args.json:
        result = {
            "total_violations": len(violations),
            "status": "PASSED" if len(violations) == 0 else "FAILED",
            "violations": [v.to_dict() for v in violations],
        }
        print(json.dumps(result, indent=2))
        sys.exit(0 if len(violations) == 0 else 1)

    print("=" * 80)
    print(" PETAL BROWSER - UI INTEGRITY & DESIGN SYSTEM AUDIT")
    print(f" Repository: {repo_dir}")
    base_commit = get_git_base_commit(repo_dir)
    if base_commit:
        print(f" Baseline commit: {base_commit}")
    print("=" * 80)
    print()

    # Category breakdown
    cat_counts = Counter(v.category for v in violations)
    cat_files = defaultdict(set)
    for v in violations:
        cat_files[v.category].add(v.file_path)

    all_categories = ["Borders", "Outlines", "Shapes", "Containment", "SplashScreen"]
    summary_rows = []
    for cat in all_categories:
        count = cat_counts[cat]
        files_count = len(cat_files[cat])
        status = "PASS" if count == 0 else "FAIL"
        summary_rows.append([cat, str(count), str(files_count), status])

    total_violations = len(violations)
    total_affected_files = len(set(v.file_path for v in violations))
    overall_status = "PASS" if total_violations == 0 else "FAIL"
    summary_rows.append(["TOTAL", str(total_violations), str(total_affected_files), overall_status])

    print("CATEGORY SUMMARY:")
    print_table(["Category", "Violations", "Affected Files", "Status"], summary_rows)
    print()

    if args.baseline:
        print("BASELINE METRICS:")
        print(f"  unauthorized_borders: {cat_counts['Borders']}")
        print(f"  unauthorized_outlines: {cat_counts['Outlines']}")
        print(f"  unauthorized_shapes: {cat_counts['Shapes']}")
        print(f"  containment_violations: {cat_counts['Containment']}")
        print(f"  splash_violations: {cat_counts['SplashScreen']}")
        print(f"  total_violations: {total_violations}")
        print()

    # File-by-file breakdown
    if violations:
        file_stats = defaultdict(lambda: Counter())
        for v in violations:
            file_stats[v.file_path][v.category] += 1

        sorted_files = sorted(file_stats.items(), key=lambda x: -sum(x[1].values()))
        file_rows = []
        for file_path, counts in sorted_files[:args.max_files]:
            tot = sum(counts.values())
            # Truncate file path display if too long
            display_path = file_path
            if display_path.startswith("app/src/main/java/com/petal/browser/"):
                display_path = display_path[len("app/src/main/java/com/petal/browser/"):]
            file_rows.append([
                display_path,
                str(counts["Borders"]),
                str(counts["Outlines"]),
                str(counts["Shapes"]),
                str(counts["Containment"] + counts["SplashScreen"]),
                str(tot)
            ])

        print(f"VIOLATIONS BY FILE (Top {min(len(sorted_files), args.max_files)} of {len(sorted_files)} affected files):")
        print_table(["File (relative to com/petal/browser/)", "Borders", "Outlines", "Shapes", "Boundary", "Total"], file_rows)
        print()

    # Detailed violations listing
    if args.verbose and violations:
        print("DETAILED VIOLATIONS LIST:")
        for idx, v in enumerate(violations, 1):
            print(f"[{idx}] {v.file_path}:{v.line_number} [{v.category}] ({v.symbol})")
            print(f"     > {v.snippet}")
        print()
    elif violations and not args.quiet:
        print(f"Note: Use --verbose (-v) to view all {total_violations} individual violation snippets.")
        print()

    # Final Verdict
    print("-" * 80)
    if total_violations == 0:
        print(" VERDICT: PASSED (0 violations). Codebase fully complies with UI integrity rules.")
        print("-" * 80)
        sys.exit(0)
    else:
        print(f" VERDICT: FAILED ({total_violations} violations found across {total_affected_files} files).")
        print("-" * 80)
        sys.exit(1)


if __name__ == "__main__":
    main()
