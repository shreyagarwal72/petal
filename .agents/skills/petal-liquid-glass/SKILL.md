---
name: petal-liquid-glass
description: Use whenever you build, edit, fix or review ANY Compose UI in the Petal Browser Android app that is, or should be, "liquid glass" (sheets, dialogs, popup/overflow menus, top bars, bottom bars, cards, settings pages, full-screen viewers, scanner screens). Triggers on: liquid glass, glass UI, frosted, refraction, blur, translucent, bottom sheet, dropdown menu, popup menu, dialog, ghost panel, flat/opaque card, double border, black gap under status bar. Read this BEFORE touching glass code. It lists the architecture, the exact components to reuse, and the mistakes that keep being repeated.
---

# Petal Liquid Glass

Real liquid glass in Petal is NOT "a semi-transparent color". It is three things working together. If any one is missing, the UI looks flat, dark, or broken.

1. **A backdrop** — something recorded into a `PetalGlassWallpaper` so there is real content to frost and refract.
2. **A glass panel** — `Modifier.liquidGlassChrome(...)` on a clipped shape. It reads the wallpaper, blurs it, applies the lens refraction, rim light and sheen.
3. **A see-through surface** — every `Surface`/`Card`/container color between the panel and the content must be transparent (or `petalGroupSurfaceColor()`), or it paints over the glass.

Always gate on `isLiquidGlassEnabled("sp_liquid_glass_containments")` and keep a normal Material fallback for when glass is off. Never delete the non-glass path.

Platform: real blur needs Android 12+ (API 31), the AGSL lens needs Android 13+ (API 33). Below that, code must still look fine (the helpers already fall back to a tinted color).

Package map (all under `com.petal.browser.ui`):
- `containment/` → `isLiquidGlassEnabled`, `liquidGlassChrome`, `petalGroupSurfaceColor`, `PetalSheet`, `PetalPopupMenu`, `PetalMaterialAlertDialog`, `PetalDialog`, `PetalAlertDialog`, `PetalGroup`, `PetalGroupListRow`, `PetalGroupIconBadge`, `PetalSettingsSection`, `PetalSelectableOptionCard`
- `glass/` → `PetalGlassWallpaper`, `LocalPetalGlassWallpaper`, `LocalPetalGlassSource`, `rememberPetalGlassWallpaper`, `captureGlassWallpaper`, `recordGlassWallpaper`, `liveGlassWallpaper`, `PetalGlassPattern`, `PetalGlassContainer`
- `components/` → `PetalSlider`, `IconSwitch`, `ExpressiveHeader`, `M3ExpressiveVariableBackground`

---

## The 15 mistakes (check every one before you answer)

### 1. Putting the glass on the OUTER modifier of a window or slot component
`ModalBottomSheet(modifier = ...)`, `DropdownMenu(modifier = ...)`, `AlertDialog(modifier = ...)`: the outer modifier sits OUTSIDE the slide-in offset / popup positioning. Glass drawn there appears at the wrong place (a dark ghost panel at the top of the screen, an offset inner rectangle in a menu).
**Fix:** make the component's own container transparent and draw the glass INSIDE its content, on a `Box`/`Column` that wraps the real content. `PetalSheet` and `PetalPopupMenu` already do this. Use them, never raw `ModalBottomSheet` / `DropdownMenu`.

### 2. An opaque `Surface`/`Card` inside the content covering the glass
The sheet is glass but the content root is `Surface(color = surfaceContainerHigh)`, so everything looks flat.
**Fix:** `color = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh`. Inner cards: `color = if (isGlass) petalGroupSurfaceColor() else <normal>` plus `.liquidGlassChrome(shape, enabled = true)`.

### 3. No backdrop, so `liquidGlassChrome` silently does nothing
`liquidGlassChrome` only produces real glass when `LocalPetalGlassWallpaper.current?.ready == true`. The theme provides a wallpaper object but nothing records into it. A screen with glass cards but no recorder renders flat.
**Fix:** give every glass screen/window a recorder (see "Choosing the backdrop"). Do not assume the theme has already done it.

### 4. Putting `captureGlassWallpaper` on a container that holds glass cards
The recorder must be a **background sibling**, never a parent of the glass panels, or glass records itself.
```kotlin
Box {
    Backdrop(modifier = Modifier.matchParentSize())   // records the wallpaper
    Content()                                          // glass cards live here
}
```
Exception: `captureGlassWallpaper` on the page content that glass bars float OVER (e.g. the PDF page list) is correct, because the bars are siblings of that box, not inside it.

### 5. Every window has its own coordinate system
Dialogs, popups and sheets are separate windows. `positionInRoot()` is relative to the window root, so a wallpaper recorded in window A cannot be reused in window B. Each window needs its OWN `rememberPetalGlassWallpaper()` + recorder inside that window, which `PetalSheet`, `PetalPopupMenu` and `PetalGlassContainer` do for you. Never pass a wallpaper from the outer screen into a popup/sheet.

### 6. Recording the wrong source view
`PetalGlassContainer(live = true)` and `PetalSheet` record `R.id.main` (the browser) by default. For a full-screen overlay (PDF viewer, file viewer, About page) the menu/sheet opened on top should refract THAT screen.
**Fix:** at the root of the overlay screen:
```kotlin
val hostView = LocalView.current
CompositionLocalProvider(LocalPetalGlassSource provides hostView) { Screen() }
```
Never record a view that contains the glass being drawn (infinite recursion). Popups/sheets are separate windows, so recording the host view is safe. In-tree bars are not, so use mistake 4's sibling pattern for those.

### 7. Double borders
`OutlinedButton` (border) + `liquidGlassChrome` (its own rim) = two outlines. Same with `Surface(border = ...)`, `FilterChip`, `Card(border = ...)`.
**Fix:** `border = if (isGlass) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)`.

### 8. Shadows and tonal elevation under a translucent surface
A shadow shows through the glass as a dirty smudge.
**Fix in glass mode:** `shadowElevation = 0.dp`, `tonalElevation = 0.dp`. Applies to `Surface`, `DropdownMenu`, `Card`, `AlertDialog`.

### 9. System bar insets outside the glass
`padding(top = statusBarHeight)` on the bar pushes the bar down and leaves a black strip above it. `navigationBarsPadding()` on a sheet's outer modifier lifts the whole sheet and the app shows through underneath.
**Fix:** the glass shape fills to the screen edge; the inset goes INSIDE it: `Modifier.liquidGlassChrome(shape).windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))`. For sheets set `contentWindowInsets = { WindowInsets(0) }` and put `navigationBarsPadding()` on the inner Column.

### 10. Ghosting from a "mostly opaque" full-screen layer
`Color(0xF20B0C10)` (95% alpha) as a screen background lets the previous screen bleed through as faint text.
**Fix:** a full-screen route is fully opaque (`Color(0xFF0B0C10)` / `surface`). Glass is for panels floating on top of content, not for the screen root.

### 11. `BottomSheetDialog` background cleared too early
`findViewById(design_bottom_sheet)` before `setContentView`/`show()` returns null, so the default opaque sheet background stays.
**Fix:** do it in `dialog.setOnShowListener { ... setBackgroundColor(TRANSPARENT) ... }`.

### 12. Camera preview cannot be recorded
`PreviewView` defaults to a `SurfaceView` (PERFORMANCE mode), which cannot be drawn into a canvas.
**Fix:** `implementationMode = PreviewView.ImplementationMode.COMPATIBLE` (TextureView) when glass must refract the live camera. Use `liveGlassWallpaper(wallpaper, previewView)`.

### 13. Uneven or clipped layout
- Sibling cards in a row with different text wrapping get different heights. Give them a fixed `height(84.dp)` (or `IntrinsicSize.Min`), `weight(1f)` columns and `maxLines = 2`.
- Long labels in narrow chips ("Sepia (Eye comfort)") wrap to 3-4 lines. Use full-width rows instead of 3 chips.
- More actions than fit on a phone width: `Modifier.horizontalScroll(rememberScrollState())`, never let them clip off-screen.
- Tall sheet content: `verticalScroll`.

### 14. Using stock Material widgets where Petal has its own
Stock `Slider`, `Switch`, `FilterChip`, `AlertDialog`, `DropdownMenu`, `ModalBottomSheet` look wrong next to glass.
| Instead of | Use |
|---|---|
| `Slider` | `PetalSlider` (pill track + rolling cookie thumb) |
| `Switch` | `IconSwitch(checked, icon, onCheckedChange)` |
| `FilterChip` / radio rows | `PetalSelectableOptionCard` |
| `ModalBottomSheet` | `PetalSheet` |
| `DropdownMenu` / `DropdownMenuItem` | `PetalPopupMenu` / `PetalPopupMenuItem` / `PetalMenuDivider` |
| `AlertDialog` | `PetalMaterialAlertDialog` or `PetalAlertDialog` |
| `Dialog` | `PetalDialog` |
| list rows | `PetalGroup` + `PetalGroupListRow` + `PetalGroupIconBadge` |
| settings blocks | `PetalSettingsSection` |
| page header | `ExpressiveHeader` |

### 15. Claiming it works without building
You cannot see the result. Say what you changed and what to verify on a device. Never write "fixed" or "verified" without a build. If you changed a shared component (`PetalSheet`, `PetalPopupMenu`, `PetalGlassContainer`), say that it affects every screen using it.

---

## Choosing the backdrop

| Situation | Backdrop |
|---|---|
| Bottom sheet | `PetalSheet(...)`. It owns a live backdrop. Pass `ownsGlassBackdrop = false` only if the caller already provides its own. |
| Popup / overflow / dropdown menu | `PetalPopupMenu(...)` (uses `PetalGlassContainer(live = true)`). |
| Dialog | `PetalDialog` / `PetalMaterialAlertDialog` / `PetalAlertDialog`. |
| Full-screen settings/list page | `M3ExpressiveVariableBackground(pageSeed = "unique_page_name")` as the first child, then a transparent `Scaffold(containerColor = Color.Transparent)`. |
| Glass bars floating over scrolling content (viewer, reader) | `Modifier.captureGlassWallpaper(wallpaper)` on the content box (give it a solid background first), `CompositionLocalProvider(LocalPetalGlassWallpaper provides wallpaper)` around the screen. |
| Glass over a live camera | Full-bleed `PreviewView` in COMPATIBLE mode + `liveGlassWallpaper(wallpaper, previewView) { frameTick }`; show `PetalGlassPattern` until the camera is ready. |
| Dialog-like `BottomSheetDialog` hosting a `ComposeView` | `PetalSheetGlassBackdrop(wallpaper, Modifier.matchParentSize())` (internal in `PetalContainment.kt`). |

Live recording needs a per-frame tick:
```kotlin
val frameTick = remember { mutableLongStateOf(0L) }
LaunchedEffect(source) { while (true) withFrameNanos { frameTick.longValue = it } }
Box(Modifier.matchParentSize().liveGlassWallpaper(wallpaper, source) { frameTick.longValue })
```

---

## Templates

### Glass card inside any glass container
```kotlin
val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
val shape = RoundedCornerShape(24.dp)
Surface(
    shape = shape,
    color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
    tonalElevation = if (isGlass) 0.dp else 2.dp,
    modifier = Modifier
        .fillMaxWidth()
        .then(if (isGlass) Modifier.liquidGlassChrome(shape, enabled = true) else Modifier)
) { /* content */ }
```
`petalGroupSurfaceColor()` returns Transparent once the backdrop is ready and a translucent fallback before that. Keep it, do not hardcode alphas.

### Tinted glass button (single rim)
```kotlin
OutlinedButton(
    onClick = onClick,
    shape = shape,
    border = if (isGlass) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    colors = ButtonDefaults.outlinedButtonColors(
        containerColor = if (isGlass) petalGroupSurfaceColor() else Color.Transparent
    ),
    modifier = if (isGlass) Modifier.liquidGlassChrome(shape, enabled = true, sheenIntensity = 0.5f) else Modifier
) { /* content */ }
```
Use `tintColor = MaterialTheme.colorScheme.primaryContainer` on `liquidGlassChrome` for accent buttons, and make the Button container Transparent, not an opaque `primaryContainer`.

### Glass top bar that reaches the status bar
```kotlin
Box(
    Modifier.fillMaxWidth()
        .liquidGlassChrome(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp), enabled = isGlass)
        .background(petalGroupSurfaceColor(), barShape)
        .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
        .padding(horizontal = 12.dp, vertical = 6.dp)
) { /* row */ }
```

### Sheet content
```kotlin
PetalSheet(onDismissRequest = onDismiss) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) { /* PetalSelectableOptionCard, PetalSlider, IconSwitch ... */ }
}
```
Do NOT wrap the content in another opaque `Surface`. Do NOT add `navigationBarsPadding()` to the sheet's modifier (`PetalSheet` already pads inside the glass).

### Popup menu
```kotlin
PetalPopupMenu(expanded = open, onDismissRequest = { open = false }) {
    PetalPopupMenuItem(text = "Share", leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { ... })
    PetalMenuDivider()
}
```

---

## Workflow for every glass task

1. **Find the real host.** Is this composable inside a `ModalBottomSheet`, `DropdownMenu`, `Dialog`, `BottomSheetDialog`, a full-screen overlay, or a normal screen? The answer decides the backdrop (table above).
2. **Read the existing component first** (`PetalSheet`, `PetalPopupMenu`, `PetalGlassContainer`, `liquidGlassChrome`). Reuse it. Do not write a second glass implementation.
3. **Walk the surface chain** from the window down to the text: any non-transparent color, border, shadow or elevation in between breaks the glass (mistakes 2, 7, 8).
4. **Check insets and overflow** (mistakes 9, 13).
5. **Keep the non-glass branch** working.
6. **Check blast radius.** Editing `PetalSheet`, `PetalPopupMenu`, `PetalGlassContainer`, `liquidGlassChrome` or `PetalGlassWallpaper.kt` changes dozens of screens. Say so.
7. **Self-review against the 15 mistakes**, then report: what was wrong, what changed, what to test on a phone. State plainly that it was not built or run.

## Output rules (this user works from a phone)
- Send only the files that changed, each with its path under `app/src/main/java/com/petal/browser/`. A full zip only if explicitly requested.
- No desktop/PC steps. No "open Android Studio" instructions as the only path.
- Keep explanations short and in plain English. Lead with what was wrong, then what you changed.
- If a prompt for another tool is requested, write it as one continuous paragraph with no line breaks.
