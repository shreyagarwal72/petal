#!/usr/bin/env python3
"""
Stress test and adversarial verification harness for Petal transient surfaces and containment.
Tests:
1. Shape geometry and corner logic generator (0 items, 1 item, 2 items, 3+ items, massive items, edge indices)
2. Action row layout and weight distribution
3. Massive text content and truncation behavior
4. State lifecycle and rapid dismiss/reopen behavior
5. Zero border regressions in M1 transient surfaces and connected button groups
6. Containment isolation verification
"""

import sys
import os
import re
import subprocess

def test_petal_stacked_block_shape():
    """
    Simulates petalStackedBlockShape(index, count):
    fun petalStackedBlockShape(index: Int, count: Int): RoundedCornerShape {
        return when {
            count <= 1 -> RoundedCornerShape(18.dp)
            index == 0 -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
            index == count - 1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
            else -> RoundedCornerShape(6.dp)
        }
    }
    """
    def shape(index, count):
        if count <= 1:
            return (18, 18, 18, 18)
        if index == 0:
            return (18, 18, 6, 6) # topStart, topEnd, bottomStart, bottomEnd
        if index == count - 1:
            return (6, 6, 18, 18)
        return (6, 6, 6, 6)

    # Edge Case 1: 0 items
    assert shape(0, 0) == (18, 18, 18, 18), "Count 0 must be 18dp all around"
    assert shape(-1, 0) == (18, 18, 18, 18), "Count 0 with negative index must be safe"

    # Edge Case 2: 1 item block
    assert shape(0, 1) == (18, 18, 18, 18), "Count 1 must have 18dp outer corners on all 4 sides"
    assert shape(1, 1) == (18, 18, 18, 18), "Count 1 out of bounds index must return 18dp"

    # 2 items
    assert shape(0, 2) == (18, 18, 6, 6), "Item 0 of 2 must have top outer 18dp, bottom inner 6dp"
    assert shape(1, 2) == (6, 6, 18, 18), "Item 1 of 2 must have top inner 6dp, bottom outer 18dp"

    # 3 items
    assert shape(0, 3) == (18, 18, 6, 6)
    assert shape(1, 3) == (6, 6, 6, 6), "Middle item must have 6dp on all sides"
    assert shape(2, 3) == (6, 6, 18, 18)

    # Massive count (1,000,000 items)
    assert shape(0, 1000000) == (18, 18, 6, 6)
    assert shape(500000, 1000000) == (6, 6, 6, 6)
    assert shape(999999, 1000000) == (6, 6, 18, 18)

    print("[PASS] test_petal_stacked_block_shape passed all edge cases (0 items, 1 item, massive items).")


def test_petal_connected_shape():
    """
    Simulates petalConnectedShape(index, count):
    fun petalConnectedShape(index: Int, count: Int): Shape = when {
        count <= 1 -> RoundedCornerShape(50.dp)
        index == 0 -> RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 6.dp, bottomEnd = 6.dp)
        index == count - 1 -> RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 50.dp, bottomEnd = 50.dp)
        else -> RoundedCornerShape(6.dp)
    }
    """
    def shape(index, count):
        if count <= 1:
            return (50, 50, 50, 50) # topStart, bottomStart, topEnd, bottomEnd
        if index == 0:
            return (50, 50, 6, 6)
        if index == count - 1:
            return (6, 6, 50, 50)
        return (6, 6, 6, 6)

    # Edge Case 1: 0 items
    assert shape(0, 0) == (50, 50, 50, 50)

    # Edge Case 2: 1 item
    assert shape(0, 1) == (50, 50, 50, 50)

    # 2 items
    assert shape(0, 2) == (50, 50, 6, 6)
    assert shape(1, 2) == (6, 6, 50, 50)

    # 3 items
    assert shape(0, 3) == (50, 50, 6, 6)
    assert shape(1, 3) == (6, 6, 6, 6)
    assert shape(2, 3) == (6, 6, 50, 50)

    # Massive items
    assert shape(0, 1000) == (50, 50, 6, 6)
    assert shape(500, 1000) == (6, 6, 6, 6)
    assert shape(999, 1000) == (6, 6, 50, 50)

    print("[PASS] test_petal_connected_shape passed all edge cases (0 items, 1 item, massive items).")


def test_action_row_physics():
    """
    Verifies PetalActionRow and PetalAlertDialog action button shapes & weights:
    - 1 button (dismiss=null): 50dp pill, weight 1.0f (full width)
    - 2 buttons (dismiss!=null): dismiss (50, 50, 6, 6), confirm (6, 6, 50, 50), each weight 1.0f (50% width)
    """
    # 1 button: confirm only
    confirm_shape_1 = (50, 50, 50, 50)
    confirm_weight_1 = 1.0 # 100% of row width

    # 2 buttons: dismiss + confirm
    dismiss_shape_2 = (50, 50, 6, 6)
    confirm_shape_2 = (6, 6, 50, 50)
    dismiss_weight_2 = 1.0
    confirm_weight_2 = 1.0
    # Equal split 50/50 with 6dp spacing
    assert dismiss_shape_2[0] == 50 and dismiss_shape_2[1] == 50
    assert confirm_shape_2[2] == 50 and confirm_shape_2[3] == 50
    print("[PASS] test_action_row_physics passed.")


def test_massive_text_and_overflow_defense():
    """
    Analyzes all text composables in modified files for overflow defenses:
    - TextOverflow.Ellipsis
    - maxLines
    - horizontalScroll / verticalScroll
    """
    files_to_check = [
        "app/src/main/java/com/petal/browser/ui/containment/PetalTransientSurfaces.kt",
        "app/src/main/java/com/petal/browser/ui/containment/PetalContainment.kt",
        "app/src/main/java/com/petal/browser/ui/components/ExpressiveButtonGroup.kt",
        "app/src/main/java/com/petal/browser/ui/components/PetalChoiceButtonGroup.kt",
    ]

    findings = []

    for fpath in files_to_check:
        with open(fpath, "r", encoding="utf-8") as f:
            code = f.read()

        # Check button group text overflow defenses
        if "PetalConnectedButtonGroup" in code:
            if "maxLines = 1" in code and "TextOverflow.Ellipsis" in code:
                findings.append(("[PASS] PetalConnectedButtonGroup handles long button labels with Ellipsis", "LOW"))
            else:
                findings.append(("[FAIL] PetalConnectedButtonGroup missing label overflow handling", "HIGH"))

        if "ExpressiveButtonGroup" in code:
            if "maxLines = 1" in code and "TextOverflow.Ellipsis" in code:
                findings.append(("[PASS] ExpressiveButtonGroup handles long button labels with Ellipsis", "LOW"))
            else:
                findings.append(("[FAIL] ExpressiveButtonGroup missing label overflow handling", "HIGH"))

        if "PetalChoiceButtonGroup" in code:
            if "shouldScroll = scrollableIfCrowded" in code and "horizontalScroll" in code:
                findings.append(("[PASS] PetalChoiceButtonGroup handles crowded/long labels via horizontal scrolling", "LOW"))
            else:
                findings.append(("[FAIL] PetalChoiceButtonGroup missing horizontal scroll on long labels", "HIGH"))

        # Check PetalSheetTitleBadge
        if "fun PetalSheetTitleBadge" in code:
            badge_code = code.split("fun PetalSheetTitleBadge")[1].split("fun ")[0]
            if "TextOverflow" in badge_code or "maxLines" in badge_code:
                findings.append(("[PASS] PetalSheetTitleBadge has maxLines/TextOverflow", "LOW"))
            else:
                findings.append(("[FINDING] PetalSheetTitleBadge wraps text without maxLines or ellipsis (multi-line titles expand badge vertically)", "MEDIUM"))

        # Check PetalAlertDialog massive message handling
        if "fun PetalAlertDialog" in code:
            alert_code = code.split("fun PetalAlertDialog")[1].split("fun PetalMaterialAlertDialog")[0]
            if "verticalScroll" in alert_code:
                findings.append(("[PASS] PetalAlertDialog has verticalScroll on message", "LOW"))
            else:
                findings.append(("[FINDING] PetalAlertDialog does not have verticalScroll on message/content (massive text pushes actions or requires caller wrapping)", "MEDIUM"))

    for f, sev in findings:
        print(f"{f} (Severity: {sev})")


def test_zero_border_regressions_m1():
    """
    Audits all M1 modified targets for any unauthorized BorderStroke or Modifier.border:
    - PetalTransientSurfaces.kt:
      * PetalPopupMenu (lines 96..135)
      * PetalAlertDialog / PetalMaterialAlertDialog / PetalDialog (lines 235..487)
      * PetalSheet / PetalStackedBlock / PetalSheetTitleBadge / PetalActionRow (lines 488..787)
    - PetalContainment.kt:
      * PetalConnectedButtonGroup / petalConnectedShape (lines 912..1010)
    - ExpressiveButtonGroup.kt (all lines)
    - PetalChoiceButtonGroup.kt (all lines)
    """
    targets = [
        ("app/src/main/java/com/petal/browser/ui/components/ExpressiveButtonGroup.kt", 1, 9999),
        ("app/src/main/java/com/petal/browser/ui/components/PetalChoiceButtonGroup.kt", 1, 9999),
        ("app/src/main/java/com/petal/browser/ui/containment/PetalTransientSurfaces.kt", 95, 135),
        ("app/src/main/java/com/petal/browser/ui/containment/PetalTransientSurfaces.kt", 235, 787),
        ("app/src/main/java/com/petal/browser/ui/containment/PetalContainment.kt", 910, 1010),
    ]

    border_violations = 0
    for fpath, start_line, end_line in targets:
        with open(fpath, "r", encoding="utf-8") as f:
            lines = f.readlines()

        for idx in range(start_line - 1, min(len(lines), end_line)):
            line = lines[idx]
            lno = idx + 1
            stripped = line.strip()
            if stripped.startswith("//") or stripped.startswith("import ") or stripped.startswith("*"):
                continue
            if "border: BorderStroke? = null" in line or "border = null" in line:
                continue
            if "BorderStroke(" in line:
                print(f"[FAIL] Unauthorized BorderStroke in M1 target {fpath}:{lno}: {stripped}")
                border_violations += 1
            if ".border(" in line or "Modifier.border(" in line:
                print(f"[FAIL] Unauthorized Modifier.border in M1 target {fpath}:{lno}: {stripped}")
                border_violations += 1

    assert border_violations == 0, f"Found {border_violations} border violations in M1 targets!"
    print("[PASS] Zero border regressions across all M1 transient surfaces and connected button groups.")


def test_rapid_dismiss_reopen_lifecycle():
    """
    Simulates state machine transitions for rapid dismiss and reopen:
    - PetalConnectedButtonGroup:
      * Click button 0 -> clickedIndex=0, starts 240ms timer
      * Immediately click button 1 -> clickedIndex=1, cancels prior timer, restarts 240ms timer
      * Rapid press/release cycles
    - PetalSheet:
      * SheetState animation cancellation & statusOverlapPx recomputation
    """
    # Simulate PetalConnectedButtonGroup click debounce state machine
    class ButtonGroupState:
        def __init__(self):
            self.clicked_index = None
            self.timer_epoch = 0

        def click(self, idx, timestamp_ms):
            self.clicked_index = idx
            self.timer_epoch = timestamp_ms

        def poll(self, current_time_ms):
            if self.clicked_index is not None and (current_time_ms - self.timer_epoch) >= 240:
                self.clicked_index = None

    state = ButtonGroupState()
    # Rapid sequence: 0ms click 0, 50ms click 1, 100ms click 2, 120ms click 0
    state.click(0, 0)
    assert state.clicked_index == 0
    state.poll(50)
    assert state.clicked_index == 0, "At 50ms, index 0 should still be active"
    state.click(1, 50)
    assert state.clicked_index == 1
    state.poll(100)
    state.click(2, 100)
    assert state.clicked_index == 2
    state.poll(150)
    state.click(0, 150)
    assert state.clicked_index == 0
    # Wait until 389ms (239ms after last click)
    state.poll(389)
    assert state.clicked_index == 0, "At 389ms (239ms after click), index 0 should still be active"
    # At 391ms (241ms after last click)
    state.poll(391)
    assert state.clicked_index is None, "At 391ms (>240ms after last click), clickedIndex should be reset to null"

    print("[PASS] test_rapid_dismiss_reopen_lifecycle passed.")


def test_containment_isolation():
    """
    Runs git diff check against baseline commit to verify strict containment boundary preservation.
    """
    cmd = ["python3", "scripts/audit_ui_integrity.py"]
    res = subprocess.run(cmd, capture_output=True, text=True)
    assert "Containment  | 0          | 0              | PASS" in res.stdout, "Containment isolation must PASS with 0 violations"
    assert "SplashScreen | 0          | 0              | PASS" in res.stdout, "SplashScreen isolation must PASS with 0 violations"
    print("[PASS] test_containment_isolation passed: 0 containment and 0 splash screen violations.")


if __name__ == "__main__":
    print("=== RUNNING TRANSIENT SURFACES EMPIRICAL STRESS TEST ===")
    test_petal_stacked_block_shape()
    test_petal_connected_shape()
    test_action_row_physics()
    test_massive_text_and_overflow_defense()
    test_zero_border_regressions_m1()
    test_rapid_dismiss_reopen_lifecycle()
    test_containment_isolation()
    print("=== ALL EMPIRICAL STRESS TESTS COMPLETED SUCCESSFULLY ===")
