"""Draw a design wireframe for the 8x8 perfboard editor page.

This is a layout mock-up, not a game screenshot. It uses the same palette and dimensions as
soldering.html / soldering.css so the page structure can be reviewed without launching Minecraft.

Run from the repository root with the bundled Python:
    python docs/soldering-editor/wireframe.py
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
ICON_DIR = ROOT / "src/main/resources/assets/wildfires/textures/item"
OUT = ROOT / "docs/soldering-editor/wireframe.png"

W, H = 610, 704
BOARD_X, BOARD_Y, CELL, MARGIN = 20, 78, 28, 16
BOARD_W = 256

COL_BG = (18, 20, 22, 255)
COL_PANEL = (39, 41, 44, 255)
COL_PANEL_LIGHT = (52, 55, 59, 255)
COL_EDGE = (14, 14, 16, 255)
COL_INK = (230, 232, 236, 255)
COL_MUTED = (160, 165, 172, 255)
COL_GOLD = (240, 160, 80, 255)
COL_GREEN = (47, 125, 58, 255)
COL_DARKGREEN = (23, 37, 28, 255)
COL_SLOT = (119, 120, 122, 255)
COL_SLOT_DARK = (77, 78, 80, 255)
COL_SLOT_LIGHT = (178, 179, 181, 255)

FONT_PATH = "C:/Windows/Fonts/msyh.ttc"


def font(size: int) -> ImageFont.FreeTypeFont:
    try:
        return ImageFont.truetype(FONT_PATH, size)
    except OSError:
        return ImageFont.load_default()


def text(draw: ImageDraw.ImageDraw, xy, value, size=12, fill=COL_INK):
    draw.text(xy, value, font=font(size), fill=fill)


def rounded(draw, box, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def button(draw, box, label, active=False, small=False):
    fill = COL_GOLD if active else COL_PANEL_LIGHT
    ink = (26, 18, 8, 255) if active else COL_INK
    rounded(draw, box, 3, fill, COL_EDGE, 1)
    size = 10 if small else 11
    bbox = draw.textbbox((0, 0), label, font=font(size))
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    text(draw, (box[0] + (box[2] - box[0] - tw) / 2, box[1] + (box[3] - box[1] - th) / 2 - 1),
         label, size, ink)


def draw_board(draw: ImageDraw.ImageDraw):
    draw.rectangle((BOARD_X, BOARD_Y, BOARD_X + BOARD_W, BOARD_Y + BOARD_W), fill=COL_DARKGREEN)
    draw.rectangle((BOARD_X + 7, BOARD_Y + 7, BOARD_X + BOARD_W - 7, BOARD_Y + BOARD_W - 7),
                   fill=COL_GREEN)
    draw.rectangle((BOARD_X + 7, BOARD_Y + 7, BOARD_X + BOARD_W - 7, BOARD_Y + 10),
                   fill=(59, 140, 71, 255))
    for i in range(9):
        p = BOARD_X + MARGIN + i * CELL
        draw.line((p, BOARD_Y + MARGIN, p, BOARD_Y + MARGIN + 8 * CELL),
                  fill=(36, 95, 45, 255))
        draw.line((BOARD_X + MARGIN, p, BOARD_X + MARGIN + 8 * CELL, p),
                  fill=(36, 95, 45, 255))
    for y in range(8):
        for x in range(8):
            cx = BOARD_X + MARGIN + x * CELL + CELL // 2
            cy = BOARD_Y + MARGIN + y * CELL + CELL // 2
            draw.ellipse((cx - 5, cy - 5, cx + 5, cy + 5), fill=COL_GOLD)
            draw.ellipse((cx - 3, cy - 3, cx + 3, cy + 3), fill=(11, 13, 14, 255))

    def node(x, y):
        return (BOARD_X + MARGIN + x * CELL + CELL // 2,
                BOARD_Y + MARGIN + y * CELL + CELL // 2)

    # Two traces: a straight horizontal one and an L-shaped one.
    for a, b in [((1, 1), (5, 1)), ((5, 1), (6, 1)), ((6, 1), (6, 4)), ((1, 5), (4, 5)),
                 ((4, 5), (4, 3))]:
        draw.line((*node(*a), *node(*b)), fill=(20, 52, 27, 255), width=7)
    for a, b in [((1, 1), (5, 1)), ((5, 1), (6, 1)), ((6, 1), (6, 4)), ((1, 5), (4, 5)),
                 ((4, 5), (4, 3))]:
        draw.line((*node(*a), *node(*b)), fill=COL_GOLD, width=3)

    # A resistor at (1,1), a capacitor at (3,3), a chip at (5,5) roughly.
    draw.rectangle((BOARD_X + MARGIN + 1 * CELL + 6, BOARD_Y + MARGIN + 1 * CELL + 9,
                    BOARD_X + MARGIN + 3 * CELL - 6, BOARD_Y + MARGIN + 2 * CELL - 9),
                   fill=(216, 192, 138, 255), outline=COL_EDGE)
    draw.rectangle((BOARD_X + MARGIN + 1 * CELL + 12, BOARD_Y + MARGIN + 1 * CELL + 10,
                    BOARD_X + MARGIN + 1 * CELL + 15, BOARD_Y + MARGIN + 2 * CELL - 10),
                   fill=(210, 74, 74, 255))
    draw.rectangle((BOARD_X + MARGIN + 3 * CELL + 10, BOARD_Y + MARGIN + 3 * CELL + 10,
                    BOARD_X + MARGIN + 5 * CELL - 10, BOARD_Y + MARGIN + 5 * CELL - 10),
                   fill=(58, 78, 110, 255), outline=COL_EDGE)
    draw.rectangle((BOARD_X + MARGIN + 5 * CELL + 8, BOARD_Y + MARGIN + 5 * CELL + 8,
                    BOARD_X + MARGIN + 8 * CELL - 8, BOARD_Y + MARGIN + 7 * CELL - 8),
                   fill=(36, 38, 43, 255), outline=COL_EDGE)
    for i in range(3):
        px = BOARD_X + MARGIN + 5 * CELL + 14 + i * 12
        draw.rectangle((px, BOARD_Y + MARGIN + 5 * CELL + 2, px + 3, BOARD_Y + MARGIN + 5 * CELL + 8),
                       fill=(185, 190, 198, 255))
        draw.rectangle((px, BOARD_Y + MARGIN + 7 * CELL - 8, px + 3, BOARD_Y + MARGIN + 7 * CELL - 2),
                       fill=(185, 190, 198, 255))

    draw.rectangle((BOARD_X + 2, BOARD_Y + 2, BOARD_X + BOARD_W - 2, BOARD_Y + BOARD_W - 2),
                   outline=(13, 21, 16, 255), width=3)


def draw_icon(draw: ImageDraw.ImageDraw, name: str, box):
    path = ICON_DIR / f"{name}.png"
    icon = Image.open(path).convert("RGBA").resize((32, 32), Image.NEAREST)
    icon.putalpha(icon.getchannel("A"))
    # Draw on a temporary layer so transparency is preserved.
    return icon


def main() -> None:
    image = Image.new("RGBA", (W, H), COL_BG)
    draw = ImageDraw.Draw(image)
    rounded(draw, (0, 0, W - 1, H - 1), 4, COL_PANEL, COL_EDGE, 2)
    draw.rectangle((0, 0, W - 1, 40), fill=COL_PANEL_LIGHT)
    text(draw, (16, 10), "洞洞板电路编辑器", 18)
    text(draw, (W - 80, 15), "8 × 8", 12, COL_MUTED)

    # Board column.
    text(draw, (20, 52), "8×8 洞洞板", 14)
    text(draw, (W - 90, 55), "8 × 8", 11, COL_MUTED)
    draw_board(draw)
    text(draw, (20, 342), "点两个洞拉线：同排/同列直线，否则 L 形（先横后竖）；", 11, COL_MUTED)
    text(draw, (20, 358), "消耗铜线 = 曼哈顿距离", 11, COL_MUTED)

    # Side column.
    sx = 300
    text(draw, (sx, 52), "模式", 10, COL_MUTED)
    button(draw, (sx, 66, sx + 90, 94), "布线", True, True)
    button(draw, (sx + 94, 66, sx + 184, 94), "装元件", False, True)
    button(draw, (sx + 188, 66, sx + 278, 94), "删除", False, True)

    text(draw, (sx, 102), "元件", 10, COL_MUTED)
    palette = [("resistor", "电阻", "1×2"), ("capacitor", "电容", "2×2"),
               ("switch", "开关", "1×3"), ("diode", "二极管", "1×2 · 极性"),
               ("ic_chip", "芯片", "3×2 / 2×3")]
    py = 116
    for index, (icon, name, size) in enumerate(palette):
        selected = index == 0
        rounded(draw, (sx, py, sx + 278, py + 32), 3,
                (52, 55, 59, 255), COL_GOLD if selected else COL_EDGE, 1)
        draw.rectangle((sx + 3, py + 3, sx + 31, py + 29), fill=(27, 42, 32, 255), outline=COL_EDGE)
        icon_image = draw_icon(draw, icon, (sx + 3, py + 3, sx + 31, py + 29))
        icon_image = icon_image.resize((26, 26), Image.NEAREST)
        image.alpha_composite(icon_image, (sx + 4, py + 3))
        text(draw, (sx + 38, py + 5), name, 12)
        text(draw, (sx + 38, py + 19), size, 9, COL_MUTED)
        text(draw, (sx + 250, py + 8), "1", 11, COL_GOLD)
        py += 35

    button(draw, (sx, py + 2, sx + 136, py + 28), "方向：横向", False, True)
    button(draw, (sx + 140, py + 2, sx + 278, py + 28), "极性：正向", False, True)
    py += 34
    text(draw, (sx, py + 2), "铜线 12", 11, COL_MUTED)
    text(draw, (sx + 80, py + 2), "空白板 3", 11, COL_MUTED)
    py += 20
    rounded(draw, (sx, py, sx + 278, py + 44), 2, (28, 32, 27, 255), COL_GOLD, 1)
    text(draw, (sx + 6, py + 6), "已选（1, 1），再点一个洞", 11)
    text(draw, (sx + 6, py + 24), "这条线需要 4 铜线", 10, COL_MUTED)
    py += 52
    button(draw, (sx, py, sx + 136, py + 30), "完成", True)
    button(draw, (sx + 140, py, sx + 278, py + 30), "清空", False)

    # Backpack.
    by = 452
    text(draw, (W // 2 - 20, by), "背包", 14, COL_MUTED)
    container_x = (W - 476) // 2
    container_y = by + 22
    draw.rectangle((container_x, container_y, container_x + 476, container_y + 216),
                   fill=(30, 32, 34, 255), outline=COL_EDGE, width=2)
    for row in range(4):
        for col in range(9):
            x = container_x + 4 + col * 52
            y = container_y + 4 + row * 52
            draw.rectangle((x, y, x + 48, y + 48), fill=COL_SLOT, outline=COL_EDGE, width=3)
            draw.line((x + 3, y + 3, x + 45, y + 3), fill=COL_SLOT_DARK, width=2)
            draw.line((x + 3, y + 3, x + 3, y + 45), fill=COL_SLOT_DARK, width=2)
            draw.line((x + 45, y + 45, x + 45, y + 3), fill=COL_SLOT_LIGHT, width=2)
            draw.line((x + 45, y + 45, x + 3, y + 45), fill=COL_SLOT_LIGHT, width=2)
    # A few demo items in the backpack.
    demo = [("resistor", 0), ("capacitor", 1), ("switch", 2), ("diode", 3), ("ic_chip", 4),
            ("copper_wire", 5), ("circuit_board", 6)]
    for icon, index in demo:
        col = index % 9
        row = index // 9
        x = container_x + 4 + col * 52
        y = container_y + 4 + row * 52
        icon_image = draw_icon(draw, icon, (x, y, x + 48, y + 48))
        image.alpha_composite(icon_image, (x + 8, y + 8))

    # Footer note.
    text(draw, (20, H - 22), "线框图（非实机截图）：610 × 704，与 soldering.html 的布局一致。",
         10, COL_MUTED)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUT)
    print(f"wrote {OUT} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
