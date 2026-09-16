# Petal Browser Development Guidelines & Rules

## 1. Action Over Repetitive File Inspection
- Always take direct practical action to fix identified issues. Avoid repeatedly reading or inspecting the same source files multiple times.
- Implement code edits, fixes, and architectural enhancements directly and test the changes with builds (`./gradlew assembleDebug` or tests).

## 2. GeckoView Session & Navigation Mechanics
- **Navigation & Back History**:
  - `PetalGeckoView` tracks session navigation status via `GeckoSession.NavigationDelegate` (`onCanGoBack`, `onCanGoForward`).
  - GeckoView navigates back using `session.goBack()`. When no back history exists on the active page, return cleanly to the Home surface (`petal://home` or `about:blank`).
  - When the user presses Back on the Home surface with multiple tabs open, close the current tab (`removeAlbum(currentAlbumController)`) rather than quitting immediately.
  - Double-back or exit dialog should only trigger when the user is on the Home surface with only a single tab remaining.
- **System Gesture Exclusions**:
  - Android 10+ (API 29+) edge swipe gestures require explicit clearing of exclusion rects:
    ```kotlin
    fun resetGestureExclusionRects() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                systemGestureExclusionRects = emptyList()
                geckoView.systemGestureExclusionRects = emptyList()
                for (i in 0 until childCount) {
                    getChildAt(i)?.systemGestureExclusionRects = emptyList()
                }
            } catch (_: Throwable) {}
        }
    }
    ```

## 3. Pull-To-Refresh Architecture
- `PullToRefreshFrameLayout` wraps `contentFrame` (`R.id.main_content`).
- **Home & Web Support**:
  - Home page (`petal://home`, `about:blank`) must allow pull-to-refresh when at the top. On release, it reloads/refreshes the home view and shortcuts.
  - Internal overlay screens (`petal://settings`, `petal://downloads`, `petal://history`, `petal://account`) disallow pull-to-refresh to prevent conflicts with native scrolling.
  - Web pages rely on `getPageScrollY() <= 0` and vertical swipe dominance to trigger pull-to-refresh without conflicting with web horizontal swiping.

## 4. Tab Restore Architecture
- Use `PetalTabSessionManager` with SQLite persistence (`TABLE_SESSION`), SharedPreferences backups, and legacy `openTabs` compatibility.
- Ensure all Kotlin/Java interop classes provide `@JvmStatic` or `@JvmOverloads` when consumed by Java classes like `BrowserActivity.java` or `BackupUnit.java`.
