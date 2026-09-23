package app.harbor.domain

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The ground the field is grown on.
 *
 * Ported from the `fieldtrial.html` prototype, constant for constant. The
 * field is not a flat plane with things standing on it — it is a small island
 * with hills, a river, wet ground and dry, and the terrain is what decides
 * where anything can be. Change a number here and the whole world moves, so
 * they are kept together, named, and left alone.
 *
 * Everything is a pure function of position and [SEED]: nothing is stored, and
 * the same coordinates give the same ground on every device, every launch.
 * That is the same discipline [Garden] uses for the plan view, for the same
 * reason — a garden that rearranged itself between sessions would not be a
 * place.
 *
 * ## Why the hashing looks the way it does
 *
 * [hash2] is the prototype's, including its reliance on 32-bit wrap-around.
 * JavaScript's `Math.imul` and `>>>` are exactly Kotlin's `Int` multiply and
 * `ushr`, which is the correspondence that lets the two implementations agree
 * bit for bit. Widening any of it to `Long` would quietly produce a different
 * island.
 */
object Terrain {

    const val SEED = 20260911

    /**
     * How closely the ground is sampled, and over how many steps.
     *
     * These three move together and the world does not move with them:
     * [FIELD_W] and [FIELD_H] are the product, so halving [CELL] while
     * doubling [COLS] and [ROWS] leaves the island exactly where it was. The
     * landform is sampled from continuous functions of world position
     * ([landAt], [heightAt], [moistureAt]) and does not know the grid exists,
     * so a denser grid is the same island described in more cells rather than
     * a different island.
     *
     * What does change is everything drawn from `hash2(col, row, ...)` — the
     * per-cell jitter, tone and chance — because those are keyed on the grid
     * indices. Scatter is redealt; shape is not.
     *
     * Raised from 15.0 / 156 / 116 by half again, which is 2.25 times the
     * cells. Affordable because a frame no longer touches cells it cannot see:
     * the cost of the field is what is on screen, not how much of it exists.
     * See [Field.BLOCK]. Turn these if the ground wants to be denser still --
     * it is one ratio, and the three have to keep their product.
     */
    const val CELL = 10.0
    const val COLS = 234
    const val ROWS = 174

    const val FIELD_W = COLS * CELL
    const val FIELD_H = ROWS * CELL

    /** The scale everything is sampled at. Smaller means busier ground. */
    private const val N = 1250.0

    /** Integer hash in [0, 1). 32-bit wrap-around is load-bearing. */
    fun hash2(xi: Int, yi: Int, seed: Int): Double {
        var h = seed xor (xi * 374761393) xor (yi * 668265263)
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)).toUInt().toDouble()) / 4294967295.0
    }

    /** Value noise with a smoothstep fade, bilinear between four hashed corners. */
    fun noise(x: Double, y: Double, seed: Int): Double {
        val xi = floor(x).toInt()
        val yi = floor(y).toInt()
        val xf = x - xi
        val yf = y - yi
        val u = xf * xf * (3 - 2 * xf)
        val v = yf * yf * (3 - 2 * yf)
        val a = hash2(xi, yi, seed)
        val b = hash2(xi + 1, yi, seed)
        val c = hash2(xi, yi + 1, seed)
        val d = hash2(xi + 1, yi + 1, seed)
        return (a * (1 - u) + b * u) * (1 - v) + (c * (1 - u) + d * u) * v
    }

    /** Fractal noise: octaves at halving amplitude and doubling frequency. */
    fun fbm(x: Double, y: Double, seed: Int, octaves: Int = 4): Double {
        var sum = 0.0
        var amp = 1.0
        var freq = 1.0
        var norm = 0.0
        for (i in 0 until octaves) {
            sum += noise(x * freq, y * freq, seed + i * 101) * amp
            norm += amp
            amp *= 0.5
            freq *= 2
        }
        return sum / norm
    }

    fun clamp01(n: Double): Double = if (n < 0) 0.0 else if (n > 1) 1.0 else n

    /** Hermite ease on a clamped value. The prototype's single-argument one. */
    fun smooth(n: Double): Double {
        val t = clamp01(n)
        return t * t * (3 - 2 * t)
    }

    /** Where the river runs at a given depth into the field. */
    fun riverAt(wy: Double): Double =
        FIELD_W * 0.12 + fbm(wy / 900 * 1.7, 3.2, SEED + 505, 3) * FIELD_W * 0.26

    /**
     * How much land there is here.
     *
     * The field is an island, not a rectangle: a soft radial falloff roughed
     * up by noise, so the edge wanders instead of ruling a line. Below about
     * 0.2 there is no ground at all.
     */
    fun landAt(wx: Double, wy: Double): Double {
        val dx = (wx - FIELD_W / 2) / (FIELD_W * 0.52)
        val dy = (wy - FIELD_H / 2) / (FIELD_H * 0.52)
        val radial = 1 - sqrt(dx * dx + dy * dy)
        val rough = fbm(wx / N * 1.9 + 41, wy / N * 1.9 + 41, SEED + 2200, 4)
        return radial * 0.72 + rough * 0.5 - 0.16
    }

    /**
     * Height in [0, 1].
     *
     * Rolling base noise, plus a ridged fold so there are spines rather than
     * only blobs, plus a general rise toward the back of the field. The last
     * step cuts a valley wherever the river runs, which is what stops water
     * from sitting improbably on a hillside.
     */
    fun heightAt(wx: Double, wy: Double): Double {
        val base = fbm(wx / N * 2.1, wy / N * 2.1, SEED, 5)
        val folds = fbm(wx / N * 1.3 + 9, wy / N * 1.3 + 9, SEED + 77, 4)
        val ridge = 1 - abs(folds * 2 - 1)
        val rise = smooth(1 - wy / (FIELD_H * 0.58))
        var h = base * 0.42 + ridge * ridge * 0.3 + rise * 0.5
        h *= 0.46 + 0.54 * smooth(wx / FIELD_W * 1.9)
        val bank = abs(wx - riverAt(wy))
        if (bank < 58) h = min(h, 0.2 + bank / 58 * 0.16)
        return clamp01(h)
    }

    /** Wet ground grows denser and greener. */
    fun moistureAt(wx: Double, wy: Double): Double =
        fbm(wx / N * 3.3 + 21, wy / N * 3.3 + 21, SEED + 311, 4)

    /** Tight clumps of heavier growth — trees, at this scale. */
    fun grovesAt(wx: Double, wy: Double): Double =
        fbm(wx / N * 9.5 + 4, wy / N * 9.5 + 4, SEED + 907, 3)

    /** Worked ground, drawn as rows of crosses. */
    fun tilledAt(wx: Double, wy: Double): Double =
        fbm(wx / N * 2.6 + 61, wy / N * 2.6 + 61, SEED + 1301, 3)

    /**
     * An outline that is never a circle: a noisy radius walked around a centre.
     *
     * Returned as points; the drawing layer closes them into a curve.
     */
    fun blobRing(
        cx: Double,
        cy: Double,
        radius: Double,
        seed: Int,
        points: Int = 17,
    ): List<Garden.Spot> = (0 until points).map { k ->
        val a = (k.toDouble() / points) * 2 * Math.PI
        val r = radius * (0.68 + fbm(cos(a) * 1.6 + 3, sin(a) * 1.6 + 3, seed, 3) * 0.7)
        Garden.Spot(cx + cos(a) * r, cy + sin(a) * r)
    }

    /**
     * Nudges a patch onto ground worth planting.
     *
     * Walks outward in rings looking for land that is neither underwater nor
     * a hilltop, and stops early once it finds somewhere good enough. Without
     * this a patch can land in the river, which reads as a bug even though the
     * terrain is doing exactly what it should.
     */
    fun settle(x: Double, y: Double): Garden.Spot {
        var best = Garden.Spot(x, y)
        var bestScore = -1.0
        for (ring in 0..7) {
            val steps = if (ring == 0) 1 else 12
            for (step in 0 until steps) {
                val a = (step.toDouble() / 12) * 2 * Math.PI
                val px = x + cos(a) * ring * 80
                val py = y + sin(a) * ring * 80
                if (landAt(px, py) < 0.26) continue
                val z = heightAt(px, py)
                val score =
                    if (z > 0.38 && z < 0.72) 1 - abs(z - 0.52) - ring * 0.04 else -1.0
                if (score > bestScore) {
                    bestScore = score
                    best = Garden.Spot(px, py)
                }
            }
            if (bestScore > 0.62) break
        }
        return best
    }

    /** Even-odd ray cast. Patch outlines are concave, so a radius will not do. */
    fun inRing(ring: List<Garden.Spot>, px: Double, py: Double): Boolean {
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[j]
            if ((a.y > py) != (b.y > py) &&
                px < ((b.x - a.x) * (py - a.y)) / (b.y - a.y) + a.x
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}
