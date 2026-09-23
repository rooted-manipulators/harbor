"""Cut the eight bee-and-flower moods into the drawables the app ships.

The source is one sheet, `mood-source/bee_moods.webp`: eight bees, each holding
a flower, four across and two down, each over a label pill -- Happy, Calm,
Loved, Curious, then Grounded, Hopeful, Brave, Anxious. It already has a
transparent background, so there is nothing to key out; the only work is
separating the tiles and leaving the labels behind.

## How the tiles are separated

Not by fixed columns. Two tiles on the top row overlap by a few pixels -- the
Loved bee's wing and the Curious bee's flower share columns 629 to 632 -- so a
straight cut would take a sliver of one into the other. Every tile is its own
connected shape, though, and so is every heart, sparkle and dash around it. So
each band is split into connected pieces, the four biggest are the bees, and
every smaller piece goes to whichever bee it sits nearest.

The labels are a band of their own under each row of art, separated by
transparent rows, and are simply never read. The app writes its own words.

Run from the repo root:

    python3 tools/cut_moods.py

and the eight `bee_mood_<name>.webp` files land in the drawables folder.
"""
from PIL import Image
import numpy as np
from scipy import ndimage
import os

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "mood-source", "bee_moods.webp")
OUT = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable-nodpi")

# The art bands, in sheet pixels. Found by looking for fully transparent rows:
# each row of bees is followed by a gap and then its row of label pills.
BANDS = [(106, 507), (639, 1056)]
NAMES = [["happy", "calm", "loved", "curious"],
         ["grounded", "hopeful", "brave", "anxious"]]

EDGE = 384        # 128dp at 3x; the arch shows this at about 116dp
CUT = 20          # alpha at or under this is not part of anything
QUALITY = 92


def cut_band(rgba, top, bottom, names):
    band = rgba[top:bottom]
    solid = band[:, :, 3] > CUT
    lab, n = ndimage.label(solid)
    sizes = ndimage.sum(solid, lab, range(1, n + 1))
    centres = ndimage.center_of_mass(solid, lab, range(1, n + 1))

    # The four bees are the four biggest pieces, left to right.
    bees = sorted(np.argsort(sizes)[-4:], key=lambda i: centres[i][1])
    owner = {}
    for i in range(n):
        near = min(range(4), key=lambda k: abs(centres[i][1] - centres[bees[k]][1]) +
                   abs(centres[i][0] - centres[bees[k]][0]) * 0.5)
        owner[i + 1] = near

    for k, name in enumerate(names):
        mine = np.isin(lab, [lab_id for lab_id, o in owner.items() if o == k])
        # Grow the mask two pixels so the soft antialiased rim, which is under
        # the cut, comes along with the shape it belongs to.
        mine = ndimage.binary_dilation(mine, iterations=2)
        tile = band.copy()
        tile[~mine] = 0
        ys, xs = np.nonzero(tile[:, :, 3])
        tile = tile[ys.min():ys.max() + 1, xs.min():xs.max() + 1]

        # Square, with the art standing on the bottom edge -- the arch aligns
        # it to the bottom, and a bee floating in the middle of its box would
        # hover over the arch's floor.
        h, w = tile.shape[:2]
        side = max(h, w)
        square = np.zeros((side, side, 4), dtype=np.uint8)
        square[side - h:, (side - w) // 2:(side - w) // 2 + w] = tile
        img = Image.fromarray(square, "RGBA").resize((EDGE, EDGE), Image.LANCZOS)
        path = os.path.join(OUT, f"bee_mood_{name}.webp")
        img.save(path, "WEBP", quality=QUALITY, method=6)
        print(f"{path}  {os.path.getsize(path) // 1024}KB")


rgba = np.asarray(Image.open(SRC).convert("RGBA"))
for (top, bottom), names in zip(BANDS, NAMES):
    cut_band(rgba, top, bottom, names)
