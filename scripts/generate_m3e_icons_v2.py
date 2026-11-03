import os

DARK_DIR = "/data/data/com.termux/files/home/petal/docs/readme/dark"
LIGHT_DIR = "/data/data/com.termux/files/home/petal/docs/readme/light"

ICONS_24 = {
    "icon-lightning.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.8333)" d="M7 2v11h3v9l7-12h-4l4-8z"/>',
    "icon-phone.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.8333)" d="M17 1.01L7 1c-1.1 0-2 .9-2 2v18c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V3c0-1.1-.9-1.99-2-1.99zM17 19H7V5h10v14z"/>',
    "icon-sparkles.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.8333)" d="M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zm-7.5.5L9 4 6.5 9.5 1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5zM19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25L19 15z"/>',
    "icon-gecko.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.8333)" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/>',
    "icon-shield.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.8333)" d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 10.99h7c-.53 4.12-3.28 7.79-7 8.94V12H5V6.3l7-3.11v8.8z"/>',
    "icon-palette.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.8333)" d="M12 3c-4.97 0-9 4.03-9 9 0 2.12.74 4.07 1.97 5.61L4.35 19.4c-.39.39-.39 1.02 0 1.41.39.39 1.02.39 1.41 0l2.12-2.12C9.44 19.45 10.68 20 12 20c4.97 0 9-4.03 9-9s-4.03-9-9-9zm0 15c-3.31 0-6-2.69-6-6s2.69-6 6-6 6 2.69 6 6-2.69 6-6 6z"/>',
    "icon-lock.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.8333)" d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"/>',
    "icon-download.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.8333)" d="M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM17 13l-5 5-5-5h3V9h4v4h3z"/>',
    "icon-bot.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.8333)" d="M21 6h-2v9H5V6H3c-.55 0-1 .45-1 1v14c0 .55.45 1 1 1h18c.55 0 1-.45 1-1V7c0-.55-.45-1-1-1zm-10 7H9v-2h2v2zm4 0h-2v-2h2v2zM15 1H9c-.55 0-1 .45-1 1v3h8V2c0-.55-.45-1-1-1z"/>',
    "icon-key.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.8333)" d="M12.65 10C11.83 7.67 9.61 6 7 6c-3.31 0-6 2.69-6 6s2.69 6 6 6c2.61 0 4.83-1.67 5.65-4H17v4h4v-4h2v-4H12.65zM7 14c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z"/>',
    "icon-layers.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.8333)" d="M11.99 18.54l-7.37-5.73L3 14.07l9 7 9-7-1.63-1.27-7.38 5.74zM12 16l7.36-5.73L21 9l-9-7-9 7 1.63 1.27L12 16z"/>',
    "icon-rocket.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.8333)" d="M12 2.5s3.5 2.5 3.5 6.5c0 1.83-.83 3.5-2.06 4.67l2.83 2.83c.39.39.39 1.02 0 1.41-.39.39-1.02.39-1.41 0l-2.83-2.83C10.86 16.32 9.17 17.15 7.33 17.15 3.33 17.15.83 13.65.83 13.65s2.5-3.5 6.5-3.5c1.83 0 3.5.83 4.67 2.06l2.83-2.83c.39-.39 1.02-.39 1.41 0 .39.39.39 1.02 0 1.41l-2.83 2.83c1.23 1.17 2.06 2.84 2.06 4.67 0 4-3.5 6.5-3.5 6.5s-3.5-2.5-3.5-6.5c0-1.83.83-3.5 2.06-4.67L7.64 10.8c-.39-.39-.39-1.02 0-1.41.39-.39 1.02-.39 1.41 0l2.83 2.83C13.05 11.05 14.72 10.22 16.56 10.22c4 0 6.5 3.5 6.5 3.5z"/>'
}

def render_icon(path_data, dark=True):
    bg = "#342236" if dark else "#eeddee"
    color = "#fed7ff" if dark else "#4a244e"
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 28 28">
  <rect width="28" height="28" rx="8" fill="{bg}"/>
  {path_data.format(color=color)}
</svg>"""

for fname, pdata in ICONS_24.items():
    with open(os.path.join(DARK_DIR, fname), "w") as f:
        f.write(render_icon(pdata, dark=True))
    with open(os.path.join(LIGHT_DIR, fname), "w") as f:
        f.write(render_icon(pdata, dark=False))

print("Regenerated all heading icons with standard Material 24x24 paths.")
