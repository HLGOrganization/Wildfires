"""Generate the 16x16 item icons for the 8x8 perfboard parts.

The palette is lifted from the existing soldering textures (circuit_board.png,
soldered_circuit.png, electronic_component.png, solder_wire.png) so the new icons read as
part of the same set: transparent background, a 1 px near-black outline, and at most a
handful of flat colours.

Run from the repository root with the bundled Python:
    python docs/soldering-editor/gen_icons.py
"""

from pathlib import Path

from PIL import Image

PALETTE = {
    ".": (0, 0, 0, 0),
    "#": (20, 20, 22, 255),      # outline
    "G": (47, 125, 58, 255),     # PCB green
    "Y": (224, 179, 76, 255),    # gold
    "y": (244, 210, 122, 255),   # light gold
    "S": (185, 190, 198, 255),   # silver
    "s": (154, 160, 168, 255),   # mid silver
    "D": (36, 38, 43, 255),      # dark body
    "d": (107, 112, 120, 255),   # grey body
    "C": (168, 95, 42, 255),     # copper
    "c": (240, 160, 80, 255),    # bright copper
    "R": (210, 74, 74, 255),     # red band
    "B": (58, 78, 110, 255),     # capacitor blue
    "W": (215, 219, 224, 255),   # highlight
    "N": (216, 192, 138, 255),   # resistor body
}

ICONS = {
    "resistor": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "....########....",
        "...#NNNNNNNN#...",
        "..#SNNRNNYYNNS#.",
        "..#SNNRNNYYNNS#.",
        "...#NNNNNNNN#...",
        "....########....",
        "................",
        "................",
        "................",
        "................",
        "................",
    ],
    "capacitor": [
        "................",
        "................",
        "....########....",
        "...#SSSSSSSS#...",
        "...#BWBBBBBB#...",
        "...#BWBBBBBB#...",
        "...#BWBBBBBB#...",
        "...#BWBBBBBB#...",
        "...#BBBBBBBB#...",
        "....########....",
        "....#S..#S......",
        "....#S..#S......",
        "....#S..#S......",
        "....#S..#S......",
        "................",
        "................",
    ],
    "switch": [
        "................",
        "................",
        "................",
        "......##........",
        ".....#SS#.......",
        ".....#SS#.......",
        "..############..",
        ".#dddddddddddd#.",
        ".#dddddddddddd#.",
        ".#dddddddddddd#.",
        "..############..",
        "....#Y....Y#....",
        "....#Y....Y#....",
        "................",
        "................",
        "................",
    ],
    "diode": [
        "................",
        "................",
        "................",
        "................",
        "....########....",
        "...#DDDDDDDD#...",
        "..#SDDDDSSDDS#..",
        "..#SDDDDSSDDS#..",
        "...#DDDDDDDD#...",
        "....########....",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ],
    "ic_chip": [
        "................",
        "................",
        "....###..###....",
        "..S#DDDDDDDD#S..",
        "..S#DDDDDDDD#S..",
        "..S#DDDDDDDD#S..",
        "..S#DDDDDDDD#S..",
        "..S#DDDDDDDD#S..",
        "..S#DDDDDDDD#S..",
        "..S#DDDDDDDD#S..",
        "..S#DDDDDDDD#S..",
        "..S#DDDDDDDD#S..",
        "....########....",
        "................",
        "................",
        "................",
    ],
    "copper_wire": [
        "................",
        "................",
        "....########....",
        "...#CCCCCCCC#...",
        "..#CCCCCCCCCC#..",
        ".#CCCCCCCCCCCC#.",
        ".#CCC######CCC#.",
        ".#CCC#....#CCC#.",
        ".#CCC#....#CCC#.",
        ".#CCC######CCC#.",
        ".#CCCCCCCCCCCC#.",
        "..#CCCCCCCCCC#..",
        "...#CCCCCCCC#...",
        "....########....",
        ".......#C#......",
        "................",
    ],
}


def main() -> None:
    root = Path(__file__).resolve().parents[2]
    out_dir = root / "src/main/resources/assets/wildfires/textures/item"
    out_dir.mkdir(parents=True, exist_ok=True)
    for name, rows in ICONS.items():
        if len(rows) != 16 or any(len(row) != 16 for row in rows):
            raise ValueError(f"{name}: expected 16x16, got {len(rows)} rows")
        image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        pixels = image.load()
        for y, row in enumerate(rows):
            for x, char in enumerate(row):
                pixels[x, y] = PALETTE[char]
        path = out_dir / f"{name}.png"
        image.save(path, optimize=True)
        print(f"wrote {path} ({path.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
