package app.harbor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * The field's terrain, layout and camera, checked as arithmetic.
 *
 * This is the half of the port that a screenshot cannot check: whether the
 * island is an island, whether the river actually cuts a valley, whether the
 * camera tips continuously rather than snapping, and whether the whole thing
 * comes out the same twice.
 */
class FieldTest {

    private val mom = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val dad = UUID.fromString("22222222-2222-2222-2222-222222222222")

    private fun people(vararg calls: Int) = calls.mapIndexed { i, n ->
        Field.Person(
            contactId = if (i == 0) mom else dad,
            label = if (i == 0) "Mom" else "Dad",
            calls = n,
            flower = FlowerKind.entries[i % FlowerKind.entries.size],
        )
    }

    // --- what is growing, and what is not ------------------------------------

    private fun flowersIn(calls: Int): Int =
        Field.cells(Field.patches(people(calls))).count { it.kind == Field.Kind.FLOWER }

    @Test
    fun `a patch nobody has called has nothing growing in it`() {
        // The garden is the entire reward. A contact who exists but has never
        // been called used to open onto a patch in full bloom, which gave away
        // for nothing the one thing the app has to give.
        assertEquals(0, flowersIn(0))
    }

    @Test
    fun `one call grows one flower, exactly`() {
        // This used to be a probability per cell that averaged out right and
        // got individual cases wrong: a patch of one call had rather worse
        // than even odds of growing anything at all. Scattered, still, but
        // counted rather than rolled.
        assertEquals(1, flowersIn(1))
        assertEquals(2, flowersIn(2))
        assertEquals(4, flowersIn(4))
    }

    private fun placed(calls: Int) = Field.cells(Field.patches(people(calls)))
        .filter { it.kind == Field.Kind.FLOWER }
        .sortedBy { it.bloom }
        .map { it.x to it.y }

    @Test
    fun `a flower keeps its place once the patch has stopped growing`() {
        // Planting order is a rank, not a clock, so the eighth flower is the
        // next cell down a list the first seven already sat at the top of. A
        // flower that moved when the next one arrived would make the field a
        // picture of the count rather than a record of the calls.
        //
        // Seven and eight, not five and six: the ring stops growing at six
        // calls (Field.patches), and until it does this does not hold. See
        // the test below, which is the one that says so.
        assertEquals(placed(7), placed(8).take(7))
    }

    @Test
    fun `while the patch is still growing, an earlier flower can move`() {
        // Not a property anybody wants -- it is the current behaviour, pinned
        // so that changing it is a decision rather than an accident.
        //
        // `growth` in Field.patches widens the ring until the sixth call, so
        // each of the first six calls admits ground the ranking had never
        // seen. A newly admitted cell can outrank every flower already
        // standing, take bloom 0, and push the rest along -- which reads, to
        // the person whose garden it is, as their first flowers moving.
        //
        // It only bites below the cap, which is also the week a participant is
        // most likely to be looking. Fixing it means ranking against the pool
        // as it will be at full growth rather than as it is today.
        val five = placed(5)
        val six = placed(6)
        assertTrue(
            "the first five flowers no longer move when the sixth arrives -- " +
                "if this is now stable, that is the fix, and the test above " +
                "should take over",
            five != six.take(5),
        )
    }

    @Test
    fun `the newest flower is a place the camera can go`() {
        // What the field flies to after somebody grows one. The patch centre
        // is not it: at standing zoom a well-planted patch is wider than the
        // screen, so its middle can have the new bloom off the edge.
        val patches = Field.patches(people(9))
        val cells = Field.cells(patches)
        val newest = Field.newestBloom(cells, 0)
        assertEquals(8, newest?.bloom)
        assertTrue("the newest flower grew outside its patch", newest!!.patch == 0)
        assertTrue(
            "the newest flower is not on its patch's ground",
            Terrain.inRing(patches[0].ring, newest.x, newest.y),
        )
        assertEquals(null, Field.newestBloom(Field.cells(Field.patches(people(0))), 0))
    }

    @Test
    fun `more calls grow more flowers`() {
        assertTrue(flowersIn(40) > flowersIn(4))
        assertTrue(flowersIn(4) > flowersIn(0))
    }

    @Test
    fun `a patch never fills solid`() {
        // Somebody with a year of calls has earned a full patch, not a
        // coloured rectangle: past about three quarters it stops reading as
        // flowers at all.
        val patches = Field.patches(people(100_000))
        val cells = Field.cells(patches)
        val inside = cells.count { Field.patchAt(patches, it.x, it.y) >= 0 }
        val bloomed = cells.count { it.kind == Field.Kind.FLOWER }
        assertTrue("$bloomed of $inside cells bloomed", bloomed < inside)
    }

    // --- noise --------------------------------------------------------------

    @Test
    fun `the hash is stable and stays in range`() {
        repeat(200) { i ->
            val v = Terrain.hash2(i, i * 7, Terrain.SEED)
            assertTrue("hash out of range: $v", v >= 0.0 && v < 1.0)
            assertEquals(v, Terrain.hash2(i, i * 7, Terrain.SEED), 0.0)
        }
    }

    @Test
    fun `the hash does not collapse for negative or huge coordinates`() {
        // 32-bit wrap-around is load-bearing here; widening it would change
        // the island, and collapsing it would tile the world.
        val seen = HashSet<Double>()
        listOf(-9999, -1, 0, 1, 65536, 1 shl 20).forEach { x ->
            listOf(-9999, -1, 0, 1, 65536).forEach { y ->
                seen += Terrain.hash2(x, y, Terrain.SEED)
            }
        }
        assertTrue("hash collapsed: only ${seen.size} distinct values", seen.size >= 28)
    }

    @Test
    fun `fbm stays inside the unit range`() {
        for (i in 0 until 300) {
            val v = Terrain.fbm(i * 0.37, i * 0.11, Terrain.SEED, 5)
            assertTrue("fbm out of range: $v", v in 0.0..1.0)
        }
    }

    // --- terrain ------------------------------------------------------------

    @Test
    fun `the field is an island, not a rectangle`() {
        val middle = Terrain.landAt(Terrain.FIELD_W / 2, Terrain.FIELD_H / 2)
        val corner = Terrain.landAt(0.0, 0.0)
        assertTrue("the middle should be land, was $middle", middle > 0.3)
        assertTrue("the corner should be sea, was $corner", corner < 0.2)
    }

    @Test
    fun `height is always a fraction`() {
        for (row in 0 until Terrain.ROWS step 7) {
            for (col in 0 until Terrain.COLS step 7) {
                val z = Terrain.heightAt(col * Terrain.CELL, row * Terrain.CELL)
                assertTrue("height out of range: $z", z in 0.0..1.0)
            }
        }
    }

    @Test
    fun `the river cuts a valley rather than running over a hill`() {
        var checked = 0
        var y = 200.0
        while (y < Terrain.FIELD_H - 200) {
            val x = Terrain.riverAt(y)
            val onRiver = Terrain.heightAt(x, y)
            val away = Terrain.heightAt(x + 300, y)
            if (Terrain.landAt(x, y) > 0.25 && Terrain.landAt(x + 300, y) > 0.25) {
                assertTrue(
                    "river at y=$y sat above its bank ($onRiver vs $away)",
                    onRiver <= away + 1e-9,
                )
                checked++
            }
            y += 150
        }
        assertTrue("no river samples were on land at all", checked > 3)
    }

    @Test
    fun `settle finds ground worth planting`() {
        val at = Terrain.settle(Terrain.FIELD_W * 0.4, Terrain.FIELD_H * 0.66)
        assertTrue("settled into the sea", Terrain.landAt(at.x, at.y) >= 0.26)
    }

    @Test
    fun `a patch outline is never a circle`() {
        val ring = Terrain.blobRing(0.0, 0.0, 100.0, 4000)
        val radii = ring.map { kotlin.math.hypot(it.x, it.y) }
        assertTrue("outline was a circle", radii.max() - radii.min() > 12)
        assertEquals(ring, Terrain.blobRing(0.0, 0.0, 100.0, 4000))
    }

    @Test
    fun `a point inside the outline reads as inside`() {
        val ring = Terrain.blobRing(500.0, 500.0, 120.0, 77)
        assertTrue(Terrain.inRing(ring, 500.0, 500.0))
        assertTrue(!Terrain.inRing(ring, 5000.0, 5000.0))
    }

    // --- patches ------------------------------------------------------------

    @Test
    fun `a patch grows with what has been planted in it`() {
        val small = Field.patches(people(1)).first()
        val large = Field.patches(people(20)).first()
        assertTrue("planting more did not widen the patch", large.radius > small.radius)
    }

    @Test
    fun `patches stop growing once they reach the prototype's size`() {
        val six = Field.patches(people(6)).first().radius
        val many = Field.patches(people(400)).first().radius
        assertEquals(six, many, 1e-9)
    }

    @Test
    fun `two people do not land on the same ground`() {
        val both = Field.patches(people(5, 5))
        val d = kotlin.math.hypot(both[0].x - both[1].x, both[0].y - both[1].y)
        assertTrue("patches overlap: centres $d apart", d > both[0].radius)
    }

    @Test
    fun `every person gets a place, however many there are`() {
        val many = Field.patches(List(12) {
            Field.Person(UUID.randomUUID(), "P$it", 3, FlowerKind.GLAD_WE_TALKED)
        })
        assertEquals(12, many.size)
        many.forEach {
            assertTrue("a patch fell off the world", it.x > -500 && it.x < Terrain.FIELD_W + 500)
        }
    }

    @Test
    fun `the palette has shared colours first and two per flower kind after`() {
        // A bucket per kind, not per patch. A patch owning its own two
        // buckets is what made every bloom on it the same colour however
        // differently the picker had been answered.
        val palette = Field.palette()
        assertEquals(Field.PATCH_PAINT_FROM + FlowerKind.entries.size * 2, palette.size)
        FlowerKind.entries.forEach { kind ->
            val spec = Flowers.spec(kind)
            assertEquals(spec.petalDeep, palette[Field.paintFor(kind, deep = true)])
            assertEquals(spec.petal, palette[Field.paintFor(kind, deep = false)])
        }
        assertTrue("the palette must not leave a colour undefined", palette.all { it != 0L })
    }

    @Test
    fun `a bloom is the kind that was chosen on the call that grew it`() {
        // The whole of the picker: twelve calls answered twelve ways used to
        // come out as twelve identical flowers.
        val chosen = listOf(
            FlowerKind.DREADED_THIS_ONE,
            FlowerKind.FELT_LOVED,
            FlowerKind.EASY_SILENCE,
            FlowerKind.SAID_THE_HARD_THING,
        )
        val patches = Field.patches(listOf(
            Field.Person(mom, "Mom", chosen.size, FlowerKind.FELT_LOVED, chosen),
        ))
        val grew = Field.cells(patches)
            .filter { it.kind == Field.Kind.FLOWER }
            .sortedBy { it.bloom }
        assertEquals(chosen, grew.map { Field.kindOf(patches[0], it.bloom) })
        grew.forEach {
            val spec = Flowers.spec(Field.kindOf(patches[0], it.bloom))
            val colour = Field.palette()[it.paint]
            assertTrue(
                "a bloom drew in a colour that is not its own kind's",
                colour == spec.petal || colour == spec.petalDeep,
            )
        }
        assertEquals(4, grew.map { it.paint }.distinct().size)
    }

    @Test
    fun `a patch with more calls than answers falls back to its own kind`() {
        // A long call grows a run of flowers off one answer, and the list can
        // simply be short. Neither may leave a bloom without a colour.
        val patches = Field.patches(listOf(
            Field.Person(mom, "Mom", 6, FlowerKind.STEADIER_NOW, listOf(FlowerKind.LIGHTER_NOW)),
        ))
        assertEquals(FlowerKind.LIGHTER_NOW, Field.kindOf(patches[0], 0))
        assertEquals(FlowerKind.STEADIER_NOW, Field.kindOf(patches[0], 5))
        val palette = Field.palette()
        Field.cells(patches).forEach {
            assertTrue("paint ${it.paint} is past the palette", it.paint in palette.indices)
        }
    }

    @Test
    fun `sparse ground is faint and planted ground is not`() {
        assertTrue(Field.alphaFor(7) < Field.alphaFor(0))
        assertTrue(Field.alphaFor(Field.PATCH_PAINT_FROM) > Field.alphaFor(0))
    }

    // --- cells --------------------------------------------------------------

    @Test
    fun `the field comes out the same twice`() {
        val patches = Field.patches(people(6, 3))
        val a = Field.cells(patches)
        val b = Field.cells(patches)
        assertEquals(a.size, b.size)
        assertEquals(a.first(), b.first())
        assertEquals(a.last(), b.last())
    }

    @Test
    fun `nothing is drawn out at sea`() {
        Field.cells(Field.patches(people(6, 3))).forEach {
            assertTrue("a cell was placed on water", Terrain.landAt(it.x, it.y) >= 0.2)
        }
    }

    @Test
    fun `flowers only grow inside somebody's patch`() {
        val patches = Field.patches(people(6, 3))
        Field.cells(patches).forEach {
            if (it.kind == Field.Kind.FLOWER) {
                assertTrue("a flower grew in open country", it.patch >= 0)
                assertTrue(
                    "a flower took a colour that is not its patch's",
                    it.paint >= Field.PATCH_PAINT_FROM,
                )
            }
        }
    }

    @Test
    fun `every cell points at a colour the palette actually has`() {
        val patches = Field.patches(people(6, 3))
        val palette = Field.palette()
        Field.cells(patches).forEach {
            assertTrue("paint ${it.paint} is past the palette", it.paint in palette.indices)
        }
    }

    @Test
    fun `a field with nobody in it is still a field`() {
        val cells = Field.cells(emptyList())
        assertTrue("the island vanished without people on it", cells.size > 1000)
        assertTrue(cells.none { it.kind == Field.Kind.FLOWER })
    }

    // --- camera -------------------------------------------------------------

    @Test
    fun `the overview zoom fits the whole island`() {
        val base = Field.overviewZoom(1080.0, 2000.0)
        assertTrue(Terrain.FIELD_W * base >= 1080.0 - 1)
    }

    @Test
    fun `zoom cannot go below the overview or past the limit`() {
        val base = 0.5
        assertEquals(base, Field.clampZoom(0.001, base), 1e-9)
        assertEquals(base * Field.MAX_REL, Field.clampZoom(9999.0, base), 1e-9)
    }

    @Test
    fun `the view is flat until it starts tipping, and fully tipped after`() {
        val base = 0.5
        assertEquals(0.0, Field.tiltFor(base * 1.0, base), 1e-9)
        assertEquals(0.0, Field.tiltFor(base * Field.TILT_FROM, base), 1e-9)
        assertEquals(1.0, Field.tiltFor(base * Field.TILT_TO, base), 1e-9)
        assertEquals(1.0, Field.tiltFor(base * 30, base), 1e-9)
    }

    @Test
    fun `the tip is continuous, never a jump`() {
        val base = 0.5
        var last = 0.0
        var step = Field.TILT_FROM
        while (step <= Field.TILT_TO) {
            val tilt = Field.tiltFor(base * step, base)
            assertTrue("tilt went backwards", tilt >= last - 1e-9)
            assertTrue("tilt jumped by ${tilt - last}", tilt - last < 0.2)
            last = tilt
            step += 0.05
        }
    }

    @Test
    fun `an untipped camera is a plain overhead map`() {
        val cam = Field.Camera(Terrain.FIELD_W / 2, Terrain.FIELD_H / 2, 0.5)
        val lens = Field.buildLens(cam, 0.5, 1080.0, 2000.0)
        val out = Field.Point()
        Field.project(cam.x + 100, cam.y, 0.5, cam, lens, 1080.0, 2000.0, out)
        assertEquals(0.0, lens.tilt, 1e-9)
        assertEquals(1080.0 / 2 + 100 * 0.5, out.x, 1e-6)
        assertEquals(cam.zoom, out.s, 1e-9)
    }

    @Test
    fun `once tipped, further away is smaller`() {
        val base = 0.5
        val cam = Field.Camera(Terrain.FIELD_W / 2, Terrain.FIELD_H / 2, base * Field.TILT_TO)
        val lens = Field.buildLens(cam, base, 1080.0, 2000.0)
        val near = Field.Point()
        val far = Field.Point()
        // The eye sits behind the camera at increasing y, so depth is
        // cam.y + back - wy: a *smaller* y is further away, not a larger one.
        Field.project(cam.x, cam.y + 200, 0.4, cam, lens, 1080.0, 2000.0, near)
        Field.project(cam.x, cam.y - 700, 0.4, cam, lens, 1080.0, 2000.0, far)
        assertTrue("perspective did not shrink with distance", far.s < near.s)
        assertTrue("the far point should sit higher up the screen", far.y < near.y)
    }

    @Test
    fun `a zero sized viewport does not divide by it`() {
        assertEquals(0.2, Field.overviewZoom(0.0, 0.0), 1e-9)
    }

    // --- the grass ----------------------------------------------------------

    @Test
    fun `a tuft is the same tuft every time it is drawn`() {
        // Grass is rebuilt from scratch on every frame. If it were not a pure
        // function of the cell it stands on, it would crawl as you panned.
        for (n in 0 until 6) {
            assertEquals(Field.wisp(0.42, n), Field.wisp(0.42, n), 0.0)
        }
        assertTrue(Field.wisp(0.42, 0) != Field.wisp(0.43, 0))
    }

    @Test
    fun `a blade's randomness stays inside the unit it is scaled by`() {
        // Every use multiplies this by a length. Outside nought-to-one a blade
        // grows backwards or off into the next patch.
        for (tone in listOf(0.0, 0.17, 0.5, 0.83, 1.0)) {
            for (n in 0 until 12) {
                val w = Field.wisp(tone, n)
                assertTrue("wisp($tone, $n) = $w", w >= 0.0 && w < 1.0)
            }
        }
    }

    @Test
    fun `blades in one tuft do not march in step`() {
        // The obvious cheap hash -- frac(tone * n * k) -- steps by a constant
        // as n goes up, so the blades of a tuft lean progressively further and
        // a whole field of grass comes out as identical diagonal combs. It
        // passes every other test here, and it is only wrong to look at.
        for (tone in listOf(0.07, 0.31, 0.5, 0.86)) {
            val steps = (0 until 7).map { n ->
                val d = Field.wisp(tone, n + 1) - Field.wisp(tone, n)
                d - kotlin.math.floor(d)
            }
            val spread = steps.max() - steps.min()
            assertTrue("tone $tone steps evenly: $steps", spread > 0.2)
        }
    }

    @Test
    fun `looking straight down at the ground grows no grass`() {
        // A blade is drawn as a sliver going up the screen, which is a side
        // view of a thing. In plan there is no up for it to go, and the first
        // version grew grass over the whole overview -- thickest along the far
        // edge, because the high ground at the back carries the bigger cells.
        val h = 2000.0
        for (y in listOf(0.0, 500.0, 1200.0, 1999.0)) {
            assertEquals(0.0, Field.grassStand(0.0, y, h), 1e-9)
            assertEquals(0.0, Field.grassStand(Field.GRASS_TILT, y, h), 1e-9)
        }
    }

    @Test
    fun `grass thickens toward the near edge of a tipped view`() {
        val h = 2000.0
        val far = Field.grassStand(1.0, h * 0.20, h)
        val mid = Field.grassStand(1.0, h * 0.60, h)
        val near = Field.grassStand(1.0, h * 0.98, h)
        assertEquals("ground up by the horizon is a long way off", 0.0, far, 1e-9)
        assertTrue("grass should thicken toward the viewer", mid < near)
        assertTrue(near <= 1.0)
    }

    @Test
    fun `grass comes up as the view tips, rather than arriving`() {
        // Any step here is a line of grass switching on across the whole
        // screen at one zoom, which is the pop the whole ladder exists to
        // avoid. Monotonic and continuous from the gate to full tilt.
        val h = 2000.0
        var last = -1.0
        var t = Field.GRASS_TILT
        while (t <= 1.0001) {
            val now = Field.grassStand(t, h * 0.9, h)
            assertTrue("stand went backwards at tilt $t", now >= last - 1e-9)
            assertTrue("stand jumped at tilt $t", last < 0 || now - last < 0.08)
            last = now
            t += 0.02
        }
        assertTrue("full tilt near the viewer should be full grass", last > 0.8)
    }

    @Test
    fun `a viewport with no height does not divide by it`() {
        assertEquals(0.0, Field.grassStand(1.0, 100.0, 0.0), 1e-9)
    }

    @Test
    fun `the detail ladder only ever goes up`() {
        // Dot, then drawn flower, then the artwork. Each rung has to sit above
        // the one under it or a bloom would reach a rung it can never leave.
        assertTrue(Field.GRASS_AT < Field.FLOWER_AT)
        assertTrue(Field.FLOWER_AT < Field.ARTWORK_AT)
        assertTrue(Field.ARTWORK_FADE > 0.0)
    }

    // --- the blocks a frame skips by -----------------------------------------

    @Test
    fun `the blocks tile the cells exactly once`() {
        val built = Field.build(Field.patches(people(40, 12)))
        var next = 0
        for (block in built.blocks) {
            assertEquals("blocks run in order and leave no gap", next, block.from)
            assertTrue(block.to >= block.from)
            next = block.to
        }
        assertEquals("every cell belongs to one block", built.cells.size, next)
    }

    @Test
    fun `a cell sits inside the block that claims it`() {
        val built = Field.build(Field.patches(people(40, 12)))
        // Cells are jittered off their grid point by up to half a cell, so the
        // bounds a block has to cover are its own plus that slack.
        val slack = Terrain.CELL * 0.5
        for (block in built.blocks) {
            for (i in block.from until block.to) {
                val c = built.cells[i]
                assertTrue(c.x >= block.x0 - slack && c.x <= block.x1 + slack)
                assertTrue(c.y >= block.y0 - slack && c.y <= block.y1 + slack)
                assertTrue("zTop has to cover the block", c.z <= block.zTop + 1e-9)
            }
        }
    }

    /**
     * The one that matters.
     *
     * Skipping a block must never skip a cell that would have been drawn. A
     * cheap test that is wrong does not cost frames, it puts holes in the
     * ground — and holes at one zoom and not another are exactly the kind of
     * thing nobody notices until a participant's garden is missing.
     */
    @Test
    fun `no visible cell is ever inside a skipped block`() {
        val built = Field.build(Field.patches(people(40, 12)))
        val w = 1080.0
        val h = 2160.0
        val base = Field.overviewZoom(w, h)
        val point = Field.Point()
        // Overview, either side of the tip, and hard in: the framings where
        // the projection behaves differently from one another.
        for (rel in listOf(1.0, 2.5, 4.5, 12.0, Field.MAX_REL)) {
            val cam = Field.Camera(
                x = Terrain.FIELD_W * 0.45,
                y = Terrain.FIELD_H * 0.55,
                zoom = base * rel,
            )
            val lens = Field.buildLens(cam, base, w, h)
            for (block in built.blocks) {
                if (Field.onScreen(block, cam, lens, w, h, 26.0, point)) continue
                for (i in block.from until block.to) {
                    val c = built.cells[i]
                    Field.project(c.x, c.y, c.z, cam, lens, w, h, point)
                    val drawn = point.x >= -26 && point.x <= w + 26 &&
                        point.y >= -26 && point.y <= h + 26
                    assertTrue("a drawn cell sat in a skipped block at rel $rel", !drawn)
                }
            }
        }
    }

    // --- the ground stirring as you move over it -----------------------------

    @Test
    fun `a still camera stirs nothing at all`() {
        // The one that keeps the garden a place rather than an aquarium. If
        // this ever returns more than nought, the ground simmers forever.
        assertEquals(0.0, Field.stirAmount(0.0), 0.0)
        val out = push(40.0, 25.0, travelX = 1.0, amount = Field.stirAmount(0.0))
        assertEquals(0.0, out.x, 0.0)
        assertEquals(0.0, out.y, 0.0)
    }

    @Test
    fun `the stir rises with speed and then stops rising`() {
        val slow = Field.stirAmount(0.2)
        val quick = Field.stirAmount(1.0)
        assertTrue("a faster camera has to stir harder", slow < quick)
        assertEquals("and it saturates rather than growing forever", 1.0, Field.stirAmount(99.0), 0.0)
        // Squared, so a slow drag barely disturbs anything.
        assertTrue("a slow drag should be nearly still", slow < 0.05)
    }

    private fun push(
        fromX: Double,
        fromY: Double,
        tone: Double = 0.5,
        travelX: Double = 0.0,
        travelY: Double = 0.0,
        amount: Double = 1.0,
        radius: Double = 9.0,
    ) = Field.stirPush(fromX, fromY, tone, travelX, travelY, amount, radius, Field.Point())

    @Test
    fun `a cell never wanders further than its own radius`() {
        // A dot that can travel further than it is wide stops reading as that
        // dot leaning and starts reading as a different dot.
        val radius = 9.0
        for (step in 0..20) {
            val tone = step / 20.0
            val out = push(40.0, -15.0, tone = tone, travelX = 1.0, radius = radius)
            val reach = kotlin.math.hypot(out.x, out.y)
            assertTrue("a cell reached $reach on a radius of $radius", reach <= radius)
        }
    }

    @Test
    fun `the ground you are arriving at moves more than the ground behind you`() {
        // The whole point. Walking into long grass parts it in front of you;
        // what is behind your shoulder has already sprung back.
        val ahead = push(60.0, 0.0, travelX = 1.0)
        val behind = push(-60.0, 0.0, travelX = 1.0)
        val beside = push(0.0, 60.0, travelX = 1.0)
        val front = kotlin.math.hypot(ahead.x, ahead.y)
        val back = kotlin.math.hypot(behind.x, behind.y)
        val side = kotlin.math.hypot(beside.x, beside.y)
        assertTrue("ground ahead has to move most", front > side)
        assertTrue("ground beside has to move more than ground behind", side > back)
        assertTrue("and ground behind should barely move", back < front * 0.25)
    }

    @Test
    fun `a cell is pushed away from you, not towards you`() {
        // Radial, outward. A field that closed in on you as you travelled
        // would read as the ground swallowing the view.
        for (step in 0..11) {
            val angle = step / 12.0 * 6.283185307179586
            val fx = kotlin.math.cos(angle) * 50
            val fy = kotlin.math.sin(angle) * 50
            val out = push(fx, fy, travelX = 1.0, tone = 0.5)
            val outward = (out.x * fx + out.y * fy) / 50.0
            assertTrue("a cell at $step was pulled inward", outward >= 0.0)
        }
    }

    @Test
    fun `only zooming parts the ground evenly`() {
        // No heading at all. Everything in the box should still move, equally,
        // because moving straight in parts what is around you on all sides.
        val a = kotlin.math.hypot(push(50.0, 0.0).x, push(50.0, 0.0).y)
        val b = kotlin.math.hypot(push(-50.0, 0.0).x, push(-50.0, 0.0).y)
        val c = kotlin.math.hypot(push(0.0, -50.0).x, push(0.0, -50.0).y)
        assertEquals(a, b, 1e-9)
        assertEquals(a, c, 1e-9)
        assertTrue("a zoom should still disturb the ground", a > 0.0)
    }

    @Test
    fun `a plant leans its own way, and the same way every time`() {
        // Perfectly radial pushes draw a clean starburst, and a starburst is
        // an effect. The scatter is what makes it grass -- and it is the
        // cell's own, so a plant leans its own way every time you pass it.
        val once = push(40.0, 40.0, tone = 0.61, travelX = 1.0)
        val x = once.x
        val y = once.y
        val again = push(40.0, 40.0, tone = 0.61, travelX = 1.0)
        assertEquals(x, again.x, 0.0)
        assertEquals(y, again.y, 0.0)
        val neighbour = push(40.0, 40.0, tone = 0.12, travelX = 1.0)
        assertTrue("two plants in the same place leaned identically", x != neighbour.x)
    }

    @Test
    fun `only the middle of the frame stirs`() {
        val w = 1080.0
        val h = 2400.0
        // Dead centre is the whole stir.
        assertEquals(1.0, Field.stirNear(w / 2, h / 2, w, h), 1e-9)
        // The corners, and the edges each way, are perfectly still -- if this
        // ever stops being true the whole picture slides again.
        assertEquals(0.0, Field.stirNear(0.0, 0.0, w, h), 1e-9)
        assertEquals(0.0, Field.stirNear(w, h, w, h), 1e-9)
        assertEquals(0.0, Field.stirNear(w / 2, 0.0, w, h), 1e-9)
        assertEquals(0.0, Field.stirNear(0.0, h / 2, w, h), 1e-9)
    }

    @Test
    fun `the edge of the stirred patch is a fade, not a line`() {
        val w = 1080.0
        val h = 2400.0
        // Walking out from the centre, the share has to fall without ever
        // jumping. A step would draw a visible rectangle across the ground.
        var last = 1.0
        var sawMiddle = false
        for (step in 0..60) {
            val y = h / 2 + (h / 2) * (step / 60.0)
            val near = Field.stirNear(w / 2, y, w, h)
            assertTrue("the share went back up walking outward", near <= last + 1e-9)
            if (near > 0.05 && near < 0.95) sawMiddle = true
            last = near
        }
        assertTrue("there was no partial band at all, so the edge is a line", sawMiddle)
        assertEquals("and it reaches nothing well before the frame edge", 0.0, last, 1e-9)
    }

    @Test
    fun `the lens is the same lens whichever way the phone is held`() {
        // It was not. Focal length came off the height alone, so a phone
        // turned upright was shooting the same field at four times the focal
        // length -- 27 degrees across against 101 -- which is the difference
        // between a telescope and standing in a place. The field filled the
        // frame in landscape and ran out as a strip in portrait, and nothing
        // about the camera had moved.
        val cam = Field.Camera(Terrain.FIELD_W / 2, Terrain.FIELD_H / 2, 1.0)
        val portrait = Field.buildLens(cam, 0.5, 1080.0, 2400.0)
        val landscape = Field.buildLens(cam, 0.5, 2400.0, 1080.0)
        assertEquals(
            "one lens, whichever way up",
            portrait.focal,
            landscape.focal,
            1e-9,
        )
    }

    @Test
    fun `a pull-back can refuse to flatten`() {
        // The whole flat-to-perspective blend lives between 2.1x and 4.2x, and
        // a scroll crosses that in about a fifth of its travel -- so the view
        // tipped to an overhead map in the middle of an otherwise even move.
        // Held at the floor it stays the landscape it started as.
        val cam = Field.Camera(Terrain.FIELD_W / 2, Terrain.FIELD_H / 2, 0.5 * 0.7)
        val free = Field.buildLens(cam, 0.5, 1080.0, 2400.0)
        val held = Field.buildLens(cam, 0.5, 1080.0, 2400.0, tiltFloor = 1.0)
        assertEquals("wide out, the view flattens on its own", 0.0, free.tilt, 1e-9)
        assertEquals("unless something is holding it", 1.0, held.tilt, 1e-9)
        // And the floor never tips a view further than it already is.
        val close = Field.Camera(Terrain.FIELD_W / 2, Terrain.FIELD_H / 2, 0.5 * 27)
        assertEquals(
            Field.buildLens(close, 0.5, 1080.0, 2400.0).tilt,
            Field.buildLens(close, 0.5, 1080.0, 2400.0, tiltFloor = 0.4).tilt,
            1e-9,
        )
    }

    @Test
    fun `making the grid denser does not move the island`() {
        // CELL, COLS and ROWS move together and their product is the world.
        // If density ever moves it, every patch lands somewhere else and every
        // garden in the study is a different place than it was.
        assertEquals(2340.0, Terrain.FIELD_W, 1e-9)
        assertEquals(1740.0, Terrain.FIELD_H, 1e-9)
    }
}
