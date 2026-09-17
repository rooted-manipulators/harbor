"""Cut the twenty supplied flower artworks into the drawables the app ships.

The flowers are artwork now, not drawn geometry (see docs/01-decisions.md).
The source is twenty RGBA PNGs, one per flower, each a whole plant -- bloom,
stem and two leaves -- on transparency, in the order the specimen sheet reads:
across, then down. `SRC` points at them; run this file and copy the result into
`app/src/main/res/drawable-nodpi/`.

Two cuts come out of each source, because the app shows a flower at two very
different sizes:

  flower_<kind>.webp        the whole plant, for the specimen arch, which no
                            longer draws its own stem and leaves
  flower_<kind>_bloom.webp  the bloom alone, for every small mark -- a 34dp
                            row, a 44dp avatar, the 46dp field chip -- where a
                            whole plant would shrink the actual flower to a
                            third of the box

Finding where the bloom ends is the only judgement here, and it is made on
shape alone -- see [neck]. Colour would be the obvious signal and is the wrong
one: two of the twenty flowers are green, and the hanging stamens on Anxious
are sparse and off-hue in exactly the way a leaf is.

WebP rather than PNG: the same pixels at about a fifth of the weight, which is
the difference between adding 5MB to the APK and adding 1MB. minSdk is 26, so
lossy WebP with alpha is safe everywhere the app runs.
"""
from PIL import Image
import numpy as np
import os, sys

SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "flower-source")
OUT = sys.argv[1] if len(sys.argv) > 1 else "flower-drawables"

# FlowerKind, lowercased, in the sheet's own order.
KINDS = ["glad_we_talked", "lighter_now", "felt_loved", "she_remembered", "easy_silence",
         "steadier_now", "worth_slowing_down", "said_what_i_meant", "want_to_try_something",
         "asked_more_than_usual", "looking_forward", "still_thinking_about_it",
         "hard_to_shake_off", "time_to_actually_do_it", "nothing_left_unsaid",
         "wondering_if_that_landed", "said_the_hard_thing", "glad_she_picked_up",
         "wished_it_was_longer", "dreaded_this_one"]

# The specimen arch is 150dp tall and the landing animation opens the bloom at
# 200dp, so at 3x those are the sizes worth carrying. Bigger buys nothing and
# costs heap: a decoded bitmap is width x height x 4 bytes, and a person's
# ledger can put a dozen different kinds on one screen.
PLANT_EDGE, BLOOM_EDGE = 520, 448
CUT = 24          # alpha at or under this is background
QUALITY = 92

# Where [neck] cannot read a flower, in source pixels.
#
# One of the twenty defeats it. Calm is a crescent leaning off to one side and
# its two leaves reach up level with the bloom, so the plant never pinches in
# between them -- there is no waist to find, and the rule runs on down to the
# foot of the leaves. Measured instead: its blue runs out at row 438.
#
# An entry here is a statement that the picture is unusual, not that the rule
# is wrong. Adding colour to the rule to cover this one case would cost the
# hanging stamens on Anxious, which are sparse and off-hue in exactly the way
# a leaf is.
NECK_BY_HAND = {"worth_slowing_down": 445}


def neck(alpha, h):
    """The row where the plant pinches in under the bloom.

    Walking down from the bloom's widest row, the opaque count falls away and
    then climbs again as the leaves come in: the trough between the two is the
    neck. It is found as a turning point rather than by an absolute threshold,
    because on several of these the leaves attach high enough that there is no
    stem-only row at all -- looking for one put the cut below the leaves and
    handed the small marks a whole plant.
    """
    count = (alpha > CUT).sum(axis=1).astype(float)
    smooth = np.convolve(count, np.ones(9) / 9, mode="same")
    top = int(np.argmax(count > 0))
    peak = top + int(np.argmax(smooth[top:h // 2]))
    limit = int(h * 0.72)
    # The first turning point that is also genuinely narrow. Both halves of
    # that test are load-bearing: without the turn, a flower whose leaves reach
    # the bloom has no stem-only row and the cut lands under the leaves;
    # without the narrowness, a dip between two courses of petals inside the
    # bloom passes for the neck and the cut lands through the flower.
    waist = smooth[peak] * 0.35
    y = peak
    while y + 1 < limit:
        if smooth[y + 1] > smooth[y] and smooth[y] < waist:
            return y
        y += 1
    return peak + int(np.argmin(smooth[peak:limit]))


def trim(im):
    return im.crop(im.getchannel("A").point(lambda v: 255 if v > CUT else 0).getbbox())


def fit(im, edge):
    k = edge / max(im.size)
    return im.resize((max(1, round(im.size[0] * k)), max(1, round(im.size[1] * k))),
                     Image.LANCZOS)


def main():
    os.makedirs(OUT, exist_ok=True)
    for i, kind in enumerate(KINDS):
        path = os.path.join(SRC, "flowers_%02d.png" % (i + 1))
        src = Image.open(path).convert("RGBA")
        w, h = src.size
        n = NECK_BY_HAND.get(kind) or neck(np.asarray(src)[:, :, 3], h)
        plant = fit(trim(src), PLANT_EDGE)
        bloom = fit(trim(src.crop((0, 0, w, n + 1))), BLOOM_EDGE)
        plant.save(os.path.join(OUT, "flower_%s.webp" % kind), quality=QUALITY, method=4)
        bloom.save(os.path.join(OUT, "flower_%s_bloom.webp" % kind), quality=QUALITY, method=4)
        print("%-26s neck %4d/%d  plant %dx%d  bloom %dx%d"
              % (kind, n, h, plant.size[0], plant.size[1], bloom.size[0], bloom.size[1]))
    total = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
    print("%d files, %.0f KB" % (len(os.listdir(OUT)), total / 1024))


if __name__ == "__main__":
    main()
