package app.harbor.domain

import java.util.UUID
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The garden as a place you can be in.
 *
 * Ported from the `fieldtrial.html` prototype, which is the design authority
 * for this screen the way `harvest-pulse` is for the rest of the app. The
 * terrain it stands on is in [Terrain]; this is everything above ground —
 * whose patch is where, what each cell of the field is, and the camera.
 *
 * ## One camera, not two views
 *
 * There is no 2D mode and 3D mode. [tiltFor] blends a flat overhead
 * projection into a perspective one as you zoom in, so the plan view *is* the
 * field seen from far enough away, and everything in between is a real
 * position on that dial. Pulling back is how you get the map; leaning in is
 * how you get the landscape. The readout calls the three states plan,
 * tipping and landscape.
 *
 * That is why this replaced a Field/Top toggle: a toggle asks the user to
 * classify what they want before they can look, and the honest answer is
 * usually "somewhere between".
 *
 * ## Why flowers appear and disappear
 *
 * A planted cell is a coloured dot until it is drawn larger than
 * [FLOWER_AT] pixels, at which point it opens into an actual flower. That is
 * level of detail doing the work of an animation: walking in opens the buds
 * around you because they got big, not because anything is keyframed.
 *
 * Pure arithmetic, no Android, so the projection can be tested exactly.
 */
object Field {

    // --- camera constants, from the prototype -----------------------------

    /** Relative zoom where the overhead view starts tipping into perspective. */
    const val TILT_FROM = 2.1

    /** Relative zoom by which the tip is complete. */
    const val TILT_TO = 4.2

    /** How far in you can go, as a multiple of the overview zoom. */
    const val MAX_REL = 30.0

    private const val EYE = 300.0
    private const val SET_BACK = 2.4
    private const val HORIZON = 0.14
    private const val ELEVATION = 170.0

    /** Drawn radius at which a planted dot becomes a flower. */
    // Lowered from 5.5. A planted cell under this draws as a plain circle,
    // and at the zoom people actually open the garden at, that meant a patch
    // of somebody's flowers was a patch of dots -- the one place in the app
    // where the flowers were promised and not delivered. Below about four
    // pixels a petal is thinner than a pixel and there is genuinely nothing
    // to show, so this is as far down as it is worth going.
    const val FLOWER_AT = 4.2

    /**
     * Cell radius in pixels is this times its size, times the projected scale.
     *
     * Tied to [Terrain.CELL], and the ratio between them is the thing that
     * matters: a dot is `size * DOT_SCALE` across and stands `CELL` from its
     * neighbour, so `DOT_SCALE / CELL` is how much of the ground each cell
     * covers. That ratio is fixed across zoom — dots and the gaps between them
     * grow together — which is why getting it wrong looks fine far off, where
     * every dot is sub-pixel, and like a heap of overlapping discs close to.
     *
     * Was 3.3 against a cell of 15. When the grid went to 10 this stayed at
     * 3.3 for one build, which put coverage up by half and turned the ground
     * at standing zoom into green foam. 2.2 against 10 is the same coverage as
     * 3.3 against 15: the same ground, told in more and smaller marks, which
     * is what denser was meant to mean.
     *
     * Change [Terrain.CELL] and this has to move with it.
     */
    const val DOT_SCALE = 2.2

    /**
     * Drawn radius at which a bloom stops being drawn and becomes the artwork.
     *
     * The third and last rung of the ladder [FLOWER_AT] starts: a planted cell
     * is a coloured dot, then petals walked around a centre, then the flower
     * itself. Thirteen pixels is about where the drawn one runs out of things
     * to say -- below that a petal is a few pixels across and the artwork
     * would only be a smudge with more steps in it.
     *
     * Raw pixels, like [FLOWER_AT] and [DOT_SCALE], not dp. The field is
     * measured in projected pixels throughout because pixels are what decide
     * whether there is anything to see; a denser screen showing more detail
     * from the same camera is the right answer, not a bug.
     */
    const val ARTWORK_AT = 13.0

    /**
     * How far past [ARTWORK_AT] the drawn flower fades out under the artwork.
     *
     * Both are drawn through this band, which costs one extra flower's worth
     * of fills on the few blooms inside it. It is what stops a patch popping
     * as you lean in -- and a rung you can see is a rung that failed, because
     * the whole ladder is meant to read as one flower getting closer.
     */
    const val ARTWORK_FADE = 4.0

    /**
     * Drawn radius at which ground stops being a dot and grows blades.
     *
     * The same idea one level down. Far off a tuft of grass is a dot, because
     * at that distance a tuft of grass *is* a dot. Close to, it is a dot with
     * grass coming out of it: the dot stays, shrinking, as the root of the
     * clump, so nothing has to appear out of nothing.
     */
    const val GRASS_AT = 2.2

    /**
     * How many tufts one frame will grow before the rest stay dots.
     *
     * Zooming in shrinks how much ground is on screen, so the count falls away
     * on its own and this is almost never reached. It is here for the band on
     * the way in where the ground is both close enough to sprout and still
     * wide enough to fill the view -- the one framing that could otherwise put
     * tens of thousands of blades in a single frame.
     */
    const val GRASS_BUDGET = 1400

    /**
     * How far the view has to have tipped before ground grows blades.
     *
     * A blade is drawn as a sliver rising up the screen, which is a thing seen
     * from the side. In plan view you are directly above the ground and there
     * is no "up the screen" for grass to go, so drawing it there is not a
     * coarse version of the truth, it is a different picture.
     *
     * It also fixed the louder half of the same bug. Flat, every cell projects
     * at the same scale, so whether it sprouts comes down to the cell's own
     * size -- and the high ground at the back of the island carries the bigger
     * cells. The overview grew grass over most of the map, thickest along the
     * far edge, which is the one place the eye is not.
     */
    const val GRASS_TILT = 0.25

    /**
     * Where down the screen grass starts, once the view has tipped.
     *
     * The horizon sits near the top; ground drawn just under it is a long way
     * off however far you have zoomed in. Scale alone does not say so -- lean
     * in far enough and the distant ground is being drawn large too -- so
     * nearness is read off the screen position, which in a tipped view *is*
     * depth. Grass thickens toward the bottom of the frame, which is both
     * where you are standing and how a field actually looks.
     */
    const val GRASS_FROM = 0.35

    /**
     * How much a tuft stands up: nought is a plain dot, one is full grass.
     *
     * Kept here rather than in the renderer because it is the rule about when
     * the ground has grass at all, and that is worth being able to test
     * without a screen.
     */
    fun grassStand(tilt: Double, screenY: Double, height: Double): Double {
        if (height <= 0.0) return 0.0
        val tipped = ((tilt - GRASS_TILT) / (1.0 - GRASS_TILT)).coerceIn(0.0, 1.0)
        if (tipped <= 0.0) return 0.0
        val near = ((screenY / height - GRASS_FROM) / (1.0 - GRASS_FROM)).coerceIn(0.0, 1.0)
        return tipped * near
    }

    /**
     * A stable scrap of randomness for one cell's nth blade.
     *
     * Pure, and a function of the cell's own tone, so a tuft is the same tuft
     * every frame. Grass built from a running random would crawl as you panned
     * across it, which reads as the ground being alive in a way nothing else
     * in this app is.
     *
     * Hashed through a sine rather than the obvious `frac(tone * n * k)`. That
     * cheaper version steps by a constant as `n` goes up, so the values inside
     * one tuft march in a straight line instead of scattering: every blade
     * leaned a little further than the last and a field of grass came out as
     * a field of identical diagonal combs.
     */
    fun wisp(tone: Double, n: Int): Double {
        val v = sin((tone + 1.0) * 127.1 + n * 311.7) * 43758.5453
        return v - floor(v)
    }

    // --- paint palette ----------------------------------------------------
    //
    // Colours are bucketed so the whole field draws in about a dozen fills
    // rather than one per cell. The index a cell carries is its bucket.

    // Sage rather than grass, and now sage after dark.
    //
    // These used to be saturated yellow-greens, which made the whole field a
    // wall of colour and left a bloom nothing to be brighter than. The design
    // holds colour back everywhere except the flower, and the field is the
    // largest surface in the app to apply that to: the ground is quiet so a
    // patch of somebody's flowers reads from across the valley.
    //
    // Taken down twice, and the second time is the one that mattered.
    //
    // The first dark pass lowered this ladder to sit under a dusk sky painted
    // inside the field's own box. Then the sky became the whole screen and got
    // genuinely bright, and measuring the render showed the land arriving at
    // 1.15 to 1.38 luminance contrast against it -- which is to say the ground
    // and the sky were the same brightness and only hue was telling them
    // apart. The horizon stopped being a horizon.
    //
    // These are dark enough to silhouette, which is both what the reference
    // does and what an evening actually looks like: at dusk the sky is the
    // bright thing and the land in front of it is nearly black. It also gives
    // a flower somewhere to be luminous, which on a field of thousands of
    // cells is the only way one bloom reads at all.
    //
    // Order is preserved: index 0 is still the lightest step.
    //
    // Lifted once more, and this time for the *bottom* of the view rather
    // than the top. The sky gradient hands over to [Paper] two thirds of the
    // way down, so under the horizon the land was dark green on near-black:
    // 1.10 to 1.52 luminance contrast against the page, which is to say the
    // island had no edge at all below the skyline, and the darkest two steps
    // were not there.
    //
    // The ladder is now compressed rather than moved. Every step reads
    // against the page -- 1.44 at the dark end, 1.83 at the light one -- and
    // every step still sits under the sky, at 1.35 to 1.72, so the horizon
    // keeps its silhouette. The two cannot both be strong: the page and the
    // sky are only about 2.5 to 1 apart in the first place, and the land has
    // to live between them.
    // **Lit, 18 Sep 2026, and the argument above is now the wrong one.**
    //
    // Everything before this paragraph reasons about a *dusk*: a bright sky
    // with nearly-black land silhouetted in front of it, and a ladder tuned to
    // stay readable against a near-black page. The field is a daylight meadow
    // now (`ui/Meadow.kt`, ported from the web prototype), and in daylight the
    // land is not a silhouette -- it is the lit thing, and the sky behind it is
    // paler than it is.
    //
    // So the ladder is the prototype's own `field` and `fieldDeep` greens,
    // lightest first, in the order this list has always been read in. What the
    // old note gets right and still applies: these have to read against the
    // page as well as against the sky, and the two cannot both be strong. The
    // difference is that the page is now the far end of a gradient rather than
    // the colour immediately under the horizon, which is why the sky gained a
    // horizon stop in the same change -- the fade carries the seam that the
    // contrast step used to.
    //
    // Unmeasured on a device. The old numbers came with luminance ratios
    // somebody computed against a render; these come from a drawing made for a
    // white page. Look at the horizon before trusting them.
    val VEG = listOf(0xFF9CC77E, 0xFF8ABB6C, 0xFF7BAE60, 0xFF6E9F55, 0xFF5F914B)

    // Water carried the same problem and worse -- a river the colour of the
    // page is not a river. Bright enough now to read as water from the
    // overview, which is where the island's shape is doing the work.
    // Lit with the land. The prototype's `water`, and a step under it, so a
    // river still reads as water rather than as a gap in the field.
    val WATER = listOf(0xFFB7DCE8, 0xFF9CC6D6)

    /**
     * Sparse ground, drawn faintly.
     *
     * The prototype reaches for `CIRCLE_PAINT.length - 1` here, which is
     * evaluated after the patch colours have been appended — so its bare
     * ground silently takes the last person's petal colour. Harmless in a
     * mock with four fixed people; in Harbor the ground would change colour
     * when a contact is added. This is the constant that was meant.
     */
    const val BARE = 0xFFBFAE8C

    /**
     * Where the petal colours start in the palette. Two per flower kind:
     * deep, then light.
     *
     * Bucketed by kind rather than by patch. A patch used to own two buckets
     * and every bloom on it drew from them, which is what made a patch one
     * colour however many different flowers had been chosen for it. There are
     * twenty kinds and they never change, so this is a fixed table that does
     * not depend on how many people are in the field.
     */
    const val PATCH_PAINT_FROM = 8

    private const val BARE_PAINT = 7

    enum class Kind { DOT, CROSS, SQUARE, FLOWER }

    /** One person's planted ground. */
    data class Patch(
        val contactId: UUID?,
        val label: String,
        val calls: Int,
        val x: Double,
        val y: Double,
        val radius: Double,
        val ring: List<Garden.Spot>,
        /**
         * The flower this patch is planted with *most often*, which is what
         * names it on the tag and the card.
         */
        val flower: FlowerKind,
        /**
         * What each flower in it actually is, oldest first, one per call
         * minute -- so entry `k` is the kind of the flower whose [Cell.bloom]
         * is `k`.
         *
         * The patch used to have only the kind above, and every bloom on it
         * was drawn in that one colour. Somebody who had answered the picker
         * differently on twelve calls got twelve identical flowers, which
         * quietly threw away the only thing the picker is for. Short, or
         * empty, falls back to [flower].
         */
        val flowers: List<FlowerKind> = emptyList(),
    )

    /**
     * One cell of the field.
     *
     * There are tens of thousands of these, so it holds numbers and indices
     * rather than objects, and the drawing layer never allocates per cell.
     */
    data class Cell(
        val x: Double,
        val y: Double,
        val z: Double,
        val kind: Kind,
        val size: Double,
        val paint: Int,
        /** Index into the patch list, or -1 for open country. */
        val patch: Int,
        /** Stable per-cell randomness, used for petal spin and colour choice. */
        val tone: Double,
        /**
         * Which flower of its patch this is, counting from the first ever
         * planted there, or -1 for anything that is not a flower.
         *
         * Planting order is a stable rank rather than a clock, so a flower
         * keeps its number as the patch grows around it -- which makes the
         * highest number in a patch the one that arrived most recently, and
         * therefore the place to fly the camera when somebody has just grown
         * it.
         */
        val bloom: Int = -1,
    )

    data class Camera(val x: Double, val y: Double, val zoom: Double)

    /** What the camera works out once per frame, rather than once per cell. */
    data class Lens(
        val tilt: Double,
        val focal: Double,
        val eye: Double,
        val back: Double,
        val camZ: Double,
    )

    /** Somewhere to put a projected point without allocating in the hot loop. */
    class Point {
        @JvmField var x: Double = 0.0
        @JvmField var y: Double = 0.0
        @JvmField var s: Double = 1.0
    }

    /** The zoom at which the whole island just fills the frame. */
    fun overviewZoom(width: Double, height: Double): Double =
        if (width <= 0 || height <= 0) 0.2
        else max(width / Terrain.FIELD_W, height / Terrain.FIELD_H)

    fun clampZoom(zoom: Double, base: Double): Double =
        min(base * MAX_REL, max(base, zoom))

    /** 0 is flat overhead, 1 is full perspective. Everything between is real. */
    fun tiltFor(zoom: Double, base: Double): Double =
        Terrain.smooth((zoom / base - TILT_FROM) / (TILT_TO - TILT_FROM))

    /**
     * The eye for this camera.
     *
     * [Lens.camZ] is sampled under what you are looking at rather than under
     * the viewer, so the framing does not lurch every time the ground beneath
     * you changes height.
     */
    fun buildLens(camera: Camera, base: Double, height: Double): Lens {
        val rel = max(camera.zoom / base, 0.6)
        val eye = max(34.0, EYE * TILT_TO / rel)
        return Lens(
            tilt = tiltFor(camera.zoom, base),
            focal = height * 0.92,
            eye = eye,
            back = eye * SET_BACK,
            camZ = Terrain.heightAt(camera.x, camera.y) * ELEVATION,
        )
    }

    /**
     * World to screen.
     *
     * Projects flat and in perspective, then mixes the two by [Lens.tilt].
     * Mixing the *results* rather than switching between them is what makes
     * the tip continuous — there is no frame where the world jumps.
     */
    fun project(
        wx: Double,
        wy: Double,
        wz: Double,
        camera: Camera,
        lens: Lens,
        width: Double,
        height: Double,
        out: Point,
    ): Point {
        val flatX = width / 2 + (wx - camera.x) * camera.zoom
        val flatY = height * 0.5 + (wy - camera.y) * camera.zoom
        if (lens.tilt <= 0.002) {
            out.x = flatX
            out.y = flatY
            out.s = camera.zoom
            return out
        }
        // Depth. The eye sits at camera.y + back and looks toward decreasing
        // y, so +y runs *toward* the viewer and the far edge of the field is
        // its low-y edge. Easy to get backwards; the floor stops anything at
        // or behind the eye from projecting to infinity.
        val d = max(lens.eye * 0.3, camera.y + lens.back - wy)
        val tx = width / 2 + lens.focal * (wx - camera.x) / d
        val ty = height * HORIZON + lens.focal * (lens.eye + lens.camZ - wz * ELEVATION) / d
        val ts = lens.focal / d
        out.x = flatX + (tx - flatX) * lens.tilt
        out.y = flatY + (ty - flatY) * lens.tilt
        out.s = camera.zoom + (ts - camera.zoom) * lens.tilt
        return out
    }

    // --- patches ----------------------------------------------------------

    /** Where the first few patches are aimed, before [Terrain.settle] adjusts. */
    private val SPOTS = listOf(
        0.40 to 0.66,
        0.68 to 0.74,
        0.55 to 0.42,
        0.82 to 0.55,
    )

    /**
     * A place to aim patch [index] at.
     *
     * The prototype has four people and four hand-placed spots. Harbor does
     * not know how many people there will be, so past the fourth this walks a
     * golden-angle spiral out from the middle — which never repeats and never
     * clusters, and stays deterministic.
     */
    fun spotFor(index: Int): Pair<Double, Double> {
        SPOTS.getOrNull(index)?.let { return it }
        val n = index - SPOTS.size
        val angle = n * 2.399963
        val radius = 0.16 + 0.055 * kotlin.math.sqrt(n + 1.0)
        return (0.5 + kotlin.math.cos(angle) * radius) to
            (0.56 + kotlin.math.sin(angle) * radius * 0.8)
    }

    /**
     * Lay out one patch per person.
     *
     * Radius follows the prototype, then grows with how much has been planted
     * — a patch of one call should not cover the same ground as a patch of
     * twenty. It reaches the prototype's size at six calls, which is where
     * the two agree exactly.
     */
    fun patches(people: List<Person>): List<Patch> = people.mapIndexed { i, person ->
        val (fx, fy) = spotFor(i)
        val at = Terrain.settle(fx * Terrain.FIELD_W, fy * Terrain.FIELD_H)
        val seed = 4000 + i * 37
        val full = 165 + Terrain.hash2(i, 3, seed) * 60
        val growth = 0.72 + 0.28 * min(1.0, person.calls / 6.0)
        val radius = full * growth
        Patch(
            contactId = person.contactId,
            label = person.label,
            calls = person.calls,
            x = at.x,
            y = at.y,
            radius = radius,
            ring = Terrain.blobRing(at.x, at.y, radius, seed),
            flower = person.flower,
            flowers = person.flowers,
        )
    }

    /** A person, as the field needs them. */
    data class Person(
        val contactId: UUID?,
        val label: String,
        val calls: Int,
        /** What this patch is planted with — the kind chosen most often here. */
        val flower: FlowerKind,
        /** Every flower here, oldest first. See [Patch.flowers]. */
        val flowers: List<FlowerKind> = emptyList(),
    )

    /**
     * Where a garden with nothing in it opens.
     *
     * Not the overview. An empty island seen from above is a map of nothing —
     * it reads as a screen that failed to load. Standing on good ground
     * instead, close enough to see the grass, it reads as somewhere with room
     * in it, which is the honest description of a garden nobody has planted
     * yet.
     *
     * [Terrain.settle] is what keeps this out of the river.
     */
    fun emptyStart(): Garden.Spot =
        Terrain.settle(Terrain.FIELD_W * 0.52, Terrain.FIELD_H * 0.60)

    /** How far in an empty garden stands. Past [TILT_TO], so it is landscape. */
    const val EMPTY_ZOOM = 6.5

    /**
     * How far in the field stands when it is standing at a flower.
     *
     * [EMPTY_ZOOM] is the right distance for empty ground -- close enough to
     * read the grass, far enough to see there is room. It is the wrong one for
     * a bloom: at 6.5x a flower is about eleven pixels across, which is a dot
     * with petals rather than the thing somebody just grew. At this distance
     * it is about fifty, which is four and a half times the area and is as
     * close as the field goes -- the last tenth of [MAX_REL] is left so a
     * pinch inward still does something.
     *
     * Fifty pixels is the ceiling, not a choice: a bloom is about three world
     * units across on a fifteen-unit grid, so how large it can ever draw is
     * set by [MAX_REL]. That is the dial to turn if this is still too small.
     */
    const val BLOOM_ZOOM = MAX_REL * 0.9

    /**
     * Somewhere a flower could grow, and the order in which it would.
     *
     * Collected on the one pass over the grid so the planting afterwards
     * needs no second look at the terrain.
     */
    private class Candidate(
        /** Index into the cell list, so the cell can be planted in place. */
        val at: Int,
        /** Low ranks are planted first, and a cell's rank never changes. */
        val rank: Double,
        val tone: Double,
        val size: Double,
    )

    /**
     * Where the last flower planted in a patch stands, or null if none has.
     *
     * The camera's target after somebody grows one. The patch centre is not
     * good enough: at standing zoom a well-planted patch is wider than the
     * screen, so aiming at the middle can leave the new bloom off the edge.
     */
    fun newestBloom(cells: List<Cell>, patch: Int): Cell? {
        var best: Cell? = null
        for (c in cells) {
            if (c.kind != Kind.FLOWER || c.patch != patch) continue
            if (best == null || c.bloom > best.bloom) best = c
        }
        return best
    }

    /**
     * Plant exactly one flower per call, in rank order.
     *
     * This used to be a probability: each cell in a patch bloomed if its own
     * hash fell under `calls / cells-in-patch`. The average came out right and
     * every individual case did not -- a patch of one call had rather worse
     * than even odds of growing anything at all, so better than a third of
     * first flowers simply did not exist, and two or three calls in you were
     * hunting a couple of cells in two hundred. The first flower is the whole
     * of somebody's first week; it cannot be a coin toss.
     *
     * Ranking instead of rolling costs one sort per patch and gives an exact
     * count, still scattered, still identical on every redraw, and still
     * stable as the patch grows: flower five does not move when flower six
     * arrives, because six is simply the next rank down the list.
     *
     * Capped at three quarters of the patch rather than all of it. A patch
     * where every cell is a flower stops reading as flowers and starts
     * reading as a coloured field, and somebody with two hundred calls has
     * earned a full patch, not a solid one.
     */
    private fun plant(
        out: ArrayList<Cell>,
        patches: List<Patch>,
        candidates: Array<ArrayList<Candidate>>,
    ) {
        for (i in patches.indices) {
            val calls = patches[i].calls
            if (calls <= 0) continue
            val here = candidates[i]
            if (here.isEmpty()) continue
            val room = max(1, (here.size * 0.75).toInt())
            val n = min(calls, room)
            here.sortBy { it.rank }
            for (k in 0 until n) {
                val seed = here[k]
                out[seed.at] = out[seed.at].copy(
                    kind = Kind.FLOWER,
                    size = seed.size,
                    // Each bloom in the kind somebody chose for that call, not
                    // in the patch's one colour. The tone still picks between
                    // that kind's light and deep petal, which is what keeps a
                    // run of the same answer from reading as a flat blob.
                    paint = paintFor(kindOf(patches[i], k), deep = seed.tone <= 0.55),
                    bloom = k,
                )
            }
        }
    }

    /** Which patch contains a point, or -1. */
    fun patchAt(patches: List<Patch>, px: Double, py: Double): Int {
        for (i in patches.indices) {
            val p = patches[i]
            if (abs(px - p.x) > p.radius * 1.5 || abs(py - p.y) > p.radius * 1.5) continue
            if (Terrain.inRing(p.ring, px, py)) return i
        }
        return -1
    }

    /** Which bucket a flower of this kind fills. */
    fun paintFor(kind: FlowerKind, deep: Boolean): Int =
        PATCH_PAINT_FROM + kind.ordinal * 2 + (if (deep) 0 else 1)

    /** What kind flower number [bloom] of a patch is. */
    fun kindOf(patch: Patch, bloom: Int): FlowerKind =
        patch.flowers.getOrNull(bloom) ?: patch.flower

    /** The full palette: the shared ground colours, then two per flower kind. */
    fun palette(): LongArray {
        val out = LongArray(PATCH_PAINT_FROM + FlowerKind.entries.size * 2)
        VEG.forEachIndexed { i, c -> out[i] = c }
        WATER.forEachIndexed { i, c -> out[VEG.size + i] = c }
        out[BARE_PAINT] = BARE
        FlowerKind.entries.forEach { kind ->
            val spec = Flowers.spec(kind)
            out[paintFor(kind, deep = true)] = spec.petalDeep
            out[paintFor(kind, deep = false)] = spec.petal
        }
        return out
    }

    /** How opaque a bucket draws. Sparse ground recedes; planted ground does not. */
    fun alphaFor(paint: Int): Float = when {
        paint == BARE_PAINT -> 0.62f
        paint >= PATCH_PAINT_FROM -> 0.95f
        else -> 0.9f
    }

    // --- the cells --------------------------------------------------------

    /**
     * How many cells on a side make one block.
     *
     * The field is built block by block rather than row by row so that a frame
     * can skip whole blocks instead of projecting every cell to find out it is
     * off screen. See [Built] and [onScreen].
     *
     * Twelve is a compromise between the two costs it sits between. Bigger
     * blocks mean fewer corner projections per frame but coarser skipping, so
     * more cells survive the test and get projected for nothing; smaller
     * blocks skip tightly but the per-block test starts to cost what it saves.
     * At twelve the whole grid is about a hundred and fifty blocks, which is
     * around a thousand corner projections a frame against the forty thousand
     * cells it is deciding about.
     */
    const val BLOCK = 12

    /**
     * One square of the grid, and the run of [Built.cells] that falls in it.
     *
     * [zTop] is the highest ground in the block. The test projects the block's
     * corners at both the floor and that height, because a block low on the
     * screen can still push cells up into view over a rise.
     */
    data class Block(
        val x0: Double,
        val y0: Double,
        val x1: Double,
        val y1: Double,
        val zTop: Double,
        val from: Int,
        val to: Int,
    )

    /** The field, and the index that lets a frame draw only part of it. */
    data class Built(val cells: List<Cell>, val blocks: List<Block>)

    /**
     * Whether any of [block] could land on a [width] by [height] screen.
     *
     * Conservative on purpose: it projects the eight corners of the block's
     * box and asks whether that rectangle touches the screen. The projection
     * maps straight lines to straight lines and [project] floors the depth, so
     * nothing folds behind the eye and the corners really do bound the block.
     *
     * [margin] is the same slack the per-cell test uses, so a cell whose dot
     * overhangs the edge is not dropped by the block test first.
     */
    fun onScreen(
        block: Block,
        camera: Camera,
        lens: Lens,
        width: Double,
        height: Double,
        margin: Double,
        point: Point,
    ): Boolean {
        var minX = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        for (corner in 0 until 8) {
            val x = if (corner and 1 == 0) block.x0 else block.x1
            val y = if (corner and 2 == 0) block.y0 else block.y1
            val z = if (corner and 4 == 0) 0.0 else block.zTop
            project(x, y, z, camera, lens, width, height, point)
            if (point.x < minX) minX = point.x
            if (point.x > maxX) maxX = point.x
            if (point.y < minY) minY = point.y
            if (point.y > maxY) maxY = point.y
        }
        return maxX >= -margin && minX <= width + margin &&
            maxY >= -margin && minY <= height + margin
    }

    /**
     * Build the whole field, once.
     *
     * Tens of thousands of cells, each decided by the terrain under it: water
     * where it is low, rock where it is high and steep, groves where growth
     * clumps, tilled rows where the ground is worked, and flowers wherever
     * somebody's patch covers it. Everything is a pure function of position,
     * so this is rebuilt rather than stored, and always comes out the same.
     */
    fun cells(patches: List<Patch>): List<Cell> = build(patches).cells

    /**
     * The same field, with its blocks.
     *
     * Cells come out grouped by block rather than in rows. Nothing about a
     * cell changes — [plant] ranks by a per-cell hash and [newestBloom] reads
     * a counter, so neither depends on the order — and blocks are emitted in
     * rows of increasing y, which keeps the far-to-near order the drawing
     * relies on to let near ground overlap far ground.
     */
    fun build(patches: List<Patch>): Built {
        val out = ArrayList<Cell>(Terrain.COLS * Terrain.ROWS / 2)
        val blocks = ArrayList<Block>()
        val candidates = Array(patches.size) { ArrayList<Candidate>() }
        for (blockRow in 0 until (Terrain.ROWS + BLOCK - 1) / BLOCK) {
            for (blockCol in 0 until (Terrain.COLS + BLOCK - 1) / BLOCK) {
                val rowFrom = blockRow * BLOCK
                val rowTo = min(Terrain.ROWS, rowFrom + BLOCK)
                val colFrom = blockCol * BLOCK
                val colTo = min(Terrain.COLS, colFrom + BLOCK)
                val from = out.size
                var zTop = 0.0
                for (row in rowFrom until rowTo) {
                    for (col in colFrom until colTo) {
                val jx = (Terrain.hash2(col, row, Terrain.SEED + 5) - 0.5) * Terrain.CELL * 0.5
                val jy = (Terrain.hash2(col, row, Terrain.SEED + 6) - 0.5) * Terrain.CELL * 0.5
                val x = col * Terrain.CELL + jx
                val y = row * Terrain.CELL + jy
                if (Terrain.landAt(x, y) < 0.2) continue

                val z = Terrain.heightAt(x, y)
                val m = Terrain.moistureAt(x, y)
                val grove = Terrain.grovesAt(x, y)
                val till = Terrain.tilledAt(x, y)
                val slope = abs(z - Terrain.heightAt(x + Terrain.CELL, y)) +
                    abs(z - Terrain.heightAt(x, y + Terrain.CELL))
                val chance = Terrain.hash2(col, row, Terrain.SEED + 7)
                val patch = patchAt(patches, x, y)

                var kind = Kind.DOT
                var size = 0.1
                var paint = BARE_PAINT

                // Ground somebody could plant in, and where it stands in the
                // queue. Nothing blooms here yet -- [plant] does that once the
                // whole grid is known, because how many of these cells a patch
                // is entitled to depends on how many of them there turn out to
                // be. A contact who has never called anybody still grows
                // nothing: the garden is the whole reward, and it was once
                // given away for free to anyone who added a contact.
                if (patch >= 0 && z > 0.3) {
                    candidates[patch] += Candidate(
                        at = out.size,
                        rank = Terrain.hash2(col, row, Terrain.SEED + 13),
                        tone = Terrain.hash2(col, row, Terrain.SEED + 11),
                        // Inside somebody's patch the ground is planted:
                        // denser, larger, and in their flower's colour.
                        //
                        // Carried up by half when [DOT_SCALE] came down by a
                        // third, so a bloom is drawn at exactly the pixels it
                        // always was. The ground wanted finer marks because
                        // there are more of them now; a flower did not. It is
                        // one flower for one call whatever the grid is doing,
                        // and shrinking it would have made the first week's
                        // reward quietly smaller.
                        size = min(3.6, 1.08 + chance * 1.35 + grove * 0.75),
                    )
                }

                if (z < 0.3) {
                    size = 0.5 + (0.3 - z) * 2.4
                    paint = 5 + (if (chance > 0.5) 1 else 0)
                } else if (z < 0.35) {
                    size = 0.3
                    paint = 5
                } else if (z > 0.74 && slope > 0.026) {
                    kind = Kind.SQUARE
                    size = 0.34 + slope * 3
                } else if (grove > 0.5) {
                    size = 0.6 + (grove - 0.5) * 3.4 + m * 0.6
                    paint = min(4.0, floor(z * 3.6 + chance * 1.4)).toInt()
                } else if (m > 0.44) {
                    size = 0.26 + (m - 0.44) * 2.4
                    paint = min(4.0, floor(z * 3.2 + chance)).toInt()
                } else if (till > 0.55 && till < 0.67 && z < 0.7) {
                    kind = Kind.CROSS
                    size = 0.4
                } else if (chance > 0.987 && z > 0.36) {
                    kind = Kind.SQUARE
                    size = 0.3
                } else {
                    size = 0.13 + m * 0.2
                    paint = BARE_PAINT
                }

                out += Cell(x, y, z, kind, min(size, 2.4), paint, patch, chance)
                if (z > zTop) zTop = z
                    }
                }
                // A block with no land in it still gets an entry, with an
                // empty run. Dropping them would make the list's own indices
                // stop meaning anything, and an empty run costs one test.
                blocks += Block(
                    x0 = colFrom * Terrain.CELL,
                    y0 = rowFrom * Terrain.CELL,
                    x1 = colTo * Terrain.CELL,
                    y1 = rowTo * Terrain.CELL,
                    zTop = zTop,
                    from = from,
                    to = out.size,
                )
            }
        }
        plant(out, patches, candidates)
        return Built(out, blocks)
    }
}
