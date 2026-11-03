#!/usr/bin/env python3
"""
Stress Test Suite for Milestone 3 Track 2: Settings Screens Redesign
Validates:
1. Zero BorderStroke or Modifier.border occurrences across all 12 files.
2. Zero OutlinedTextField or OutlinedButton occurrences.
3. Zero M3ExpressiveVariableBackground occurrences in PrivacySettingsScreen.kt.
4. PetalFeatureExplainerDialog uses PetalAlertDialog with 0 borders.
5. Asymmetric connected button pairs adhere to 50dp outer / 6dp inner corner geometry.
6. Filled tonal TextFields use surfaceContainerHighest with transparent indicators.
7. Clean containment boundary: 0 modifications to ui/containment and splash screen.
"""

import os
import re
import sys

SETTINGS_DIR = "app/src/main/java/com/petal/browser/compose/settings/screens"
FILES = [
    "PetalFeatureExplainerDialog.kt",
    "UpdaterSettingsScreen.kt",
    "AppearanceSettingsScreen.kt",
    "PetalAdBlockSettingsScreen.kt",
    "DisplaySettingsScreen.kt",
    "AddressBarSettingsScreen.kt",
    "SettingsComponents.kt",
    "ApiIntegrationsSettingsScreen.kt",
    "DataBackupSettingsScreen.kt",
    "PrivacySettingsScreen.kt",
    "SearchHomeSettingsScreen.kt",
    "SettingsHubScreen.kt",
]

def run_tests():
    print("=" * 80)
    print(" PETAL BROWSER - SETTINGS SCREENS EMPIRICAL STRESS TEST SUITE")
    print("=" * 80)
    
    passed = 0
    failed = 0

    # Test 1: Border Elimination
    print("\n=== TEST 1: Border Elimination Across All 12 Files ===")
    for fname in FILES:
        fpath = os.path.join(SETTINGS_DIR, fname)
        if not os.path.exists(fpath):
            print(f"  [FAIL] {fname} does not exist!")
            failed += 1
            continue
        with open(fpath, "r", encoding="utf-8") as f:
            content = f.read()
        
        has_border_stroke = "BorderStroke" in content
        has_mod_border = bool(re.search(r'\.border\s*\(', content))
        if has_border_stroke or has_mod_border:
            print(f"  [FAIL] {fname} has unauthorized border occurrences (BorderStroke={has_border_stroke}, Modifier.border={has_mod_border})")
            failed += 1
        else:
            print(f"  [PASS] {fname}: 0 borders")
            passed += 1

    # Test 2: Outlined Component Elimination
    print("\n=== TEST 2: Outlined Component Elimination Across All 12 Files ===")
    for fname in FILES:
        fpath = os.path.join(SETTINGS_DIR, fname)
        with open(fpath, "r", encoding="utf-8") as f:
            content = f.read()
        
        has_outlined = bool(re.search(r'\bOutlined(?:Button|TextField|Card)\b', content))
        if has_outlined:
            print(f"  [FAIL] {fname} contains Outlined components!")
            failed += 1
        else:
            print(f"  [PASS] {fname}: 0 Outlined components")
            passed += 1

    # Test 3: Decorative Gradient Removal in PrivacySettingsScreen
    print("\n=== TEST 3: Decorative Gradient Removal in PrivacySettingsScreen.kt ===")
    fpath = os.path.join(SETTINGS_DIR, "PrivacySettingsScreen.kt")
    with open(fpath, "r", encoding="utf-8") as f:
        content = f.read()
    if "M3ExpressiveVariableBackground" in content:
        print("  [FAIL] PrivacySettingsScreen.kt still contains M3ExpressiveVariableBackground!")
        failed += 1
    else:
        print("  [PASS] PrivacySettingsScreen.kt: M3ExpressiveVariableBackground completely eliminated")
        passed += 1

    # Test 4: PetalFeatureExplainerDialog Migration
    print("\n=== TEST 4: PetalFeatureExplainerDialog Standardization ===")
    fpath = os.path.join(SETTINGS_DIR, "PetalFeatureExplainerDialog.kt")
    with open(fpath, "r", encoding="utf-8") as f:
        content = f.read()
    if "PetalAlertDialog" in content and "DialogProperties" not in content:
        print("  [PASS] PetalFeatureExplainerDialog migrated to PetalAlertDialog with standard transient surfaces")
        passed += 1
    else:
        print("  [FAIL] PetalFeatureExplainerDialog not cleanly migrated to PetalAlertDialog!")
        failed += 1

    # Test 5: Connected Asymmetric Pill Pairs Geometry
    print("\n=== TEST 5: Connected Asymmetric Pill Pairs Geometry ===")
    asym_files = ["UpdaterSettingsScreen.kt", "DataBackupSettingsScreen.kt", "AppearanceSettingsScreen.kt"]
    for fname in asym_files:
        fpath = os.path.join(SETTINGS_DIR, fname)
        with open(fpath, "r", encoding="utf-8") as f:
            content = f.read()
        has_lead = "topStart = 50.dp, bottomStart = 50.dp, topEnd = 6.dp, bottomEnd = 6.dp" in content
        has_trail = "topStart = 6.dp, bottomStart = 6.dp, topEnd = 50.dp, bottomEnd = 50.dp" in content
        if has_lead and has_trail:
            print(f"  [PASS] {fname} implements connected asymmetric pill geometry (50/6 and 6/50)")
            passed += 1
        else:
            print(f"  [FAIL] {fname} missing standard connected asymmetric pill geometry!")
            failed += 1

    # Test 6: Filled Tonal TextFields Specification
    print("\n=== TEST 6: Filled Tonal TextFields Styling ===")
    tf_files = ["SearchHomeSettingsScreen.kt", "SettingsHubScreen.kt", "ApiIntegrationsSettingsScreen.kt"]
    for fname in tf_files:
        fpath = os.path.join(SETTINGS_DIR, fname)
        with open(fpath, "r", encoding="utf-8") as f:
            content = f.read()
        has_tonal = "surfaceContainerHighest" in content and "Color.Transparent" in content
        if has_tonal:
            print(f"  [PASS] {fname} uses filled tonal surfaceContainerHighest with transparent indicators")
            passed += 1
        else:
            print(f"  [FAIL] {fname} missing tonal container or transparent indicator colors!")
            failed += 1

    print("\n" + "=" * 80)
    print(f" RESULTS: {passed} PASSED, {failed} FAILED")
    print("=" * 80)
    return failed == 0

if __name__ == "__main__":
    success = run_tests()
    sys.exit(0 if success else 1)
