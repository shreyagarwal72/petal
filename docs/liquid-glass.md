# Liquid glass

Enable **Settings → Appearance → Liquid Glass UI**. Under **Liquid Glass Background Layer**,
choose **Glass** for the Stride-style palette pools and contour wallpaper, **Morphs** for
Petal's animated background, or an image. Existing saved background choices are preserved;
Glass is the default when no background choice has been saved.

The implementation follows the local Vyxel and Stride references: one sharp wallpaper layer,
one shared 7 dp frosted layer, and a separate edge-refraction pass for each pane. Cards reuse
the frost instead of blurring the screen individually. Text and icons are drawn after the
effect and remain sharp. Stride's 24/56 dp panel lens, 22/32 percent dark/light tint, contour
palette, and white rim are used. Petal's opacity and sheen controls scale this material.

Shared settings rows, hero/action cards, and headers participate. Browser address and
navigation bars reuse Petal's existing View-backed Stride glass so they sample browser
content rather than the settings wallpaper. Browser bars retain a stronger tint for contrast
over arbitrary websites. The incognito address bar stays solid.

Android 12 supports backdrop blur; Android 13 adds the runtime lens shader. Older versions
use a stronger tint for readability. No new dependencies were added. Vyxel and Stride were
read-only references.

Manual device checks:

- Toggle glass on/off in Appearance; check row widths, click targets, scrolling, and switches.
- Check light/dark themes, Glass/Morphs/image backgrounds, opacity, and sheen changes live.
- Scroll cards over the wallpaper; its position should remain fixed behind each pane.
- Check top/bottom address bars, floating/standard navigation, and incognito.
- Check Android 12 blur and Android 13+ refraction; check the older-device tint fallback.

Original versions of edited files are saved in `build/liquid-glass-originals` because this
workspace has no Git metadata.
