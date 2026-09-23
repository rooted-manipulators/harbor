"""Cut the tour bee's eight poses into the drawables the app ships.

The source is one sheet, `tour-source/tour_bees.webp`: the bee in eight
poses, four across and two down, on a transparent background --

    greet     explain   point      wonder
    idea      surprise  celebrate  approve

(a wave; an open hand presenting; a finger pointing; a hand to the chin
under a question mark; a finger up under a lightbulb; hands up at an
exclamation mark; both arms up in confetti; a wink and a thumbs up).

Separated the same way `cut_moods.py` separates the mood sheet: each row is
split into connected pieces, the four biggest are the bees, and every
smaller piece -- a question mark, a spark, a scrap of confetti -- goes to
the bee it sits nearest. `TourBeeState` in ui/TourBee.kt names the same
eight, and the files land on exactly the names it already loads, so
re-running this after a redraw is the whole of changing the art.

Run from the repo root:

    python3 tools/cut_tour_bee.py
"""
from PIL import Image
import numpy as np
from scipy import ndimage
import os

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "tour-source", "tour_bees.webp")
OUT = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable-nodpi")

NAMES = [["greet", "explain", "point", "wonder"],
         ["idea", "surprise", "celebrate", "approve"]]

EDGE = 432        # the mascot shows at 132dp; this is ~3x of that
CUT = 20          # alpha at or under this is not part of anything
QUALITY = 92


def bands(solid):
    """The two rows of art, found by the fully transparent rows between them."""
    rows = solid.any(axis=1)
    out, start = [], None
    for y, v in enumerate(rows):
        if v and start is None:
            start = y
        if not v and start is not None:
            out.append((start, y))
            start = None
    if start is not None:
        out.append((start, len(rows)))
    return [b for b in out if b[1] - b[0] > 100]


def cut_band(band, names):
    solid = band[:, :, 3] > CUT
    lab, n = ndimage.label(solid)
    sizes = ndimage.sum(solid, lab, range(1, n + 1))
    centres = ndimage.center_of_mass(solid, lab, range(1, n + 1))

    bees = sorted(np.argsort(sizes)[-4:], key=lambda i: centres[i][1])
    owner = {}
    for i in range(n):
        near = min(range(4), key=lambda k: abs(centres[i][1] - centres[bees[k]][1]) +
                   abs(centres[i][0] - centres[bees[k]][0]) * 0.5)
        owner[i + 1] = near

    for k, name in enumerate(names):
        mine = np.isin(lab, [lab_id for lab_id, o in owner.items() if o == k])
        mine = ndimage.binary_dilation(mine, iterations=2)
        tile = band.copy()
        tile[~mine] = 0
        ys, xs = np.nonzero(tile[:, :, 3])
        tile = tile[ys.min():ys.max() + 1, xs.min():xs.max() + 1]

        # Square, standing on its feet: the overlay anchors the bee to the
        # bottom of its box, and one floating in the middle would hover.
        h, w = tile.shape[:2]
        side = max(h, w)
        square = np.zeros((side, side, 4), dtype=np.uint8)
        square[side - h:, (side - w) // 2:(side - w) // 2 + w] = tile
        img = Image.fromarray(square, "RGBA").resize((EDGE, EDGE), Image.LANCZOS)
        path = os.path.join(OUT, f"bee_tour_{name}.webp")
        img.save(path, "WEBP", quality=QUALITY, method=6)
        print(f"{path}  {os.path.getsize(path) // 1024}KB")


rgba = np.asarray(Image.open(SRC).convert("RGBA"))
found = bands(rgba[:, :, 3] > CUT)
assert len(found) == 2, f"expected two rows of bees, found {found}"
for (top, bottom), names in zip(found, NAMES):
    cut_band(rgba[top:bottom], names)
