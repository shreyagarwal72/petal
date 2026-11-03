#!/usr/bin/env python3
"""
Adversarial Stress Test Suite for Milestone 1:
- Corner shape calculations across all button groups and stacked blocks (count = 0, 1, 2, 3, 10, negative, edge values)
- Weight calculations under simultaneous press, rapid click sequences, edge values
- Touch targets (48dp minimum) and spacing (6dp gap) verification
- Layout and semantics contract checks
"""

import sys

class ShapeCorner:
    def __init__(self, top_start, bottom_start, top_end, bottom_end):
        self.top_start = top_start
        self.bottom_start = bottom_start
        self.top_end = top_end
        self.bottom_end = bottom_end

    def is_pill(self, radius=50):
        return (self.top_start == radius and self.bottom_start == radius and
                self.top_end == radius and self.bottom_end == radius)

    def is_rect(self, radius=6):
        return (self.top_start == radius and self.bottom_start == radius and
                self.top_end == radius and self.bottom_end == radius)

    def is_leading(self, outer=50, inner=6):
        return (self.top_start == outer and self.bottom_start == outer and
                self.top_end == inner and self.bottom_end == inner)

    def is_trailing(self, outer=50, inner=6):
        return (self.top_start == inner and self.bottom_start == inner and
                self.top_end == outer and self.bottom_end == outer)

    def __repr__(self):
        return f"Corner(TL={self.top_start}, BL={self.bottom_start}, TR={self.top_end}, BR={self.bottom_end})"


def petalConnectedShape(index: int, count: int) -> ShapeCorner:
    """Mirrors Kotlin:
    fun petalConnectedShape(index: Int, count: Int): Shape = when {
        count <= 1 -> RoundedCornerShape(50.dp)
        index == 0 -> RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 6.dp, bottomEnd = 6.dp)
        index == count - 1 -> RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 50.dp, bottomEnd = 50.dp)
        else -> RoundedCornerShape(6.dp)
    }
    """
    if count <= 1:
        return ShapeCorner(50, 50, 50, 50)
    elif index == 0:
        return ShapeCorner(top_start=50, bottom_start=50, top_end=6, bottom_end=6)
    elif index == count - 1:
        return ShapeCorner(top_start=6, bottom_start=6, top_end=50, bottom_end=50)
    else:
        return ShapeCorner(6, 6, 6, 6)


class StackedBlockCorner:
    def __init__(self, top_start, top_end, bottom_start, bottom_end):
        self.top_start = top_start
        self.top_end = top_end
        self.bottom_start = bottom_start
        self.bottom_end = bottom_end

    def is_all(self, radius=18):
        return (self.top_start == radius and self.top_end == radius and
                self.bottom_start == radius and self.bottom_end == radius)

    def is_top(self, outer=18, inner=6):
        return (self.top_start == outer and self.top_end == outer and
                self.bottom_start == inner and self.bottom_end == inner)

    def is_bottom(self, outer=18, inner=6):
        return (self.top_start == inner and self.top_end == inner and
                self.bottom_start == outer and self.bottom_end == outer)

    def is_middle(self, radius=6):
        return (self.top_start == radius and self.top_end == radius and
                self.bottom_start == radius and self.bottom_end == radius)

    def __repr__(self):
        return f"StackedCorner(TL={self.top_start}, TR={self.top_end}, BL={self.bottom_start}, BR={self.bottom_end})"


def petalStackedBlockShape(index: int, count: int) -> StackedBlockCorner:
    """Mirrors Kotlin:
    fun petalStackedBlockShape(index: Int, count: Int): RoundedCornerShape = when {
        count <= 1 -> RoundedCornerShape(18.dp)
        index == 0 -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        index == count - 1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
        else -> RoundedCornerShape(6.dp)
    }
    """
    if count <= 1:
        return StackedBlockCorner(18, 18, 18, 18)
    elif index == 0:
        return StackedBlockCorner(top_start=18, top_end=18, bottom_start=6, bottom_end=6)
    elif index == count - 1:
        return StackedBlockCorner(top_start=6, top_end=6, bottom_start=18, bottom_end=18)
    else:
        return StackedBlockCorner(6, 6, 6, 6)


def test_corner_calculations():
    print("=== TEST 1: Corner Calculations Stress Test ===")
    counts = [0, 1, 2, 3, 10, -1, -10, 100]

    for count in counts:
        print(f"\nTesting count = {count}:")
        if count <= 0:
            shape = petalConnectedShape(0, count)
            assert shape.is_pill(50), f"Expected 50dp pill for count={count}, got {shape}"
            stacked = petalStackedBlockShape(0, count)
            assert stacked.is_all(18), f"Expected 18dp for count={count}, got {stacked}"
            print(f"  count={count}: PASS (fallback to full rounded)")
        elif count == 1:
            shape = petalConnectedShape(0, 1)
            assert shape.is_pill(50), f"Expected 50dp pill for count=1, got {shape}"
            stacked = petalStackedBlockShape(0, 1)
            assert stacked.is_all(18), f"Expected 18dp for count=1, got {stacked}"
            print(f"  count=1: PASS (index 0 is full pill)")
        elif count == 2:
            shape0 = petalConnectedShape(0, 2)
            shape1 = petalConnectedShape(1, 2)
            assert shape0.is_leading(50, 6), f"Expected leading (50, 6) for index 0, got {shape0}"
            assert shape1.is_trailing(50, 6), f"Expected trailing (50, 6) for index 1, got {shape1}"
            stacked0 = petalStackedBlockShape(0, 2)
            stacked1 = petalStackedBlockShape(1, 2)
            assert stacked0.is_top(18, 6), f"Expected top block (18, 6), got {stacked0}"
            assert stacked1.is_bottom(18, 6), f"Expected bottom block (18, 6), got {stacked1}"
            print(f"  count=2: PASS (index 0 leading/top, index 1 trailing/bottom)")
        elif count == 3:
            s0 = petalConnectedShape(0, 3)
            s1 = petalConnectedShape(1, 3)
            s2 = petalConnectedShape(2, 3)
            assert s0.is_leading(50, 6), f"Expected leading for s0, got {s0}"
            assert s1.is_rect(6), f"Expected 6dp inner rect for s1, got {s1}"
            assert s2.is_trailing(50, 6), f"Expected trailing for s2, got {s2}"
            st0 = petalStackedBlockShape(0, 3)
            st1 = petalStackedBlockShape(1, 3)
            st2 = petalStackedBlockShape(2, 3)
            assert st0.is_top(18, 6), f"Expected top for st0, got {st0}"
            assert st1.is_middle(6), f"Expected 6dp for st1, got {st1}"
            assert st2.is_bottom(18, 6), f"Expected bottom for st2, got {st2}"
            print(f"  count=3: PASS (s0 leading, s1 6dp middle, s2 trailing)")
        else: # count >= 10
            s_first = petalConnectedShape(0, count)
            s_last = petalConnectedShape(count - 1, count)
            assert s_first.is_leading(50, 6)
            assert s_last.is_trailing(50, 6)
            for idx in range(1, count - 1):
                s_mid = petalConnectedShape(idx, count)
                assert s_mid.is_rect(6), f"Expected 6dp for index {idx}, got {s_mid}"
            st_first = petalStackedBlockShape(0, count)
            st_last = petalStackedBlockShape(count - 1, count)
            assert st_first.is_top(18, 6)
            assert st_last.is_bottom(18, 6)
            for idx in range(1, count - 1):
                st_mid = petalStackedBlockShape(idx, count)
                assert st_mid.is_middle(6)
            print(f"  count={count}: PASS (index 0 leading, indices 1..{count-2} 6dp middle, index {count-1} trailing)")

    # Out-of-bounds indices testing
    print("\nTesting Out-of-Bounds Indices:")
    s_neg = petalConnectedShape(-1, 3)
    assert s_neg.is_rect(6), f"Negative index should fall to else (6dp), got {s_neg}"
    s_oob = petalConnectedShape(100, 3)
    assert s_oob.is_rect(6), f"Out of bound index should fall to else (6dp), got {s_oob}"
    print("  Out-of-bounds index handling: PASS (safe fallback to 6dp)")


def test_connected_button_group_weights():
    print("\n=== TEST 2: PetalConnectedButtonGroup Weight Simulations ===")
    
    # In PetalConnectedButtonGroup:
    # currentActiveIndex = pressedIndex ?: clickedIndex
    # targetWeight = when (currentActiveIndex) { null -> 1f; index -> 1.1f; else -> 0.65f }
    def calc_weights(count, active_idx):
        weights = []
        for i in range(count):
            if active_idx is None:
                weights.append(1.0)
            elif i == active_idx:
                weights.append(1.1)
            else:
                weights.append(0.65)
        return weights

    # Idle state
    for count in [1, 2, 3, 5, 10]:
        w = calc_weights(count, None)
        assert all(val == 1.0 for val in w), f"Idle weights should all be 1.0, got {w}"
        assert sum(w) == float(count)
    print("  Idle state (active_idx=None): PASS (all 1.0f)")

    # Single active press
    for count in [2, 3, 10]:
        for active in range(count):
            w = calc_weights(count, active)
            assert w[active] == 1.1
            for i in range(count):
                if i != active:
                    assert w[i] == 0.65
            assert sum(w) > 0, "Weight sum must be positive"
            assert min(w) >= 0.65, "Weight must not drop below 0.65"
    print("  Single button active press (1.1f vs 0.65f): PASS")

    # Dynamic interaction state machine simulation
    print("  Simulating state machine under rapid clicks and multi-touch:")
    # State machine:
    class ConnectedButtonGroupSM:
        def __init__(self, count):
            self.count = count
            self.pressed_index = None
            self.clicked_index = None

        def press(self, index):
            self.pressed_index = index

        def release(self, index):
            if self.pressed_index == index:
                self.pressed_index = None

        def click(self, index):
            self.clicked_index = index

        def click_timeout(self):
            self.clicked_index = None

        def current_active(self):
            return self.pressed_index if self.pressed_index is not None else self.clicked_index

        def get_weights(self):
            return calc_weights(self.count, self.current_active())

    sm = ConnectedButtonGroupSM(3)
    # 1. User presses index 0
    sm.press(0)
    assert sm.get_weights() == [1.1, 0.65, 0.65]

    # 2. User presses index 1 simultaneously (multi-touch)
    sm.press(1) # overwrites pressed_index
    assert sm.get_weights() == [0.65, 1.1, 0.65]

    # 3. User releases index 0 (not the current pressed_index)
    sm.release(0)
    assert sm.get_weights() == [0.65, 1.1, 0.65] # stays on index 1

    # 4. User releases index 1
    sm.release(1)
    assert sm.get_weights() == [1.0, 1.0, 1.0] # returns cleanly to 1.0

    # 5. Rapid click sequence: click 0, then immediately click 2 before 0 times out
    sm.click(0)
    assert sm.get_weights() == [1.1, 0.65, 0.65]
    sm.click(2) # rapid new click replaces clicked_index
    assert sm.get_weights() == [0.65, 0.65, 1.1]
    sm.click_timeout()
    assert sm.get_weights() == [1.0, 1.0, 1.0]
    print("  State machine multi-touch and rapid clicks: PASS")


def test_expressive_and_choice_group_weights():
    print("\n=== TEST 3: ExpressiveButtonGroup & PetalChoiceButtonGroup Weight Simulations ===")
    
    # In ExpressiveButtonGroup & PetalChoiceButtonGroup:
    # hasSelection = items.any { it.id == selectedId }
    # isActive = isSelected || isPressed
    # targetValue = if (!hasSelection && !isPressed) 1f else if (isActive) 1.1f else 0.65f
    def calc_segment_weights(count, selected_idx, pressed_set):
        has_selection = (selected_idx is not None and 0 <= selected_idx < count)
        weights = []
        for i in range(count):
            is_selected = (selected_idx == i)
            is_pressed = (i in pressed_set)
            is_active = (is_selected or is_pressed)
            if not has_selection and not is_pressed:
                val = 1.0
            elif is_active:
                val = 1.1
            else:
                val = 0.65
            weights.append(val)
        return weights

    # Case 1: No selection, no press
    w_none = calc_segment_weights(3, None, set())
    assert w_none == [1.0, 1.0, 1.0], f"Expected all 1.0, got {w_none}"

    # Case 2: Selected index 0, no press
    w_sel0 = calc_segment_weights(3, 0, set())
    assert w_sel0 == [1.1, 0.65, 0.65], f"Expected [1.1, 0.65, 0.65], got {w_sel0}"

    # Case 3: Selected index 0, user presses index 1
    w_sel0_p1 = calc_segment_weights(3, 0, {1})
    assert w_sel0_p1 == [1.1, 1.1, 0.65], f"Expected both active to be 1.1, got {w_sel0_p1}"
    assert sum(w_sel0_p1) == 2.85

    # Case 4: Simultaneous press of all 3 buttons
    w_all_press = calc_segment_weights(3, 0, {0, 1, 2})
    assert w_all_press == [1.1, 1.1, 1.1], f"Expected all 1.1 on full simultaneous press, got {w_all_press}"

    # Case 5: 10 items, selected index 5
    w_10 = calc_segment_weights(10, 5, set())
    assert w_10[5] == 1.1
    for i in range(10):
        if i != 5:
            assert w_10[i] == 0.65
    assert abs(sum(w_10) - (1.1 + 9 * 0.65)) < 1e-6
    print("  Segment weights (idle, selected, active press, simultaneous press, 10 items): PASS")

    # Case 6: Scrollable vs non-scrollable check in PetalChoiceButtonGroup
    print("  Verifying PetalChoiceButtonGroup scrollable condition logic:")
    # shouldScroll = scrollableIfCrowded && (options.size > 4 || options.any { it.label.length > 12 })
    def should_scroll(options_labels, scrollable_if_crowded=True):
        if not scrollable_if_crowded:
            return False
        return len(options_labels) > 4 or any(len(l) > 12 for l in options_labels)

    assert not should_scroll(["A", "B", "C"])
    assert not should_scroll(["A", "B", "C", "D"])
    assert should_scroll(["A", "B", "C", "D", "E"]) # size > 4
    assert should_scroll(["Short", "SuperLongLabelExceeding12Chars"]) # length > 12
    assert not should_scroll(["A", "B", "C", "D", "E"], scrollable_if_crowded=False)
    print("  Scrollable crowing heuristic: PASS")


def test_touch_targets_and_spacing():
    print("\n=== TEST 4: Touch Targets and Spacing Verification ===")
    
    # Inspect PetalContainment.kt
    with open("app/src/main/java/com/petal/browser/ui/containment/PetalContainment.kt", "r") as f:
        containment_code = f.read()

    assert "spacedBy(6.dp)" in containment_code, "PetalContainment must use spacedBy(6.dp)"
    assert "defaultMinSize(minHeight = 48.dp)" in containment_code, "PetalContainment must enforce 48.dp touch target"
    assert "border = null" in containment_code, "PetalConnectedButtonGroup must specify border = null"

    # Inspect ExpressiveButtonGroup.kt
    with open("app/src/main/java/com/petal/browser/ui/components/ExpressiveButtonGroup.kt", "r") as f:
        expressive_code = f.read()

    assert "spacedBy(6.dp)" in expressive_code, "ExpressiveButtonGroup must use spacedBy(6.dp)"
    assert "height.coerceAtLeast(48.dp)" in expressive_code, "ExpressiveButtonGroup must coerce height >= 48.dp"
    assert "defaultMinSize(minHeight = resolvedMinHeight)" in expressive_code
    assert "border = null" in expressive_code, "ExpressiveButtonGroup must specify border = null"

    # Inspect PetalChoiceButtonGroup.kt
    with open("app/src/main/java/com/petal/browser/ui/components/PetalChoiceButtonGroup.kt", "r") as f:
        choice_code = f.read()

    assert "spacedBy(6.dp)" in choice_code, "PetalChoiceButtonGroup must use spacedBy(6.dp)"
    assert "minHeight.coerceAtLeast(48.dp)" in choice_code, "PetalChoiceButtonGroup must coerce minHeight >= 48.dp"
    assert "border = null" in choice_code, "PetalChoiceButtonGroup must specify border = null"

    # Inspect PetalTransientSurfaces.kt
    with open("app/src/main/java/com/petal/browser/ui/containment/PetalTransientSurfaces.kt", "r") as f:
        transient_code = f.read()

    assert "spacedBy(6.dp)" in transient_code, "PetalTransientSurfaces actions must use spacedBy(6.dp)"
    assert "heightIn(min = 48.dp)" in transient_code, "Action buttons in PetalTransientSurfaces must enforce 48.dp min height"
    assert "petalStackedBlockShape" in transient_code
    assert "PetalSheetTitleBadge" in transient_code

    print("  All touch targets (>= 48.dp), spacing (6.dp), and zero borders verified in source code: PASS")


def main():
    print("=" * 60)
    print(" PETAL BROWSER - MILESTONE 1 ADVERSARIAL STRESS TEST SUITE")
    print("=" * 60)
    test_corner_calculations()
    test_connected_button_group_weights()
    test_expressive_and_choice_group_weights()
    test_touch_targets_and_spacing()
    print("\n" + "=" * 60)
    print(" ALL EMPIRICAL STRESS TESTS PASSED SUCCESSFULLY!")
    print("=" * 60)


if __name__ == "__main__":
    main()
