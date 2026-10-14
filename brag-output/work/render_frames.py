import math
import os
import subprocess
from PIL import Image, ImageDraw, ImageFont

WIDTH, HEIGHT = 1280, 720
FPS = 30
TOTAL_DURATION = 18.0  # seconds
TOTAL_FRAMES = int(TOTAL_DURATION * FPS)
WORK_DIR = "/data/data/com.termux/files/home/petal/brag-output/work/frames"
os.makedirs(WORK_DIR, exist_ok=True)

FONT_PATH = "/system/fonts/Roboto-Regular.ttf"
if not os.path.exists(FONT_PATH):
    FONT_PATH = "/system/fonts/DroidSans.ttf"

def get_font(size):
    try:
        return ImageFont.truetype(FONT_PATH, size)
    except:
        return ImageFont.load_default()

font_hero = get_font(52)
font_title = get_font(38)
font_body = get_font(26)
font_small = get_font(20)

# Colors
BG_DARK = (27, 16, 19)
TEXT_LIGHT = (255, 247, 249)
CORAL = (228, 91, 36)
TEAL = (2, 96, 101)
TEAL_ACCENT = (14, 165, 172)
MUTED = (160, 140, 145)

def draw_rounded_card(draw, box, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)

def draw_polygon_shape(draw, cx, cy, radius, n_points, phase, color):
    points = []
    for i in range(n_points * 2):
        angle = (i * math.pi / n_points) + phase
        r = radius if (i % 2 == 0) else (radius * 0.6)
        px = cx + r * math.cos(angle)
        py = cy + r * math.sin(angle)
        points.append((px, py))
    draw.polygon(points, fill=color)

def draw_sine_wave(draw, x_start, x_end, cy, amp, freq, phase, color):
    pts = []
    for x in range(int(x_start), int(x_end), 2):
        y = cy + amp * math.sin(freq * (x - x_start) + phase)
        pts.append((x, y))
    for i in range(len(pts) - 1):
        draw.line([pts[i], pts[i+1]], fill=color, width=4)

print("Starting frame generation...")

for frame_idx in range(TOTAL_FRAMES):
    t = frame_idx / FPS
    img = Image.new("RGB", (WIDTH, HEIGHT), BG_DARK)
    draw = ImageDraw.Draw(img)
    
    # Background subtle glowing circles
    blob_x1 = 200 + 40 * math.sin(t * 0.8)
    blob_y1 = 150 + 30 * math.cos(t * 0.9)
    draw.ellipse([blob_x1 - 180, blob_y1 - 180, blob_x1 + 180, blob_y1 + 180], fill=(42, 22, 28))
    
    blob_x2 = 1080 - 40 * math.cos(t * 0.7)
    blob_y2 = 550 + 40 * math.sin(t * 0.85)
    draw.ellipse([blob_x2 - 220, blob_y2 - 220, blob_x2 + 220, blob_y2 + 220], fill=(12, 35, 38))

    # Top brand badge
    draw_rounded_card(draw, (40, 30, 230, 75), 16, (40, 24, 30), outline=CORAL, width=2)
    draw.text((55, 40), "🌸 Petal Browser", font=font_small, fill=TEXT_LIGHT)
    draw.text((1050, 42), "GPLv3 · GeckoView 157", font=font_small, fill=MUTED)

    # SCENE 1: The Hook (0.0s - 4.5s)
    if t < 4.5:
        progress = min(1.0, t / 1.0)
        # Big punchy text
        draw.text((100, 180), "NOT ANOTHER", font=font_hero, fill=MUTED)
        draw.text((100, 250), "CHROMIUM CLONE.", font=font_hero, fill=TEXT_LIGHT)
        
        # Red cross line / accent line animation
        line_w = int(min(600, t * 500))
        draw.line([(100, 320), (100 + line_w, 320)], fill=CORAL, width=6)
        
        if t > 1.2:
            sub_alpha = min(1.0, (t - 1.2) / 0.8)
            draw_rounded_card(draw, (100, 370, 780, 520), 20, (35, 20, 25), outline=TEAL, width=2)
            draw.text((130, 400), "Powered by Mozilla GeckoView 157", font=font_title, fill=TEAL_ACCENT)
            draw.text((130, 460), "Desktop-grade standards compliance & quantum multi-process.", font=font_body, fill=MUTED)

        # Spinning swirl logo on the right
        swirl_cx, swirl_cy = 980, 360
        rot = t * 1.5
        draw_polygon_shape(draw, swirl_cx, swirl_cy, 120, 6, rot, TEAL)
        draw_polygon_shape(draw, swirl_cx, swirl_cy, 80, 6, -rot, CORAL)

    # SCENE 2: Material 3 Expressive & 35 Shapes (4.5s - 9.5s)
    elif t < 9.5:
        st = t - 4.5
        draw.text((100, 140), "MATERIAL 3 EXPRESSIVE", font=font_hero, fill=CORAL)
        draw.text((100, 210), "35 Mathematical Morphing Geometries", font=font_title, fill=TEXT_LIGHT)

        # Cards displaying shape attributes
        card_w, card_h = 320, 360
        y_pos = 280
        
        # Card 1: Shapes
        draw_rounded_card(draw, (100, y_pos, 100 + card_w, y_pos + card_h), 24, (38, 22, 28), outline=CORAL, width=2)
        shape_phase = st * 3.0
        n_sides = 4 + int((st * 1.5) % 6)
        draw_polygon_shape(draw, 100 + card_w // 2, y_pos + 120, 70, n_sides, shape_phase, CORAL)
        draw.text((130, y_pos + 230), f"Dynamic Token", font=font_title, fill=TEXT_LIGHT)
        draw.text((130, y_pos + 280), f"Morphing Spring: 0.75f", font=font_small, fill=MUTED)

        # Card 2: AMOLED Mode
        draw_rounded_card(draw, (480, y_pos, 480 + card_w, y_pos + card_h), 24, (0, 0, 0), outline=TEAL, width=2)
        draw_rounded_card(draw, (480 + 40, y_pos + 50, 480 + card_w - 40, y_pos + 190), 16, (15, 15, 15), outline=TEAL_ACCENT, width=1)
        draw.text((480 + 60, y_pos + 105), "#000000", font=font_hero, fill=TEAL_ACCENT)
        draw.text((510, y_pos + 230), "Pure AMOLED Black", font=font_title, fill=TEXT_LIGHT)
        draw.text((510, y_pos + 280), "Max OLED Battery Efficiency", font=font_small, fill=MUTED)

        # Card 3: Color Studio
        draw_rounded_card(draw, (860, y_pos, 860 + card_w, y_pos + card_h), 24, (38, 22, 28), outline=CORAL, width=2)
        palettes = [(228, 91, 36), (2, 96, 101), (65, 120, 210), (50, 160, 90)]
        for pi, pcol in enumerate(palettes):
            draw.ellipse([890 + pi * 65, y_pos + 100, 890 + pi * 65 + 45, y_pos + 145], fill=pcol)
        draw.text((890, y_pos + 230), "Adaptive Palettes", font=font_title, fill=TEXT_LIGHT)
        draw.text((890, y_pos + 280), "Tonal Harmony via OKLCH", font=font_small, fill=MUTED)

    # SCENE 3: Wavy Seekbar & Zero Telemetry (9.5s - 14.5s)
    elif t < 14.5:
        st = t - 9.5
        draw.text((100, 130), "MPVEX-INSPIRED MEDIA", font=font_hero, fill=TEAL_ACCENT)
        draw.text((100, 200), "Active Sine-Wave Seekbar", font=font_title, fill=TEXT_LIGHT)

        # Player Container Box
        draw_rounded_card(draw, (100, 270, 1180, 580), 28, (20, 12, 16), outline=CORAL, width=2)
        
        # Simulated Video Screen
        draw_rounded_card(draw, (140, 300, 600, 540), 20, (30, 20, 24))
        draw_polygon_shape(draw, 370, 410, 50, 8, st * 2, CORAL)
        draw.text((250, 480), "Quantum Video Stream", font=font_small, fill=MUTED)

        # Player controls and wavy seekbar
        player_x = 640
        draw.text((player_x, 320), "Active Playback: 01:33 / 04:05", font=font_body, fill=TEXT_LIGHT)
        
        # Wavy seekbar line
        is_playing = (st % 3.0 < 2.0)
        amp = 12 if is_playing else 0
        draw_sine_wave(draw, player_x, 1140, 400, amp, 0.05, st * 10, CORAL)
        
        status_label = "Wave State: Rippling" if is_playing else "Wave State: Paused & Flat"
        draw.text((player_x, 430), status_label, font=font_small, fill=TEAL_ACCENT)

        # Strict ETP badges
        draw_rounded_card(draw, (player_x, 480, 1140, 540), 12, (32, 20, 26), outline=TEAL, width=1)
        draw.text((player_x + 20, 498), "🛡️ Strict ETP: 12 Trackers Blocked · Zero Telemetry", font=font_small, fill=TEXT_LIGHT)

    # SCENE 4: CTA / Outro (14.5s - 18.0s)
    else:
        st = t - 14.5
        cx, cy = WIDTH // 2, HEIGHT // 2 - 30
        
        # Glowing center swirl
        scale = min(1.0, st * 1.5)
        r = int(90 * scale)
        if r > 5:
            draw_polygon_shape(draw, cx, cy - 60, r, 6, st * 2, TEAL)
            draw_polygon_shape(draw, cx, cy - 60, int(r * 0.65), 6, -st * 2, CORAL)
        
        draw.text((cx - 170, cy + 50), "Petal Browser", font=font_hero, fill=TEXT_LIGHT)
        draw.text((cx - 280, cy + 130), "Respects your attention, privacy, and device.", font=font_title, fill=CORAL)
        
        draw_rounded_card(draw, (cx - 200, cy + 200, cx + 200, cy + 260), 30, CORAL)
        draw.text((cx - 150, cy + 218), "Download Signed APK", font=font_body, fill=TEXT_LIGHT)
        draw.text((cx - 160, cy + 280), "Open Source · GPL-3.0 · GitHub", font=font_small, fill=MUTED)

    # Save frame
    frame_path = os.path.join(WORK_DIR, f"frame_{frame_idx:04d}.png")
    img.save(frame_path)
    if frame_idx % 60 == 0:
        print(f"Generated {frame_idx}/{TOTAL_FRAMES} frames ({t:.1f}s)")

print("All frames rendered successfully.")
