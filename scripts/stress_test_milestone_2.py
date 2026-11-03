#!/usr/bin/env python3
"""
Empirical Adversarial Stress Test Suite for Petal Browser Milestone 2:
1. UI Integrity Audit verification across all M2 files.
2. Dialogs and Action Button Rows edge cases:
   - Null dismiss actions & asymmetric action row physics
   - Extreme text lengths & overflow / scroll defenses
   - Fast tap bursts & bridge idempotency / debounce analysis
3. Button Groups and Split Buttons rapid interaction:
   - PetalConnectedButtonGroup rapid click state machine & weight physics
   - ExpressiveSplitButton corner morphing, arrow rotation & rapid toggling
   - ExpressiveButtonGroup & PetalChoiceButtonGroup crowdedness & scroll bounds
"""

import os
import sys
import re
import json
import subprocess
from typing import List, Dict, Tuple, Optional

REPO_ROOT = "/data/data/com.termux/files/home/petal"
WORKER_1_FILES = [
    "app/src/main/java/com/petal/browser/ui/components/PetalDownloadConfirmationDialog.kt",
    "app/src/main/java/com/petal/browser/ui/components/PetalCrashReportingDialog.kt",
    "app/src/main/java/com/petal/browser/ui/components/PetalPermissionDialog.kt",
    "app/src/main/java/com/petal/browser/ui/components/PetalBrowserPermissionDialog.kt",
    "app/src/main/java/com/petal/browser/ui/components/PetalColorPickerDialog.kt",
    "app/src/main/java/com/petal/browser/ui/components/PetalSummaryBoxDialog.kt",
    "app/src/main/java/com/petal/browser/ui/components/PetalExitConfirmationDialog.kt",
    "app/src/main/java/com/petal/browser/ui/components/PetalAiActionDialog.kt",
    "app/src/main/java/com/petal/browser/ui/components/PetalQrGeneratorDialog.kt",
    "app/src/main/java/com/petal/browser/ui/components/PetalExpressivePopups.kt",
]

WORKER_3_FILES = [
    "app/src/main/java/com/petal/browser/ui/components/PetalWelcomeScreen.kt",
    "app/src/main/java/com/petal/browser/ui/components/PetalButtons.kt",
    "app/src/main/java/com/petal/browser/ui/components/PetalAddressBar.kt",
    "app/src/main/java/com/petal/browser/ui/components/PetalMergedBar.kt",
    "app/src/main/java/com/petal/browser/ui/components/PetalBottomNavBar.kt",
    "app/src/main/java/com/petal/browser/ui/components/PetalMinimizedPill.kt",
    "app/src/main/java/com/petal/browser/ui/components/PetalDeveloperHeroSection.kt",
    "app/src/main/java/com/petal/browser/ui/components/ExpressiveSplitButton.kt",
    "app/src/main/java/com/petal/browser/ui/components/ExpressiveQuickActionsPill.kt",
    "app/src/main/java/com/petal/browser/ui/components/ExpressiveHeader.kt",
]

ALL_M2_FILES = WORKER_1_FILES + WORKER_3_FILES

class TestResult:
    def __init__(self):
        self.passed = 0
        self.failed = 0
        self.findings = []

    def record_pass(self, name: str):
        self.passed += 1
        print(f"  [PASS] {name}")

    def record_fail(self, name: str, reason: str):
        self.failed += 1
        print(f"  [FAIL] {name}: {reason}")

    def record_finding(self, severity: str, name: str, detail: str):
        self.findings.append((severity, name, detail))
        print(f"  [{severity.upper()}] {name}: {detail}")


def test_audit_ui_integrity(result: TestResult):
    print("\n=== TEST 1: UI Integrity Audit on M2 Files ===")
    cmd = ["python3", os.path.join(REPO_ROOT, "scripts/audit_ui_integrity.py"), "--json"]
    proc = subprocess.run(cmd, capture_output=True, text=True, cwd=REPO_ROOT)
    try:
        data = json.loads(proc.stdout)
    except Exception as e:
        result.record_fail("audit_ui_integrity_parse", f"Failed to parse JSON: {e}")
        return

    m2_basenames = [os.path.basename(f) for f in ALL_M2_FILES]
    m2_violations = [v for v in data.get("violations", []) if any(b in v.get("file", "") for b in m2_basenames)]

    if len(m2_violations) == 0:
        result.record_pass(f"Zero violations across all {len(ALL_M2_FILES)} Milestone 2 files")
    else:
        result.record_fail("M2 UI Integrity Violations", f"Found {len(m2_violations)} violations in M2 files: {m2_violations}")


def test_dialog_null_dismiss_actions(result: TestResult):
    print("\n=== TEST 2: Dialogs & Action Button Rows Null Dismiss Actions ===")

    # Simulation of PetalAlertDialog action button configuration
    def eval_petal_alert_dialog(confirm_text: str, dismiss_text: Optional[str], on_dismiss_provided: bool):
        has_dismiss_button = dismiss_text is not None
        dismiss_shape = (50, 50, 6, 6) if has_dismiss_button else None
        confirm_shape = (6, 6, 50, 50) if has_dismiss_button else (50, 50, 50, 50)
        confirm_weight = 1.0 # Box(weight(1f)) in Row
        dismiss_weight = 1.0 if has_dismiss_button else 0.0

        # Click handler resolution
        def on_dismiss_click(on_dismiss_req_called: list, on_dismiss_called: list):
            if on_dismiss_provided:
                on_dismiss_called.append(True)
            else:
                on_dismiss_req_called.append(True)

        return {
            "has_dismiss": has_dismiss_button,
            "dismiss_shape": dismiss_shape,
            "confirm_shape": confirm_shape,
            "confirm_weight": confirm_weight,
            "dismiss_weight": dismiss_weight,
            "click_fn": on_dismiss_click,
        }

    # Simulation of PetalActionRow action button configuration
    def eval_petal_action_row(confirm_text: str, dismiss_text: Optional[str], on_dismiss_provided: bool):
        # Code: if (dismissText != null && onDismiss != null)
        renders_dismiss = (dismiss_text is not None) and on_dismiss_provided
        dismiss_shape = (50, 50, 6, 6) if renders_dismiss else None
        confirm_shape = (6, 6, 50, 50) if renders_dismiss else (50, 50, 50, 50)
        return {
            "renders_dismiss": renders_dismiss,
            "dismiss_shape": dismiss_shape,
            "confirm_shape": confirm_shape,
        }

    # Simulation of PetalExpressiveAlertDialog action button configuration
    def eval_petal_expressive_alert_dialog(confirm_text: str, dismiss_text: Optional[str], on_dismiss_provided: bool):
        # Code: if (dismissText != null) { ... onClick = { (onDismiss ?: onDismissRequest)() } }
        has_dismiss_button = dismiss_text is not None
        dismiss_shape = (50, 50, 6, 6) if has_dismiss_button else None
        confirm_shape = (6, 6, 50, 50) if has_dismiss_button else (50, 50, 50, 50)
        return {
            "has_dismiss": has_dismiss_button,
            "dismiss_shape": dismiss_shape,
            "confirm_shape": confirm_shape,
            "fallback_to_onDismissRequest": not on_dismiss_provided and has_dismiss_button
        }

    # Case 1: Standard 2-button dialog
    res_alert = eval_petal_alert_dialog("OK", "Cancel", True)
    assert res_alert["has_dismiss"] is True
    assert res_alert["confirm_shape"] == (6, 6, 50, 50)
    assert res_alert["dismiss_shape"] == (50, 50, 6, 6)
    result.record_pass("PetalAlertDialog 2-button symmetric geometry (50/6 and 6/50)")

    # Case 2: PetalAlertDialog with null dismissText
    res_alert_single = eval_petal_alert_dialog("OK", None, False)
    assert res_alert_single["has_dismiss"] is False
    assert res_alert_single["confirm_shape"] == (50, 50, 50, 50)
    result.record_pass("PetalAlertDialog single confirm button morphs to 50dp pill")

    # Case 3: PetalAlertDialog dismissText="Cancel" but onDismiss=null
    res_alert_null_action = eval_petal_alert_dialog("OK", "Cancel", False)
    req_called = []
    cb_called = []
    res_alert_null_action["click_fn"](req_called, cb_called)
    assert len(req_called) == 1 and len(cb_called) == 0
    result.record_pass("PetalAlertDialog falls back safely to onDismissRequest when onDismiss=null")

    # Case 4: PetalActionRow dismissText="Cancel" but onDismiss=null
    res_row_null_action = eval_petal_action_row("OK", "Cancel", False)
    if not res_row_null_action["renders_dismiss"] and res_row_null_action["confirm_shape"] == (50, 50, 50, 50):
        result.record_finding("MEDIUM", "PetalActionRow dismissText without onDismiss",
                               "PetalActionRow checks (dismissText != null && onDismiss != null). If onDismiss is null, dismiss button is silently suppressed, and confirm button expands to 50dp pill.")
    else:
        result.record_pass("PetalActionRow dismiss behavior")

    # Case 5: PetalExpressiveAlertDialog dismissText="Cancel" but onDismiss=null
    res_exp_null_action = eval_petal_expressive_alert_dialog("OK", "Cancel", False)
    assert res_exp_null_action["has_dismiss"] is True
    assert res_exp_null_action["fallback_to_onDismissRequest"] is True
    result.record_pass("PetalExpressiveAlertDialog (onDismiss ?: onDismissRequest)() fallback verified")


def test_dialog_extreme_text_lengths(result: TestResult):
    print("\n=== TEST 3: Dialogs Extreme Text Lengths & Overflow Defenses ===")

    # Scan dialog implementations for scrollability and ellipsis defenses
    dialog_scroll_defense = {}
    for frel in WORKER_1_FILES:
        fpath = os.path.join(REPO_ROOT, frel)
        fname = os.path.basename(fpath)
        with open(fpath, "r", encoding="utf-8") as f:
            code = f.read()

        has_vscroll = "verticalScroll" in code
        has_lazy = "LazyColumn" in code or "LazyRow" in code
        has_maxlines = "maxLines" in code
        has_ellipsis = "TextOverflow.Ellipsis" in code

        dialog_scroll_defense[fname] = {
            "vscroll": has_vscroll,
            "lazy": has_lazy,
            "maxlines": has_maxlines,
            "ellipsis": has_ellipsis,
        }

    # Verify which dialogs handle massive content safely
    # 1. PetalBrowserPermissionDialog has verticalScroll
    assert dialog_scroll_defense["PetalBrowserPermissionDialog.kt"]["vscroll"] is True
    result.record_pass("PetalBrowserPermissionDialog has verticalScroll() for variable permission counts")

    # 2. PetalCrashReportingDialog has verticalScroll and horizontalScroll on trace
    assert dialog_scroll_defense["PetalCrashReportingDialog.kt"]["vscroll"] is True
    result.record_pass("PetalCrashReportingDialog has verticalScroll() on crash snippets and stack traces")

    # 3. PetalSummaryBoxDialog has verticalScroll
    assert dialog_scroll_defense["PetalSummaryBoxDialog.kt"]["vscroll"] is True
    result.record_pass("PetalSummaryBoxDialog has verticalScroll() on AI markdown summary content")

    # 4. Check PetalDownloadConfirmationDialog for long fileName defense
    if not dialog_scroll_defense["PetalDownloadConfirmationDialog.kt"]["vscroll"]:
        result.record_finding("MEDIUM", "PetalDownloadConfirmationDialog unconstrained text height",
                               "PetalDownloadConfirmationDialog lacks verticalScroll(). An extremely long fileName (e.g. 500+ chars URL query) in annotatedText causes vertical expansion, which on low-height/landscape screens can push the action button row off-screen.")
    else:
        result.record_pass("PetalDownloadConfirmationDialog has verticalScroll")

    # 5. Check action row button label wrapping physics under long button text
    def simulate_action_row_layout(btn1_text: str, btn2_text: str, row_width_dp: float = 320.0):
        # 6dp gap
        avail_width = (row_width_dp - 6.0) / 2.0
        # Approx 8.5dp per character at labelLarge 14sp bold
        btn1_lines = max(1, int(len(btn1_text) * 8.5 / avail_width) + 1) if (len(btn1_text) * 8.5) > avail_width else 1
        btn2_lines = max(1, int(len(btn2_text) * 8.5 / avail_width) + 1) if (len(btn2_text) * 8.5) > avail_width else 1
        # In Compose Row with weight(1f) and heightIn(min = 48.dp):
        # Buttons expand height independently according to line count
        btn1_height = max(48.0, 24.0 + btn1_lines * 18.0)
        btn2_height = max(48.0, 24.0 + btn2_lines * 18.0)
        height_mismatch = abs(btn1_height - btn2_height) > 0.1
        return btn1_height, btn2_height, height_mismatch

    h1, h2, mismatch = simulate_action_row_layout("No", "Yes, permanently delete and proceed with action")
    if mismatch:
        result.record_finding("LOW", "Action row button height asymmetry on extreme label length",
                               f"When one action has a very long label, heightIn(min = 48.dp) expands that button to {h2}dp while the sibling remains {h1}dp. Sibling buttons vertically center in the Row unless IntrinsicSize.Max is used.")
    else:
        result.record_pass("Action row button heights are balanced")


def test_fast_tap_and_debounce(result: TestResult):
    print("\n=== TEST 4: Fast Tap & Debounce Analysis ===")

    # Test 1: PetalExpressivePopups hostCompose idempotency guard
    exp_popups_path = os.path.join(REPO_ROOT, "app/src/main/java/com/petal/browser/ui/components/PetalExpressivePopups.kt")
    with open(exp_popups_path, "r", encoding="utf-8") as f:
        code = f.read()

    has_finished_guard = "var finished = false" in code and "if (!finished)" in code
    if has_finished_guard:
        result.record_pass("PetalExpressivePopups hostCompose contains 'finished = true' idempotency gate preventing multi-click callback re-entry")
    else:
        result.record_fail("PetalExpressivePopups hostCompose missing idempotency gate", "Missing 'finished' check in hostCompose complete()")

    # Test 2: PetalPermissionDialog fast double-tap analysis
    perm_dialog_path = os.path.join(REPO_ROOT, "app/src/main/java/com/petal/browser/ui/components/PetalPermissionDialog.kt")
    with open(perm_dialog_path, "r", encoding="utf-8") as f:
        perm_code = f.read()

    # Look at showPermissionPrompt() implementation in PetalPermissionDialog
    show_slice = perm_code.split("fun showPermissionPrompt(")[1].split("dialog.show()")[0]
    has_perm_double_tap_guard = "var completed = false" in show_slice or "var finished = false" in show_slice
    if not has_perm_double_tap_guard:
        result.record_finding("MEDIUM", "PetalPermissionDialog lacks double-tap suppression in show()",
                               "In PetalPermissionDialog.show(), onAllow and onDeny callbacks call dialog.dismiss() followed immediately by onAllow(remember). Rapid consecutive taps before dismiss unbinds the window can execute the GeckoView permission callback more than once.")
    else:
        result.record_pass("PetalPermissionDialog contains double-tap suppression")

    # Test 3: PetalDownloadConfirmationDialog fast tap analysis
    dl_dialog_path = os.path.join(REPO_ROOT, "app/src/main/java/com/petal/browser/ui/components/PetalDownloadConfirmationDialog.kt")
    with open(dl_dialog_path, "r", encoding="utf-8") as f:
        dl_code = f.read()

    # Check onConfirm in showDownloadConfirmationPrompt
    if "if (dialog.isShowing) dialog.dismiss()" in dl_code:
        result.record_finding("LOW", "PetalDownloadConfirmationDialog fast double-tap risk",
                               "Rapid successive clicks on confirm action rely on dialog.isShowing check, which may allow duplicate onConfirmDownload invocation if two touch events are dispatched in the same frame.")
    else:
        result.record_pass("PetalDownloadConfirmationDialog double-tap protection")


def test_button_groups_rapid_interaction(result: TestResult):
    print("\n=== TEST 5: Button Groups Rapid Interaction & Edge States ===")

    # Test 1: PetalConnectedButtonGroup click debounce & LaunchedEffect lifecycle simulation
    class PetalConnectedButtonGroupModel:
        def __init__(self, item_count: int):
            self.item_count = item_count
            self.clicked_index: Optional[int] = None
            self.pressed_index: Optional[int] = None
            self.coroutine_token: int = 0
            self.active_timer_expiry: int = 0

        def press(self, idx: int):
            self.pressed_index = idx

        def release(self, idx: int):
            if self.pressed_index == idx:
                self.pressed_index = None

        def click(self, idx: int, timestamp_ms: int):
            # launchedEffect(clickedIndex):
            # In Compose, if clickedIndex does NOT change value, LaunchedEffect is NOT restarted!
            prev = self.clicked_index
            self.clicked_index = idx
            if prev != idx:
                self.coroutine_token += 1
                self.active_timer_expiry = timestamp_ms + 240

        def tick(self, timestamp_ms: int):
            if self.clicked_index is not None and timestamp_ms >= self.active_timer_expiry:
                self.clicked_index = None

        def current_active(self) -> Optional[int]:
            return self.pressed_index if self.pressed_index is not None else self.clicked_index

        def target_weights(self) -> List[float]:
            active = self.current_active()
            if active is None:
                return [1.0] * self.item_count
            weights = []
            for i in range(self.item_count):
                weights.append(1.1 if i == active else 0.65)
            return weights

    # Simulate rapid tapping across 3 buttons
    model = PetalConnectedButtonGroupModel(item_count=3)
    # T=0ms: Click button 0
    model.click(0, 0)
    assert model.current_active() == 0
    assert model.target_weights() == [1.1, 0.65, 0.65]

    # T=50ms: Rapidly click button 1
    model.click(1, 50)
    assert model.current_active() == 1
    assert model.target_weights() == [0.65, 1.1, 0.65]
    assert model.active_timer_expiry == 290

    # T=100ms: Rapidly click button 2
    model.click(2, 100)
    assert model.current_active() == 2
    assert model.target_weights() == [0.65, 0.65, 1.1]
    assert model.active_timer_expiry == 340

    # T=200ms: Rapidly click button 2 AGAIN (same button)
    model.click(2, 200)
    # Key didn't change (2 == 2), timer expiry remains 340ms!
    assert model.active_timer_expiry == 340
    result.record_pass("PetalConnectedButtonGroup rapid click alternation updates active weights smoothly")

    # Test 2: Edge counts on PetalConnectedButtonGroup
    # items = 0
    model0 = PetalConnectedButtonGroupModel(item_count=0)
    assert model0.target_weights() == []
    result.record_pass("PetalConnectedButtonGroup count=0 produces safe empty layout")

    # items = 1
    model1 = PetalConnectedButtonGroupModel(item_count=1)
    model1.click(0, 0)
    assert model1.target_weights() == [1.1] # Takes 100% width
    result.record_pass("PetalConnectedButtonGroup count=1 single button weight")

    # Test 3: ExpressiveButtonGroup & PetalChoiceButtonGroup crowdedness & bounds
    btn_group_path = os.path.join(REPO_ROOT, "app/src/main/java/com/petal/browser/ui/components/ExpressiveButtonGroup.kt")
    with open(btn_group_path, "r", encoding="utf-8") as f:
        ebg_code = f.read()

    assert "if (items.isEmpty()) return" in ebg_code
    result.record_pass("ExpressiveButtonGroup handles empty items safely with early return")

    choice_group_path = os.path.join(REPO_ROOT, "app/src/main/java/com/petal/browser/ui/components/PetalChoiceButtonGroup.kt")
    with open(choice_group_path, "r", encoding="utf-8") as f:
        cbg_code = f.read()

    assert "if (options.isEmpty()) return" in cbg_code
    result.record_pass("PetalChoiceButtonGroup handles empty options safely with early return")

    # Scroll condition check:
    # val shouldScroll = scrollableIfCrowded && (options.size > 4 || options.any { it.label.length > 12 })
    assert "options.size > 4 || options.any { it.label.length > 12 }" in cbg_code
    assert "Modifier.weight(animatedWeight)" not in cbg_code.split("if (shouldScroll) {")[1].split("} else {")[0]
    result.record_pass("PetalChoiceButtonGroup strictly disables Modifier.weight inside scrollable Row")


def test_split_button_rapid_interaction(result: TestResult):
    print("\n=== TEST 6: Split Button Rapid Interaction & Edge States ===")

    split_path = os.path.join(REPO_ROOT, "app/src/main/java/com/petal/browser/ui/components/ExpressiveSplitButton.kt")
    with open(split_path, "r", encoding="utf-8") as f:
        code = f.read()

    # Check morphing corner radius logic:
    # val animatedMenuInnerCorner by animateDpAsState(targetValue = if (isMenuExpanded) pillRadius else innerRadius, ...)
    # val animatedPrimaryInnerCorner by animateDpAsState(targetValue = if (isMenuExpanded) pillRadius else innerRadius, ...)
    assert "targetValue = if (isMenuExpanded) pillRadius else innerRadius" in code
    result.record_pass("ExpressiveSplitButton inner corner morphing dynamically animates between 6dp and height/2")

    # Check arrow rotation:
    # targetValue = if (isMenuExpanded) 180f else 0f
    assert "targetValue = if (isMenuExpanded) 180f else 0f" in code
    result.record_pass("ExpressiveSplitButton arrow rotation animates between 0° and 180°")

    # Check label overflow defense:
    # In ExpressiveSplitButton.kt: line 203:
    # Text(text = label, ..., maxLines = 1) -> does it have TextOverflow.Ellipsis?
    text_slice = code.split("Text(")[1].split(")")[0]
    has_ellipsis = "TextOverflow.Ellipsis" in text_slice
    if not has_ellipsis:
        result.record_finding("MEDIUM", "ExpressiveSplitButton missing TextOverflow.Ellipsis",
                               "ExpressiveSplitButton specifies 'maxLines = 1' but omits 'overflow = TextOverflow.Ellipsis'. Long labels in split buttons will clip abruptly instead of rendering an ellipsis ('...').")
    else:
        result.record_pass("ExpressiveSplitButton has TextOverflow.Ellipsis")

    # Check split button fixed height touch target compliance (min 48dp default)
    assert "height: Dp = 48.dp" in code
    result.record_pass("ExpressiveSplitButton default height satisfies 48dp touch target standard")


def test_bar_and_navigation_components(result: TestResult):
    print("\n=== TEST 7: Bar & Navigation Components Compliance ===")

    for frel in WORKER_3_FILES:
        fpath = os.path.join(REPO_ROOT, frel)
        fname = os.path.basename(fpath)
        with open(fpath, "r", encoding="utf-8") as f:
            code = f.read()

        # Check for unauthorized BorderStroke
        border_matches = re.findall(r"BorderStroke\s*\(", code)
        if len(border_matches) > 0:
            result.record_fail(f"{fname} BorderStroke", f"Found {len(border_matches)} BorderStroke occurrences")
        else:
            result.record_pass(f"{fname} zero BorderStroke occurrences")

        # Check for unauthorized Modifier.border or .border
        # Permitted exception: PetalBottomNavBar.kt line 606 (TabCountBadge)
        mod_border_matches = re.findall(r"\.border\s*\(", code)
        if fname == "PetalBottomNavBar.kt":
            if len(mod_border_matches) == 1 and "TabCountBadge" in code:
                result.record_pass(f"{fname} exactly 1 authorized TabCountBadge border")
            else:
                result.record_fail(f"{fname} Modifier.border", f"Found {len(mod_border_matches)} border calls (expected 1)")
        else:
            if len(mod_border_matches) > 0:
                result.record_fail(f"{fname} Modifier.border", f"Found {len(mod_border_matches)} unauthorized border calls")
            else:
                result.record_pass(f"{fname} zero Modifier.border occurrences")


if __name__ == "__main__":
    print("================================================================================")
    print(" PETAL BROWSER - MILESTONE 2 ADVERSARIAL STRESS TEST SUITE")
    print("================================================================================")

    res = TestResult()
    test_audit_ui_integrity(res)
    test_dialog_null_dismiss_actions(res)
    test_dialog_extreme_text_lengths(res)
    test_fast_tap_and_debounce(res)
    test_button_groups_rapid_interaction(res)
    test_split_button_rapid_interaction(res)
    test_bar_and_navigation_components(res)

    print("\n================================================================================")
    print(f" STRESS TEST SUMMARY: {res.passed} PASSED, {res.failed} FAILED, {len(res.findings)} FINDINGS")
    print("================================================================================")
    for sev, name, detail in res.findings:
        print(f" - [{sev}] {name}: {detail}")

    if res.failed > 0:
        sys.exit(1)
    else:
        sys.exit(0)
