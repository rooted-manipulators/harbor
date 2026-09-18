package app.harbor.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path as NativePath
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.harbor.data.HarborRepository
import app.harbor.domain.CallStats
import app.harbor.domain.Field
import app.harbor.domain.FlowerKind
import app.harbor.domain.Flowers
import app.harbor.ui.theme.CardEdge
import app.harbor.ui.theme.LocalReducedMotion
import app.harbor.domain.LedgerEntry
import app.harbor.domain.Resolution
import app.harbor.domain.Terrain
import app.harbor.domain.Tone
import app.harbor.ui.theme.Gold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.min

/**
 * The field.
 *
 * A port of the `fieldtrial.html` prototype, which is the design authority
 * for this screen. The terrain is in [Terrain], the layout and camera in
 * [Field]; this draws them and takes the gestures.
 *
 * ## There is no 2D mode
 *
 * Pulling back *is* the plan view. [Field.tiltFor] blends flat and
 * perspective as you zoom, so the overhead map and the landscape are two ends
 * of one dial rather than two screens, and the readout names where on that
 * dial you are.
 *
 * ## Why this draws on the native canvas
 *
 * The field is tens of thousands of cells. Compose's `drawCircle` per cell
 * would be tens of thousands of draw calls a frame; instead every cell of the
 * same colour is added to one path and the bucket is filled once, which is
 * what the prototype does and the reason it stays smooth. The paths and
 * paints are held across frames and rewound, so a frame allocates nothing.
 */
@Composable
fun FieldCanvas(
    store: HarborRepository,
    modifier: Modifier = Modifier,
    /**
     * Whether this field takes gestures.
     *
     * False on home, where it is a preview inside a scrolling page. A child
     * that consumes pan and pinch wins over the scroll around it, so an
     * interactive field there means the page cannot be scrolled past without
     * dragging the world about — and on a real phone it opened at 9x having
     * eaten input nobody meant for it. Tapping the preview opens the real one.
     */
    interactive: Boolean = true,
    /**
     * Whether to paint the weather behind the terrain.
     *
     * False on home, where the screen already is the weather and the field is
     * only its dots. Painting a second sky inside a rounded box on top of the
     * first is exactly the seam this was meant to remove.
     */
    sky: Boolean = true,
    /**
     * A flower has just been planted, so show the garden it went into.
     *
     * Pulls back to the whole island and then flies down to the patch that
     * gained it, which is the one moment the overview is worth showing on
     * home: it answers "where did that go" before settling where home always
     * sits. The rest of the time the camera is simply already there.
     */
    arriving: Boolean = false,
    /**
     * Open standing at a flower rather than looking at the whole island.
     *
     * Separate from [interactive] because those two used to be the same
     * question and are not: home wants the close shot *and* the gestures, so
     * you can push the field back with two fingers and see the whole garden
     * without leaving the screen. Only the opening frame is fixed.
     *
     * The field screen asks for it too now. It used to open on the overview,
     * on the reasoning that the screen for looking at the whole island should
     * open on the whole island -- but the island from orbit is where a flower
     * is a pixel, so somebody who had just grown their first one went looking
     * for it and found a map. The island is one pinch, or one tap of pull
     * back, away, and that framing then stays.
     */
    standClose: Boolean = false,
    /**
     * How far the page has pulled the camera off the field: 0 is standing in
     * it, 1 is the whole island.
     *
     * A lambda rather than a value, and deliberately so. Whatever drives this
     * changes every frame of a scroll, and a composable that reads such a
     * thing recomposes every frame with it -- which on home means recomposing
     * the largest screen in the app sixty times a second to move a camera.
     * Read through `snapshotFlow` instead, so the only thing that wakes is the
     * effect that needs it.
     */
    pullBack: () -> Float = { 0f },
    /**
     * What a tap means, when it should not mean "select a patch".
     *
     * Home's field is now pinchable and pannable like the garden's, and the
     * moment it became so it swallowed the tap that used to open the garden --
     * a gesture handler consumes taps whether or not it does anything with
     * them, so the clickable wrapped around it stopped firing and there was no
     * way off home into the field at all. Given here, a tap calls this instead
     * of choosing a patch.
     */
    onTap: (() -> Unit)? = null,
    /**
     * Whether to draw the field's own chrome: the zoom buttons and the
     * readout.
     *
     * Off on home, where the field is scenery rather than an instrument. You
     * can still pinch and drag it -- the gestures are [interactive] -- but a
     * pair of zoom buttons and a magnification readout sitting on the view
     * turn a window onto a garden into a map application, and home has
     * somewhere to put a control panel: the field screen.
     */
    controls: Boolean = interactive,
) {
    val contacts by store.contacts.collectAsState()
    val settings by store.settings.collectAsState()
    var entries by remember { mutableStateOf<List<LedgerEntry>>(emptyList()) }

    LaunchedEffect(Unit) { entries = store.recentEntries() }

    val people = remember(contacts, entries) {
        // Oldest first, because that is the order the ground is planted in
        // and a flower has to keep its place as the ones after it arrive.
        val grown = entries
            .filter { it.resolution == Resolution.CALLED && it.flower != null }
            .sortedBy { it.occurredAt }
            .groupBy { it.contactId }
        contacts.map { contact ->
            val theirs = grown[contact.id].orEmpty()
            // Every flower this person has grown, in order, each in the kind
            // that was chosen on the call that grew it. One flower a minute,
            // not one a call -- see Flowers.flowerCount -- so a long call
            // puts down a run of the same answer, which is the honest shape
            // of it.
            val kinds = theirs.flatMap { entry ->
                List(Flowers.flowerCount(entry.callMinutes)) { entry.flower!! }
            }
            Field.Person(
                contactId = contact.id,
                label = contact.label,
                calls = kinds.size,
                flowers = kinds,
                // A patch is named by whatever has been chosen for it most
                // often, so the tag says something the user picked rather
                // than something assigned. Only the label and the empty
                // patch use it now; the blooms are each their own kind.
                flower = theirs.mapNotNull { it.flower }
                    .groupingBy { it }.eachCount()
                    .maxByOrNull { it.value }?.key
                    ?: defaultFlower(contact.tone),
            )
        }
    }

    val patches = remember(people) { Field.patches(people) }
    val palette = remember { Field.palette() }

    // The bloom artwork, for the flowers near enough to be worth drawing
    // properly. Only the kinds planted here, and decoded off the frame: a
    // field of five contacts holds a handful of these, not all twenty.
    val grownKinds = remember(patches) {
        patches.flatMapTo(LinkedHashSet()) { it.flowers + it.flower }
    }
    val res = LocalContext.current.resources
    val art by produceState(initialValue = emptyMap<FlowerKind, Bitmap>(), grownKinds, res) {
        value = withContext(Dispatchers.Default) {
            // Half size. A bloom drawn larger than this in the *field* means
            // one flower is filling a quarter of the screen, which is further
            // than this view is for -- and full size would hold four times the
            // heap for a sharpness nothing here is close enough to use.
            val opts = BitmapFactory.Options().apply { inSampleSize = 2; inScaled = false }
            grownKinds.associateWith { BitmapFactory.decodeResource(res, bloomOf(it), opts) }
        }
    }

    // Tens of thousands of cells, each sampling several octaves of noise.
    // Fast, but not fast enough to sit on the frame that shows the screen.
    val built by produceState(initialValue = Field.Built(emptyList(), emptyList()), patches) {
        value = withContext(Dispatchers.Default) { Field.build(patches) }
    }
    val cells = built.cells

    // The patch whose card is open, or null. Cleared by tapping open country.
    var showing by remember { mutableStateOf<Field.Patch?>(null) }

    var frame by remember { mutableStateOf(IntSize.Zero) }
    val base = remember(frame) {
        Field.overviewZoom(frame.width.toDouble(), frame.height.toDouble())
    }

    var cam by remember {
        mutableStateOf(Field.Camera(Terrain.FIELD_W / 2, Terrain.FIELD_H / 2, 0.2))
    }
    var goal by remember { mutableStateOf(cam) }
    var touched by remember { mutableStateOf(false) }

    // What the stir is worked out from, frame to frame.
    //
    // Deliberately a plain object and not snapshot state. These are read and
    // written inside the draw itself, and a state write during draw
    // invalidates the draw that read it -- which redraws, which writes again.
    // The field would repaint forever on a screen where nothing is happening,
    // and it would never look wrong: the ground is correct in every frame, it
    // is just being drawn hundreds of times for no reason. This was written
    // with mutableStateOf first and the comment above it already said not to.
    val reducedMotion = LocalReducedMotion.current
    val stirring = remember { Stirring() }
    val chase = remember { Chase() }
    val flatten = remember { Flatten() }

    // Nothing planted yet is its own opening shot, not a smaller version of
    // the usual one.
    val planted = remember(patches) { patches.sumOf { it.calls } > 0 }

    /**
     * Where home stands, which is not where the garden stands.
     *
     * The garden screen opens on the whole island, because that screen is for
     * looking at the whole island. Home is not: it is a window onto one
     * flower, down among the grass, the way the reference shows a single bloom
     * on a patch of ground rather than a map of a country. You get the island
     * by opening it.
     *
     * The patch chosen is the one most recently added to, so the flower on
     * home is the last call you had rather than whichever patch happens to be
     * biggest.
     */
    val homeSpot = remember(patches, entries) {
        // By the clock on the entry, not by where it sits in the list.
        // `recentEntries` hands them back *oldest* first, so taking the first
        // one meant the field stood at the patch of the very first call ever
        // made and called it the newest -- which is the one place in the app
        // where being a call behind is being a whole person behind.
        val newest = entries
            .filter { it.resolution == Resolution.CALLED && it.flower != null }
            .maxByOrNull { it.occurredAt }
            ?.contactId
        patches.firstOrNull { it.contactId == newest && it.calls > 0 }
            ?: patches.filter { it.calls > 0 }.maxByOrNull { it.calls }
    }

    /**
     * The ground the camera actually stands on: that patch's newest flower.
     *
     * Aiming at the patch centre was near enough while a patch was a blur of
     * dots, and is not once the flower somebody just grew is the point of the
     * shot -- at standing zoom a well-planted patch is wider than the phone,
     * so its middle can have the new bloom off the edge of the screen.
     * [Field.newestBloom] is the cell that arrived last, and it is a real
     * place rather than an average of one.
     */
    val newest: Field.Cell? = remember(cells, patches, homeSpot) {
        val here = homeSpot ?: return@remember null
        val i = patches.indexOf(here)
        if (i < 0) null else Field.newestBloom(cells, i)
    }

    val standSpot: Pair<Double, Double>? = remember(newest, cells, homeSpot) {
        // Null, not the patch centre, while the cells are still being built.
        // Nothing is drawn until they arrive, so there is nothing to aim at
        // yet -- and a first answer that has to be corrected the moment the
        // real one turns up is a jump across the patch for no reason.
        newest?.let { it.x to it.y }
            ?: homeSpot?.let { if (cells.isEmpty()) null else it.x to it.y }
    }

    // Take the opening framing, and keep taking it until the user takes over.
    //
    // Latching on the first size that arrived was wrong: layout reports an
    // early, smaller frame before it settles, so the camera locked to that
    // one's overview zoom and the island then sat at a third of the width it
    // should have filled. Re-aiming until the first gesture also means a
    // rotation reframes instead of leaving the world off-centre.
    LaunchedEffect(base, frame, planted, standSpot, standClose, arriving) {
        // While a flower is arriving the camera is being flown deliberately;
        // re-aiming underneath it would cut the flight short. standSpot also
        // changes the instant the new flower lands, which is exactly when this
        // would otherwise fire.
        if (arriving) return@LaunchedEffect
        // Something is planted but the field is not built yet: wait for it
        // rather than aim somewhere that will have to be corrected.
        if (planted && standSpot == null) return@LaunchedEffect
        if (!touched && base > 0 && frame.width > 0) {
            val here = standSpot
            cam = when {
                // Home, with something to show: stand at the newest flower.
                standClose && here != null ->
                    Field.Camera(here.first, here.second, base * Field.BLOOM_ZOOM)

                // A field asked to open on the whole island rather than to
                // stand in it. Nothing does now -- both screens stand -- but
                // this is what [standClose] being false still means.
                planted && !standClose ->
                    Field.Camera(Terrain.FIELD_W / 2, Terrain.FIELD_H / 2, base)

                // Nothing planted anywhere, on either screen. Down among the
                // grass where there is visibly room, rather than looking at an
                // empty island from orbit.
                else -> {
                    val spot = Field.emptyStart()
                    Field.Camera(spot.x, spot.y, base * Field.EMPTY_ZOOM)
                }
            }
            goal = cam
        }
    }

    // The arrival: the whole island, a beat, then down to the new flower.
    //
    // No animation of its own -- it sets the camera and then a goal, and the
    // chase below does the flying. That is why the descent eases: it is the
    // same motion a tap on a patch makes, which is the point, because this is
    // the app showing you where the thing you just did ended up.
    LaunchedEffect(arriving, base, frame, standSpot) {
        if (!arriving || base <= 0 || frame.width <= 0) return@LaunchedEffect
        val here = standSpot ?: return@LaunchedEffect
        cam = Field.Camera(Terrain.FIELD_W / 2, Terrain.FIELD_H / 2, base)
        goal = cam
        delay(520)
        // A deliberate flight, not the pull: brisk again.
        chase.tau = TAP_TAU
        goal = Field.Camera(here.first, here.second, base * Field.BLOOM_ZOOM)
    }

    // Scrolling the page pulls the camera back off the field.
    //
    // Read through snapshotFlow rather than taken as a parameter value: the
    // scroll changes every frame of a drag, and a composable that reads it
    // recomposes every frame with it. Home is a large composable. This way the
    // only thing that wakes is the effect, and what it writes is the goal --
    // so the chase below does the easing and the pull-back arrives smooth
    // without an animation of its own.
    LaunchedEffect(base, frame, standSpot, touched) {
        if (touched || base <= 0 || frame.width <= 0) return@LaunchedEffect
        val here = standSpot ?: return@LaunchedEffect
        val standX = here.first
        val standY = here.second
        val standZoom = base * Field.BLOOM_ZOOM
        // Not `base`. That is a cover fit, so the island still runs off the
        // edges at the far end of the pull and you never quite see the place
        // you are being shown. See Field.wideZoom.
        val wideZoom = Field.wideZoom(frame.width.toDouble(), frame.height.toDouble())
        snapshotFlow { pullBack().coerceIn(0f, 1f).toDouble() }.collect { pull ->
            // Only the goal, and the camera takes its time getting there.
            //
            // A short flick should start a long move: the finger says where to
            // go and then lets go, and the view keeps travelling like a camera
            // on a crane rather than a map being dragged. So the pull sets a
            // destination and slows the chase right down while it is the thing
            // steering -- [PULL_TAU] against the tenth of a second a tap gets.
            chase.tau = PULL_TAU
            // Keep the shot in perspective the whole way out.
            //
            // Tilt is otherwise a function of zoom, and the entire
            // flat-to-perspective blend lives between 2.1x and 4.2x -- which
            // the pull crosses in about a fifth of its travel. The view was
            // tipping from standing in the field to an overhead map in the
            // middle of a move that is even everywhere else, and that lurch is
            // the stutter. Held at the floor it never happens: the same
            // landscape, from higher up.
            flatten.floor = 1.0
            goal = Field.Camera(
                x = standX + (Terrain.FIELD_W / 2 - standX) * pull,
                y = standY + (Terrain.FIELD_H / 2 - standY) * pull,
                // Geometrically, not linearly. Zoom multiplies -- halfway
                // between 27x and 1x is not 14x, it is about 5x, and a linear
                // ramp spends most of the scroll crawling through the wide end
                // where nothing appears to change and then lurches at the
                // close end. This way every pixel of scroll moves the view by
                // the same proportion, which is what makes it feel even.
                zoom = standZoom * (wideZoom / standZoom).pow(pull),
            )
        }
    }

    // The camera chases its goal rather than snapping, which is what makes a
    // tap on a patch read as travelling there.
    //
    // Eased over time rather than per frame. It used to move a flat 0.13 of
    // the remaining distance every frame, which makes the speed of every move
    // in the app a function of the frame rate: the same tap travels at two
    // speeds on a 60Hz phone and a 120Hz one, and slows to a crawl on a phone
    // dropping frames -- exactly when it is already struggling. A time
    // constant is the same motion on any of them.
    //
    // Zoom eases geometrically for the same reason the pull maps it that way:
    // it multiplies. Easing it linearly makes the wide end of a long move
    // crawl and the close end arrive in a rush.
    LaunchedEffect(Unit) {
        var previous = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val seconds = if (previous == 0L) 1.0 / 60
                else ((now - previous) / 1_000_000_000.0).coerceIn(1.0 / 240, 0.1)
                previous = now
                val k = 1.0 - exp(-seconds / chase.tau)
                val next = Field.Camera(
                    x = cam.x + (goal.x - cam.x) * k,
                    y = cam.y + (goal.y - cam.y) * k,
                    zoom = cam.zoom * (goal.zoom / cam.zoom).pow(k),
                )
                if (next != cam) cam = next
            }
        }
    }

    val tagInk = MaterialTheme.colorScheme.background

    // Held across frames so drawing allocates nothing.
    val kit = remember(palette.size) { DrawKit(palette.size) }

    // The corner belongs to a panel, and on home this is not a panel.
    Box(if (sky) modifier.clip(RoundedCornerShape(30.dp)) else modifier) {

        if (sky) FieldSky(settings.weather, Modifier.fillMaxSize())

        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { frame = it }
                .pointerInput(base, interactive) {
                    if (!interactive) return@pointerInput
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (base <= 0) return@detectTransformGestures
                        touched = true
                        // Taking hold of the field hands the dial back: a
                        // pinch really is asking for the plan view.
                        flatten.floor = 0.0
                        val tilt = Field.tiltFor(goal.zoom, base)
                        val nextZoom = Field.clampZoom(goal.zoom * zoom, base)
                        // A deliberate flight, not the pull: brisk again.
                        chase.tau = TAP_TAU
                        flatten.floor = 0.0
                        goal = Field.Camera(
                            x = goal.x - pan.x / goal.zoom,
                            // Dragging up the screen has to cover more ground
                            // once the view has tipped, or the world feels
                            // stuck to the finger near the horizon.
                            y = goal.y - (pan.y / goal.zoom) * (1 + tilt * 2.4),
                            zoom = nextZoom,
                        )
                    }
                }
                .pointerInput(patches, base, interactive, onTap) {
                    if (!interactive) return@pointerInput
                    detectTapGestures { at ->
                        if (onTap != null) {
                            onTap()
                            return@detectTapGestures
                        }
                        if (base <= 0 || frame.height == 0) return@detectTapGestures
                        val lens = Field.buildLens(cam, base, frame.width.toDouble(), frame.height.toDouble(), flatten.floor)
                        val point = Field.Point()
                        var best: Field.Patch? = null
                        var bestDist = 76.0
                        for (patch in patches) {
                            Field.project(
                                patch.x, patch.y, Terrain.heightAt(patch.x, patch.y),
                                cam, lens, frame.width.toDouble(), frame.height.toDouble(),
                                point,
                            )
                            val d = hypot(point.x - at.x, point.y - at.y)
                            if (d < bestDist) {
                                bestDist = d
                                best = patch
                            }
                        }
                        // Travel there, and say whose it is. Flying the
                        // camera at a patch and then telling the user nothing
                        // about it was the one interaction on this screen that
                        // answered a question with a gesture.
                        showing = best
                        best?.let {
                            touched = true
                            // Taking hold of the field hands the dial back: a
                            // pinch really is asking for the plan view.
                            flatten.floor = 0.0
                            // A deliberate flight, not the pull: brisk again.
                            chase.tau = TAP_TAU
                            goal = Field.Camera(it.x, it.y, base * 6.5)
                        }
                    }
                },
        ) {
            if (cells.isEmpty() || base <= 0) return@Canvas
            // How fast the view is travelling, in screens a second, which is
            // the only thing the stir needs to know. Measured here rather than
            // in the gesture handlers because the camera also moves on its own
            // -- flying to a new bloom is movement the ground should feel too.
            val now = System.nanoTime()
            val seconds = ((now - stirring.at) / 1_000_000_000.0).coerceIn(1.0 / 120, 0.25)
            val was = stirring.from ?: cam
            val moved = hypot(cam.x - was.x, cam.y - was.y) * cam.zoom / size.width
            stirring.at = now
            stirring.from = cam
            // Eased rather than taken raw: a frame that happens to land between
            // two gesture events reads as a dead stop, and the ground should
            // not twitch because the touch stream did.
            stirring.speed += ((moved / seconds) - stirring.speed) * 0.25
            // Which way you are going, in screen terms. Eased like the speed
            // is, so a direction does not snap between two gesture events, and
            // kept from the last frame that actually moved -- a still camera
            // has no heading, and the one it had last is the honest answer
            // while the ground settles.
            val stepX = cam.x - was.x
            val stepY = cam.y - was.y
            val step = hypot(stepX, stepY)
            if (step > 1e-9) {
                stirring.headingX += (stepX / step - stirring.headingX) * 0.25
                stirring.headingY += (stepY / step - stirring.headingY) * 0.25
            }
            val stir = if (reducedMotion) 0.0 else Field.stirAmount(stirring.speed)
            drawField(
                built, patches, palette, cam, base, kit, tagInk, newest, art,
                stir, stirring.headingX, stirring.headingY, flatten.floor,
            )
        }

        if (controls) {
            FieldControls(
            onIn = {
                touched = true
                // Taking hold of the field hands the dial back: a
                // pinch really is asking for the plan view.
                flatten.floor = 0.0
                // A deliberate flight, not the pull: brisk again.
                chase.tau = TAP_TAU
                goal = goal.copy(zoom = Field.clampZoom(goal.zoom * 1.45, base))
            },
            onOut = {
                touched = true
                // Taking hold of the field hands the dial back: a
                // pinch really is asking for the plan view.
                flatten.floor = 0.0
                // A deliberate flight, not the pull: brisk again.
                chase.tau = TAP_TAU
                goal = goal.copy(zoom = Field.clampZoom(goal.zoom / 1.45, base))
            },
            // Pull back is a framing like any other, and it stays put.
            //
            // It used to hand the camera back to the app, which was harmless
            // while the app's own framing was also the whole island. Now that
            // the field opens standing at the newest flower, handing it back
            // means the island you asked for is taken away again the next time
            // anything reframes.
            onFit = {
                touched = true
                // Taking hold of the field hands the dial back: a
                // pinch really is asking for the plan view.
                flatten.floor = 0.0
                // A deliberate flight, not the pull: brisk again.
                chase.tau = TAP_TAU
                goal = Field.Camera(Terrain.FIELD_W / 2, Terrain.FIELD_H / 2, base)
            },
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            )
        }

        // Said on the field itself, because the field is the invitation.
        // Home carries the same line above its own preview, so this one is
        // only for the full screen.
        // The empty-state line belongs to the field screen, not to home.
        //
        // It is centred in the canvas, and home's canvas is 46% of a phone
        // with the greeting already sitting at the foot of it -- so the two
        // landed on top of each other, white serif through white serif. Home
        // says the same thing in its own eyebrow, where there is room for it.
        if (controls && !planted) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Waiting for you to grow a flower.",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                )
                Text(
                    "A call, and how it felt. That is the whole of it.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }

        showing?.let { patch ->
            PatchCard(
                patch = patch,
                usual = CallStats.usualMinutes(entries, patch.contactId),
                modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp),
            ) { showing = null }
        }

        // Not while a card is open: the two sit in the same corner of the
        // screen and the readout was drawing straight over the third line of
        // the card.
        if (controls && base > 0 && showing == null) {
            FieldReadout(
                relative = cam.zoom / base,
                tilt = Field.tiltFor(cam.zoom, base),
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )
        }
    }
}

/**
 * Whose patch this is, on the field itself.
 *
 * The field could always be flown around and a patch could always be tapped,
 * but a tap only moved the camera — you arrived somewhere and the screen told
 * you nothing about where you had arrived. A patch is a person and a count of
 * calls, and those are the two things worth knowing while looking at it.
 *
 * Sits over the field rather than replacing it, because the point is to read
 * this *and* see the ground it belongs to.
 */
@Composable
private fun PatchCard(
    patch: Field.Patch,
    usual: Int?,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, CardEdge, RoundedCornerShape(16.dp))
            .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FlowerMark(patch.flower, Modifier.size(46.dp))
        Column(Modifier.weight(1f)) {
            Text(
                patch.label,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
            )
            Text(
                when (patch.calls) {
                    0 -> "nothing here yet"
                    1 -> "one flower, " + Flowers.spec(patch.flower).name.lowercase()
                    else -> patch.calls.toString() + " flowers, mostly " +
                        Flowers.spec(patch.flower).name.lowercase()
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            usual?.let {
                Text(
                    "calls usually run " + CallStats.formatDuration(it),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
        Text(
            "Close",
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClose)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

/** Paths and paints reused every frame. */
private class DrawKit(buckets: Int) {
    val paths = Array(buckets) { NativePath() }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    // Rock and the patch outlines went up with the ground under them. Both
    // were pitched against a land that has since been lifted off the page,
    // and a boulder the colour of the grass it sits on is not a boulder.
    val rock = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xDB4E5148.toInt() }
    val till = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xCC7C5B3D.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x66708F76
        pathEffect = DashPathEffect(floatArrayOf(5f, 6f), 0f)
    }
    /** The ring around the flower that has just been grown. */
    val markRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFF0BD3E.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    /** The bloom artwork. Filtered, because it is always drawn scaled down. */
    val art = Paint(Paint.FILTER_BITMAP_FLAG)
    val tagBack = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7C5B3D.toInt() }
    val tagText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val ring = NativePath()
    val stem = NativePath()
    val rect = RectF()
    val point = Field.Point()

    /** Scratch for the stir, so the per-cell offset allocates nothing. */
    val stirPoint = Field.Point()
}

/**
 * How fast the view was travelling last frame, and when that was.
 *
 * A plain object on purpose — see where it is remembered. Nothing in here may
 * be snapshot state: it is written during the draw, and state written during a
 * draw invalidates that draw.
 */
/**
 * How long the camera takes to close the distance to its goal, as a time
 * constant in seconds: the time to cover about two thirds of what is left.
 *
 * Mutable because the two things that move the camera want different motions.
 * A tap on a patch is an answer to a question and should arrive; a pull-back
 * is a shot, and a shot is slow. A plain object rather than state -- it is
 * read inside the frame loop and written from an effect, and neither should
 * recompose anything.
 */
private class Chase {
    var tau: Double = TAP_TAU
}

/** A tap travels there. Brisk, because you asked a question. */
private const val TAP_TAU = 0.12

/**
 * A pull-back drifts there, and keeps drifting after the finger has gone.
 *
 * Nearly four times a tap's. Short flick, long move: the scroll says where to
 * point and lets go, and the view carries on like a camera on a crane rather
 * than a map being dragged about.
 */
private const val PULL_TAU = 0.45

/**
 * How much the pull refuses to let the view flatten, 0 to 1.
 *
 * A plain object: written from the pull's effect, read while drawing, and
 * neither should recompose anything.
 */
private class Flatten {
    var floor: Double = 0.0
}

private class Stirring {
    var at: Long = System.nanoTime()
    var from: Field.Camera? = null
    var speed: Double = 0.0

    /** The way you are going, normalised, kept from the last frame that moved. */
    var headingX: Double = 0.0
    var headingY: Double = 0.0
}

private class Tag(val text: String, val x: Float, var y: Float, val stemY: Float)

private fun DrawScope.drawField(
    built: Field.Built,
    patches: List<Field.Patch>,
    palette: LongArray,
    cam: Field.Camera,
    base: Double,
    kit: DrawKit,
    tagInk: Color,
    /** The flower just grown, ringed so it can be found. Null the rest of the time. */
    mark: Field.Cell?,
    /** The bloom artwork, by kind. Empty until it has finished decoding. */
    art: Map<FlowerKind, Bitmap>,
    /** How hard the ground is stirring, nought to one. See [Field.stirAmount]. */
    stir: Double,
    /** Which way you are travelling, in screen terms, normalised. */
    headingX: Double,
    headingY: Double,
    /** The least the view may flatten. See Field.buildLens. */
    tiltFloor: Double,
) {
    val unit = 1.dp.toPx()
    // A bloom is never drawn smaller than this, however far off it is.
    //
    // The ground's floor is 0.75px, and at that size a patch of one or two
    // calls is a couple of sub-pixel specks in two hundred cells -- the
    // flowers were there and simply could not be seen. Held under
    // [Field.FLOWER_AT] so a distant bloom stays a dot rather than out-growing
    // the size at which it would have opened into a flower.
    val bloomFloor = min(1.3 * unit, Field.FLOWER_AT * 0.8)
    val w = size.width.toDouble()
    val h = size.height.toDouble()
    val lens = Field.buildLens(cam, base, w, h, tiltFloor)
    val canvas = drawContext.canvas.nativeCanvas
    val p = kit.point

    for (path in kit.paths) path.rewind()
    kit.stem.rewind()

    // Flowers and rock are drawn after the bulk, so they sit on top of it.
    val blooms = ArrayList<FloatArray>()
    var tillWidth = -1f
    // Spent against [Field.GRASS_BUDGET]. Ground that runs out stays dots.
    var tufts = 0
    // Where the marked flower came out on screen, and how big, or null.
    var marked: FloatArray? = null

    // Whole blocks first, then the cells inside the ones that survive.
    //
    // The per-cell test below is what actually decides, and it has not
    // changed; this only stops the field paying to project forty thousand
    // cells in order to throw away the ninety-odd per cent of them that are
    // nowhere near the screen. Zoomed out almost every block is visible and
    // this costs a few hundred corner projections for nothing; zoomed in,
    // which is where the cost was, nearly all of them go at once.
    val cells = built.cells
    for (block in built.blocks) {
        if (block.from == block.to) continue
        if (!Field.onScreen(block, cam, lens, w, h, 26.0, p)) continue
    for (i in block.from until block.to) {
        val c = cells[i]
        Field.project(c.x, c.y, c.z, cam, lens, w, h, p)
        if (p.x < -26 || p.x > w + 26 || p.y < -26 || p.y > h + 26) continue

        var r = c.size * Field.DOT_SCALE * p.s
        if (r < 0.1) continue

        // Ground that passes through the middle of the frame gets brushed
        // aside, and settles the instant you stop travelling. Only that patch:
        // the whole field leaning at once reads as the picture sliding rather
        // than as ground being disturbed. Applied after the cull so a stirring
        // cell cannot escape the block that decided it was visible, and scaled
        // by its own radius so near ground moves further than far.
        if (stir > 0.0) {
            val near = Field.stirNear(p.x, p.y, w, h)
            if (near > 0.0) {
                Field.stirPush(
                    fromX = p.x - w / 2,
                    fromY = p.y - h / 2,
                    tone = c.tone,
                    travelX = headingX,
                    travelY = headingY,
                    amount = stir * near,
                    radius = r,
                    out = kit.stirPoint,
                )
                p.x += kit.stirPoint.x
                p.y += kit.stirPoint.y
            }
        }

        when (c.kind) {
            Field.Kind.CROSS -> {
                if (r > 0.5) {
                    if (tillWidth < 0) tillWidth = maxOf(0.6, r * 0.4).toFloat()
                    val a = (r * 1.1).toFloat()
                    val x = p.x.toFloat()
                    val y = p.y.toFloat()
                    kit.stem.moveTo(x - a, y - a); kit.stem.lineTo(x + a, y + a)
                    kit.stem.moveTo(x + a, y - a); kit.stem.lineTo(x - a, y + a)
                }
            }

            Field.Kind.SQUARE -> {
                val s = (r * 1.6).toFloat()
                canvas.drawRect(
                    (p.x - s / 2).toFloat(), (p.y - s / 2).toFloat(),
                    (p.x + s / 2).toFloat(), (p.y + s / 2).toFloat(),
                    kit.rock,
                )
            }

            Field.Kind.FLOWER -> {
                // Close enough in, a planted dot opens into the flower it
                // was standing for. Nothing is animated; it simply got big.
                if (r > Field.FLOWER_AT) {
                    blooms += floatArrayOf(
                        p.x.toFloat(), p.y.toFloat(), r.toFloat(),
                        c.patch.toFloat(), c.tone.toFloat(), c.paint.toFloat(),
                        c.bloom.toFloat(),
                    )
                } else {
                    if (r < bloomFloor) r = bloomFloor
                    kit.paths[c.paint].addCircle(p.x.toFloat(), p.y.toFloat(), r.toFloat(), NativePath.Direction.CW)
                }
                // Identity, not equality: this cell came out of the same list
                // the mark was picked from, and comparing every field of every
                // planted cell every frame is not free.
                if (c === mark) marked = floatArrayOf(p.x.toFloat(), p.y.toFloat(), r.toFloat())
            }

            Field.Kind.DOT -> {
                if (r < 0.75) r = 0.75
                // Close enough in, and only on ground the view is actually
                // looking at, a tuft grows the grass it stands for. The dot
                // stays underneath as the root of the clump, shrinking as the
                // blades come up, so the ground thickens rather than swapping
                // for something else.
                //
                // Vegetation only: water and bare dirt keep their dots, being
                // neither of them grass.
                val stand = Field.grassStand(lens.tilt, p.y, h)
                if (stand > 0.02 && r > Field.GRASS_AT &&
                    c.paint < Field.VEG.size && tufts < Field.GRASS_BUDGET
                ) {
                    tufts++
                    val g = ((r - Field.GRASS_AT) / Field.GRASS_AT).coerceIn(0.0, 1.0).toFloat()
                    addTuft(kit, c.paint, c.tone, p.x.toFloat(), p.y.toFloat(), r.toFloat(), g, stand.toFloat())
                    r *= 1.0 - 0.35 * g * stand
                }
                kit.paths[c.paint].addCircle(p.x.toFloat(), p.y.toFloat(), r.toFloat(), NativePath.Direction.CW)
            }
        }
    }
    }

    for (bucket in kit.paths.indices) {
        if (kit.paths[bucket].isEmpty) continue
        kit.fill.color = palette[bucket].toInt()
        kit.fill.alpha = (Field.alphaFor(bucket) * 255).toInt()
        canvas.drawPath(kit.paths[bucket], kit.fill)
    }

    if (!kit.stem.isEmpty) {
        kit.till.strokeWidth = if (tillWidth > 0) tillWidth else 0.6f
        canvas.drawPath(kit.stem, kit.till)
    }

    drawPatchOutlines(canvas, patches, cam, lens, w, h, kit, unit)

    for (b in blooms) {
        val patch = patches[b[3].toInt()]
        val kind = Field.kindOf(patch, b[6].toInt())
        val picture = art[kind]
        // How far this bloom is into the artwork. Zero until it is big enough
        // to be worth it, one once the drawn flower has nothing left to add.
        val shown = if (picture == null) 0f else
            ((b[2] - Field.ARTWORK_AT) / Field.ARTWORK_FADE).coerceIn(0.0, 1.0).toFloat()
        if (shown < 1f) {
            drawFlower(canvas, kind, b[0], b[1], b[2], b[4], b[5].toInt(), kit, 1f - shown)
        }
        if (shown > 0f && picture != null) {
            drawBloom(canvas, picture, b[0], b[1], b[2], shown, kit)
        }
    }

    marked?.let { drawMark(canvas, it[0], it[1], it[2], kit, unit) }

    drawTags(canvas, patches, cam, lens, w, h, kit, tagInk, unit)
}

private fun drawPatchOutlines(
    canvas: android.graphics.Canvas,
    patches: List<Field.Patch>,
    cam: Field.Camera,
    lens: Field.Lens,
    w: Double,
    h: Double,
    kit: DrawKit,
    unit: Float,
) {
    kit.outline.strokeWidth = 1.2f * unit
    for (patch in patches) {
        kit.ring.rewind()
        var visible = false
        val pts = ArrayList<Offset>(patch.ring.size)
        for (spot in patch.ring) {
            Field.project(spot.x, spot.y, Terrain.heightAt(spot.x, spot.y), cam, lens, w, h, kit.point)
            if (kit.point.x > -90 && kit.point.x < w + 90 && kit.point.y > -90 && kit.point.y < h + 90) {
                visible = true
            }
            pts += Offset(kit.point.x.toFloat(), kit.point.y.toFloat())
        }
        if (!visible) continue
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            if (i == 0) kit.ring.moveTo(a.x, a.y)
            kit.ring.quadTo(a.x, a.y, (a.x + b.x) / 2, (a.y + b.y) / 2)
        }
        kit.ring.close()
        canvas.drawPath(kit.ring, kit.outline)
    }
}

/**
 * One tuft of grass, added to the ground's own colour buckets.
 *
 * Blades go into the same paths the dots do, so a field of grass still costs
 * the dozen fills the field has always cost -- the whole reason this screen
 * draws on the native canvas. What it costs instead is path building, four
 * operations a blade, which is why [Field.GRASS_BUDGET] exists.
 *
 * Every blade is a function of the cell's own tone, so a tuft is the same tuft
 * every frame. Grass built from a running random crawls as you pan across it,
 * which reads as the ground being alive in a way nothing else in Harbor is.
 *
 * [grown] runs nought to one across [Field.GRASS_AT] and twice it, and is what
 * makes the blades rise out of the dot instead of appearing on top of it.
 * [stand] is the other half of the same idea and comes from the camera rather
 * than the cell: grass lies back down as the view tips toward plan, and toward
 * the far end of a tipped one.
 */
private fun addTuft(
    kit: DrawKit,
    paint: Int,
    tone: Double,
    x: Float,
    y: Float,
    r: Float,
    grown: Float,
    /** How far the blades are out of the ground. See [Field.grassStand]. */
    stand: Float,
) {
    val blades = 4 + (tone * 3).toInt()
    val reach = r * (1f + 2.2f * grown) * stand
    val half = r * 0.28f
    // One step up the vegetation ladder is the lighter green. A blade or two
    // in it catches the light and gives the clump some depth, and because it
    // is a bucket that is already being filled it is free.
    val lit = if (paint > 0) paint - 1 else paint
    for (b in 0 until blades) {
        val a = Field.wisp(tone, b).toFloat()
        val c = Field.wisp(tone, b + 8).toFloat()
        // Blades rise from about the same root and fan, rather than standing
        // in a row. A row is a comb; a fan is a tuft.
        val bx = x + (a - 0.5f) * r * 0.9f
        val h = reach * (0.6f + a * 0.6f)
        val lean = (c - 0.5f) * h * 0.85f
        val path = kit.paths[if (c > 0.62f) lit else paint]
        path.moveTo(bx - half, y)
        path.quadTo(bx - half * 0.3f + lean * 0.3f, y - h * 0.55f, bx + lean, y - h)
        path.quadTo(bx + half * 0.3f + lean * 0.3f, y - h * 0.55f, bx + half, y)
        path.close()
    }
}

/**
 * One bloom, as the artwork, standing on the cell that grew it.
 *
 * Anchored by its foot rather than its middle: the artwork is a flower head
 * seen face on, and the point it was planted at is the bottom of it. Centring
 * it the way the drawn flower is centred would sink half of every bloom into
 * the ground.
 */
private fun drawBloom(
    canvas: android.graphics.Canvas,
    picture: Bitmap,
    x: Float,
    y: Float,
    r: Float,
    fade: Float,
    kit: DrawKit,
) {
    // Two radii tall, footed one radius under the cell -- which puts the
    // middle of the bloom exactly on the point the drawn flower was centred
    // on, so the handover moves nothing. A bloom that jumped up the screen as
    // it resolved would make the swap visible, and not seeing the swap is the
    // whole reason there is a fade.
    val h = r * 2.0f
    val w = h * picture.width / picture.height
    val foot = y + r * 1.0f
    kit.art.alpha = (255 * fade).toInt()
    kit.rect.set(x - w / 2f, foot - h, x + w / 2f, foot)
    canvas.drawBitmap(picture, null, kit.rect, kit.art)
}

/**
 * One flower.
 *
 * Petals are ellipses walked around the centre, rotated to face outward, in
 * the kind's own colours.
 *
 * This is a rosette seen from above, and deliberately *not* the faced
 * silhouette [FlowerMark] draws. The field is a garden looked down on, and a
 * bloom eleven pixels across on a view holding hundreds of them has to be one
 * cheap fill per petal; here a flower is told by its colour, not its outline.
 * Anything that wants the sheet's actual shape should be drawing at
 * [FlowerMark]'s size.
 */
private fun drawFlower(
    canvas: android.graphics.Canvas,
    kind: FlowerKind,
    x: Float,
    y: Float,
    r: Float,
    tone: Float,
    paint: Int,
    kit: DrawKit,
    /** Dimmed as the artwork comes up underneath it. See [Field.ARTWORK_FADE]. */
    fade: Float = 1f,
) {
    val spec = Flowers.spec(kind)
    val petals = spec.petals
    val spin = tone * 57.29578f

    // The sheet's geometry, seen from above: every petal is anchored at the
    // flower's centre and reaches a full radius at the tip, rather than being
    // pushed out along its own spoke. That anchoring is what makes the petals
    // overlap near the throat, and the overlap is the whole of the depth.
    //
    // Laid at 70% rather than composited with multiply. Multiply is what the
    // single-flower renderer uses and is far too costly here, where a wide
    // view can carry hundreds of blooms; the same colour at 70% over itself
    // darkens in much the same way, for the price of an ordinary fill.
    val rx = r * 0.38f
    val ry = r * 0.60f
    val lift = r * 0.40f
    kit.fill.color = (if (paint % 2 == 1) spec.petal else spec.petalDeep).toInt()
    kit.fill.alpha = (178 * fade).toInt()
    for (i in 0 until petals) {
        canvas.save()
        canvas.rotate(i * 360f / petals + spin, x, y)
        kit.rect.set(x - rx, y - lift - ry, x + rx, y - lift + ry)
        canvas.drawOval(kit.rect, kit.fill)
        canvas.restore()
    }

    kit.fill.color = spec.heart.toInt()
    kit.fill.alpha = (204 * fade).toInt()
    canvas.drawCircle(x, y, r * 0.22f, kit.fill)
}

/**
 * The flower just grown, ringed.
 *
 * A patch of twenty is twenty flowers and no answer to "which one was mine" --
 * and the newest is nowhere in particular, because planting order is a
 * scatter rather than a spiral. So it is marked: two gold rings, the inner one
 * tight enough to read as belonging to that bloom and the outer faint one wide
 * enough to catch the eye from a long way back, which is the framing somebody
 * pinching out ends up in.
 *
 * Gold because gold is what the app uses to mean *this one*, and a ring
 * because anything drawn over the flower would hide the thing it is pointing
 * at. It stays until the next flower is grown, when it moves to that one.
 */
private fun drawMark(
    canvas: android.graphics.Canvas,
    x: Float,
    y: Float,
    r: Float,
    kit: DrawKit,
    unit: Float,
) {
    val inner = kotlin.math.max(r * 2.1f, 9f * unit)
    kit.markRing.strokeWidth = 1.6f * unit
    kit.markRing.alpha = 235
    canvas.drawCircle(x, y, inner, kit.markRing)

    kit.markRing.strokeWidth = 1.1f * unit
    kit.markRing.alpha = 92
    canvas.drawCircle(x, y, inner + 5f * unit, kit.markRing)
    kit.markRing.alpha = 255
}

/** Whose patch is whose, with colliding labels nudged apart. */
private fun drawTags(
    canvas: android.graphics.Canvas,
    patches: List<Field.Patch>,
    cam: Field.Camera,
    lens: Field.Lens,
    w: Double,
    h: Double,
    kit: DrawKit,
    ink: Color,
    unit: Float,
) {
    val tagH = 20 * unit
    val lift = 34 * unit
    val pad = 20 * unit
    kit.tagText.textSize = 11 * unit
    kit.tagText.color = android.graphics.Color.argb(
        255,
        (ink.red * 255).toInt(),
        (ink.green * 255).toInt(),
        (ink.blue * 255).toInt(),
    )
    val tags = ArrayList<Tag>()
    for (patch in patches) {
        Field.project(patch.x, patch.y, Terrain.heightAt(patch.x, patch.y), cam, lens, w, h, kit.point)
        if (kit.point.x < -50 || kit.point.x > w + 50) continue
        if (kit.point.y < -20 || kit.point.y > h + 40) continue
        // The zoom controls own the top-right corner; a tag under them is
        // unreadable anyway.
        if (kit.point.x > w - 80 * unit && kit.point.y < 210 * unit) continue
        tags += Tag(
            patch.label + " · " + patch.calls,
            kit.point.x.toFloat(),
            kit.point.y.toFloat(),
            kit.point.y.toFloat(),
        )
    }

    tags.sortBy { it.y }
    for (i in 1 until tags.size) {
        for (j in 0 until i) {
            if (kotlin.math.abs(tags[i].x - tags[j].x) < 104 * unit &&
                kotlin.math.abs(tags[i].y - tags[j].y) < 28 * unit
            ) {
                tags[i].y = tags[j].y + 28 * unit
            }
        }
    }

    for (tag in tags) {
        val wide = kit.tagText.measureText(tag.text) + pad
        val top = tag.y - lift - tagH
        kit.rect.set(tag.x - wide / 2, top, tag.x + wide / 2, top + tagH)
        canvas.drawRoundRect(kit.rect, 5 * unit, 5 * unit, kit.tagBack)
        // Only an unnudged tag still points at its patch; a moved one would
        // point at open ground.
        if (kotlin.math.abs(tag.y - tag.stemY) < 2) {
            kit.ring.rewind()
            kit.ring.moveTo(tag.x - 5 * unit, top + tagH)
            kit.ring.lineTo(tag.x + 5 * unit, top + tagH)
            kit.ring.lineTo(tag.x, top + tagH + 7 * unit)
            kit.ring.close()
            canvas.drawPath(kit.ring, kit.tagBack)
        }
        canvas.drawText(tag.text, tag.x, top + tagH * 0.72f, kit.tagText)
    }
}

/** Zoom in, zoom out, and pull back to the whole island. */
@Composable
private fun FieldControls(
    onIn: () -> Unit,
    onOut: () -> Unit,
    onFit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ControlButton("+", "Zoom in", onIn)
        ControlButton("−", "Zoom out", onOut)
        ControlButton("⤡", "Pull back", onFit)
    }
}

@Composable
private fun ControlButton(glyph: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            style = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/** Where you are on the dial between map and landscape. */
@Composable
private fun FieldReadout(relative: Double, tilt: Double, modifier: Modifier = Modifier) {
    val stage = when {
        tilt < 0.02 -> "plan"
        tilt > 0.98 -> "landscape"
        else -> "tipping"
    }
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        Text(
            String.format("%.1f×", relative),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = muted),
        )
        Box(
            Modifier
                .width(46.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(tilt.toFloat().coerceIn(0f, 1f))
                    .background(Gold),
            )
        }
        Text(
            stage,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = muted),
        )
    }
}

/** What a patch is planted with before anyone has chosen a flower for it. */
private fun defaultFlower(tone: Tone): FlowerKind = when (tone) {
    Tone.GOLD -> FlowerKind.GLAD_WE_TALKED
    Tone.GREEN -> FlowerKind.STEADIER_NOW
    Tone.ORANGE -> FlowerKind.FELT_LOVED
    Tone.SKY -> FlowerKind.WORTH_SLOWING_DOWN
}
