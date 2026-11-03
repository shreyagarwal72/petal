import os

DARK_DIR = "/data/data/com.termux/files/home/petal/docs/readme/dark"
LIGHT_DIR = "/data/data/com.termux/files/home/petal/docs/readme/light"

os.makedirs(DARK_DIR, exist_ok=True)
os.makedirs(LIGHT_DIR, exist_ok=True)

# 28x28 M3 Expressive Icon Chips (viewBox 0 0 28 28)
# Dark: Container #392839, Icon #fed7ff, Shape rx=8 or squircle
# Light: Container #eed9ee, Icon #3e1e42, Shape rx=8 or squircle

ICONS = {
    "icon-lightning.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.0208)" d="m360-80 80-280H240l280-520v360h160L360-80Z"/>',
    "icon-phone.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.0208)" d="M280-80q-33 0-56.5-23.5T200-160v-640q0-33 23.5-56.5T280-880h400q33 0 56.5 23.5T760-800v640q0 33-23.5 56.5T680-80H280Zm0-80h400v-640H280v640Zm0 0v-640 640Z"/>',
    "icon-sparkles.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.0208)" d="m320-280-40-120-120-40 120-40 40-120 40 120 120 40-120 40-40 120ZM520-40l-27-80-80-27 80-27 27-80 27 80 80 27-80 27-27 80ZM640-520l-40-120-120-40 120-40 40-120 40 120 120 40-120 40-40 120Z"/>',
    "icon-gecko.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.0208)" d="M480-80q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm-40-82v-78q-33 0-56.5-23.5T360-320v-40L168-552q-3 18-5.5 36t-2.5 36q0 121 79.5 212T440-162Zm276-102q28-35 44-76.5t18-87.5q0-52-15.5-99T718-614L600-496v56q0 33-23.5 56.5T520-360h-80v80h80q23 0 42.5 11.5T594-236l122-28Z"/>',
    "icon-shield.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.0208)" d="M480-80q-139-35-229.5-159.5T160-516v-244l320-120 320 120v244q0 152-90.5 276.5T480-80Zm0-84q104-33 172-132t68-220v-189l-240-90-240 90v189q0 121 68 220t172 132Zm0-316Z"/>',
    "icon-palette.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.0208)" d="M480-80q-82 0-155-31.5t-127.5-86Q143-252 111.5-325T80-480q0-83 31.5-155.5t86-127Q252-817 325-848.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 73-45 117.5T720-318q-31 0-53-15.5T631-360q-12-16-29.5-25t-39.5-9h-82q-17 0-28.5 11.5T440-354v94q0 50-35 85t-85 35h-40q13 30 38.5 45t56.5 15h40q17 0 28.5 11.5T460-40q0 17-11.5 28.5T420 0h-40q-41 0-77-16t-63-44ZM280-600q25 0 42.5-17.5T340-660q0-25-17.5-42.5T280-720q-25 0-42.5 17.5T220-660q0 25 17.5 42.5T280-600Zm140-120q25 0 42.5-17.5T480-780q0-25-17.5-42.5T420-840q-25 0-42.5 17.5T360-780q0 25 17.5 42.5T420-720Zm140 120q25 0 42.5-17.5T620-660q0-25-17.5-42.5T560-720q-25 0-42.5 17.5T500-660q0 25 17.5 42.5T560-600Zm140 140q25 0 42.5-17.5T760-520q0-25-17.5-42.5T700-580q-25 0-42.5 17.5T640-520q0 25 17.5 42.5T700-460Z"/>',
    "icon-lock.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.0208)" d="M240-80q-33 0-56.5-23.5T160-160v-400q0-33 23.5-56.5T240-640h40v-80q0-83 58.5-141.5T480-920q83 0 141.5 58.5T680-720v80h40q33 0 56.5 23.5T800-560v400q0 33-23.5 56.5T720-80H240Zm240-200q33 0 56.5-23.5T560-360q0-33-23.5-56.5T480-440q-33 0-56.5 23.5T400-360q0 33 23.5 56.5T480-280ZM360-640h240v-80q0-50-35-85t-85-35q-50 0-85 35t-35 85v80Z"/>',
    "icon-download.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.0208)" d="M480-320 280-520l56-58 104 104v-326h80v326l104-104 56 58-200 200ZM240-160q-33 0-56.5-23.5T160-240v-120h80v120h480v-120h80v120q0 33-23.5 56.5T720-160H240Z"/>',
    "icon-bot.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.0208)" d="M160-160q-33 0-56.5-23.5T80-240v-360q0-33 23.5-56.5T160-680h80v-120q0-33 23.5-56.5T320-880h320q33 0 56.5 23.5T720-800v120h80q33 0 56.5 23.5T880-600v360q0 33-23.5 56.5T800-160H160Zm160-80h320v-80H320v80Zm-80-160h480v-200H240v200Zm80-280h320v-120H320v120Zm-20 180q25 0 42.5-17.5T360-560q0-25-17.5-42.5T300-620q-25 0-42.5 17.5T240-560q0 25 17.5 42.5T300-500Zm360 0q25 0 42.5-17.5T720-560q0-25-17.5-42.5T660-620q-25 0-42.5 17.5T600-560q0 25 17.5 42.5T660-500Z"/>',
    "icon-key.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.0208)" d="M280-400q-33 0-56.5-23.5T200-480q0-33 23.5-56.5T280-560q33 0 56.5 23.5T360-480q0 33-23.5 56.5T280-400Zm0 160q-100 0-170-70T40-480q0-100 70-170t170-70q67 0 121.5 33t86.5 87h272l80 80-60 60-60-60-60 60-60-60-72 72q-32 54-86.5 87T280-240Z"/>',
    "icon-layers.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.0208)" d="M480-80 120-280l360-200 360 200L480-80Zm0-240L216-440l264-146 264 146-264 120Zm0-240L216-680l264-146 264 146-264 120Z"/>',
    "icon-rocket.svg": '<path fill="{color}" transform="translate(4, 4) scale(0.0208)" d="M480-80q-75 0-127.5-52.5T300-260q0-22 5.5-43t16.5-41l-94-94q-20 11-41 16.5t-43 5.5q-75 0-127.5-52.5T-136-600q0-75 52.5-127.5T44-780q22 0 43 5.5t41 16.5l94-94q-11-20-16.5-41t-5.5-43q0-75 52.5-127.5T380-1116q75 0 127.5 52.5T560-936q0 22-5.5 43t-16.5 41l94 94q20-11 41-16.5t43-5.5q75 0 127.5 52.5T896-600q0 75-52.5 127.5T716-420q-22 0-43-5.5t-41-16.5l-94 94q11 20 16.5 41t5.5 43q0 75-52.5 127.5T480-80Z"/>'
}

def render_icon(path_data, dark=True):
    bg = "#342236" if dark else "#eeddee"
    color = "#fed7ff" if dark else "#4a244e"
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 28 28">
  <rect width="28" height="28" rx="8" fill="{bg}"/>
  {path_data.format(color=color)}
</svg>"""

for fname, pdata in ICONS.items():
    with open(os.path.join(DARK_DIR, fname), "w") as f:
        f.write(render_icon(pdata, dark=True))
    with open(os.path.join(LIGHT_DIR, fname), "w") as f:
        f.write(render_icon(pdata, dark=False))

print("Rendered 28x28 M3E icons.")
