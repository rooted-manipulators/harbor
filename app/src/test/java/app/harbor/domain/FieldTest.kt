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

    @Test
    fun `a flower keeps its place as the patch grows around it`() {
        // Planting order is a rank, not a clock, so the sixth flower is the
        // next cell down a list the first five already sat at the top of. A
        // flower that moved when the next one arrived would make the field a
        // picture of the count rather than a record of the calls.
        fun placed(calls: Int) = Field.cells(Field.patches(people(calls)))
            .filter { it.kind == Field.Kind.FLOWER }
            .sortedBy { it.bloom }
            .map { it.x to it.y }
        assertEquals(placed(5), placed(6).take(5))
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
        val lens = Field.buildLens(cam, 0.5, 2000.0)
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
        val lens = Field.buildLens(cam, base, 2000.0)
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
}
