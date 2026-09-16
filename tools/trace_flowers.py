"""Turn the vectorised specimen sheet into app/.../ui/FlowerArt.kt.

The flowers in the app are the sheet's own outlines, not an approximation of
them, and this is how they get there:

  1. The flower sheet (a raster plate of twenty blooms, five across) is run
     through a vectoriser. Any tracer will do; the one used produced flat
     colour bands and absolute M/L/C/Z paths, which is all this reads.
  2. Point SRC at that SVG and run this file. It writes FlowerArt.kt beside
     itself; copy it into app/src/main/java/app/harbor/ui/.

What it has to work out for itself, because a trace has no structure:

  - **Which shapes are a flower.** The plate also carries a card, a caption, a
     stem and two leaves per tile. Neutral fills (card, text, shadow) go by
     colour; the stem goes by being tall and thin; the leaves go by hanging
     below the bloom, off to one side, or by not touching the bloom at all.
     Colour alone cannot do this -- the flowers reuse each other's palettes,
     and two of them are green.
  - **Where a bloom sits.** Each is centred on its own box and scaled so its
     longer axis runs to +-SPAN, so the renderer can ask for a radius and get
     a flower that fits it.
  - **Which bands matter.** Anything under 3% of the bloom is dropped from the
     coarse pass the garden uses when a flower is a few pixels wide.

Paint order is the SVG's own document order and must stay that way: the bands
overlap, and sorting them by anything at all turns a flower into a blob.

If the sheet changes, change it here and re-run. Editing FlowerArt.kt by hand
works exactly once and is then lost.
"""

import re, math
from collections import Counter, defaultdict

SRC = r"C:\Users\Devansh Gautam\Downloads\image 36 [Vectorized].svg"
s = open(SRC, encoding='utf-8').read()
raw = re.findall(r'<path d="([^"]+)"\s*fill="([^"]+)"', s)

def parse(d):
    out=[]; cur=(0.0,0.0); start=(0.0,0.0); cmd=None
    toks=re.findall(r'[MLCHVZ]|-?\d*\.?\d+(?:[eE]-?\d+)?', d)
    k=0
    while k < len(toks):
        if toks[k] in 'MLCHVZ': cmd=toks[k]; k+=1
        if cmd=='M':
            x=float(toks[k]); y=float(toks[k+1]); k+=2
            out.append(('M',[(x,y)])); cur=(x,y); start=(x,y); cmd='L'
        elif cmd=='L':
            x=float(toks[k]); y=float(toks[k+1]); k+=2
            out.append(('L',[(x,y)])); cur=(x,y)
        elif cmd=='H':
            x=float(toks[k]); k+=1; out.append(('L',[(x,cur[1])])); cur=(x,cur[1])
        elif cmd=='V':
            y=float(toks[k]); k+=1; out.append(('L',[(cur[0],y)])); cur=(cur[0],y)
        elif cmd=='C':
            p=[(float(toks[k]),float(toks[k+1])),(float(toks[k+2]),float(toks[k+3])),(float(toks[k+4]),float(toks[k+5]))]; k+=6
            out.append(('C',p)); cur=p[2]
        elif cmd=='Z':
            out.append(('Z',[])); cur=start
    return out

def bbox(segs):
    xs=[p[0] for c,ps in segs for p in ps]; ys=[p[1] for c,ps in segs for p in ps]
    return (min(xs),min(ys),max(xs),max(ys)) if xs else None

def rgb(h): return (int(h[1:3],16), int(h[3:5],16), int(h[5:7],16))
def lum(c): return 0.2126*c[0]+0.7152*c[1]+0.0722*c[2]

W,H=1354,1426; CW,CH=W/5,H/4
NAMES=["Happy","Upbeat","Loved","Valued","Peaceful","Grounded","Calm","Confident","Inspired","Curious",
       "Hopeful","Reflective","Tense","Motivated","Content","Insecure","Brave","Grateful","Lonely","Anxious"]
KINDS=["GLAD_WE_TALKED","LIGHTER_NOW","FELT_LOVED","SHE_REMEMBERED","EASY_SILENCE","STEADIER_NOW",
       "WORTH_SLOWING_DOWN","SAID_WHAT_I_MEANT","WANT_TO_TRY_SOMETHING","ASKED_MORE_THAN_USUAL",
       "LOOKING_FORWARD","STILL_THINKING_ABOUT_IT","HARD_TO_SHAKE_OFF","TIME_TO_ACTUALLY_DO_IT",
       "NOTHING_LEFT_UNSAID","WONDERING_IF_THAT_LANDED","SAID_THE_HARD_THING","GLAD_SHE_PICKED_UP",
       "WISHED_IT_WAS_LONGER","DREADED_THIS_ONE"]
SPEC={  # petal, petalDeep, heart  (from domain/Flowers.kt)
 "GLAD_WE_TALKED":("#FFD95E","#F0A81E","#B9740C"),
 "LIGHTER_NOW":("#FF7B8A","#EE3B57","#FFD1A8"),
 "FELT_LOVED":("#FF8A5C","#EF4B3C","#FFC26E"),
 "SHE_REMEMBERED":("#C98BE0","#8E3FB0","#5E1F7A"),
 "EASY_SILENCE":("#FFF3D6","#F3E0B4","#E3C98C"),
 "STEADIER_NOW":("#9CC47A","#3E7A46","#2A5733"),
 "WORTH_SLOWING_DOWN":("#9DBBF8","#4E76E8","#2F4FB8"),
 "SAID_WHAT_I_MEANT":("#FFDE72","#F5B92B","#D98F12"),
 "WANT_TO_TRY_SOMETHING":("#FF8878","#E83C3C","#FFCF9A"),
 "ASKED_MORE_THAN_USUAL":("#C4A6F5","#8B63DE","#5F3BA8"),
 "LOOKING_FORWARD":("#FFA48C","#F2604E","#FFD0A0"),
 "STILL_THINKING_ABOUT_IT":("#FDF6E4","#EDDCBE","#CBB48A"),
 "HARD_TO_SHAKE_OFF":("#A8A6F7","#5F5BE0","#E8E4FF"),
 "TIME_TO_ACTUALLY_DO_IT":("#7FB05E","#2F6B39","#1F4B2A"),
 "NOTHING_LEFT_UNSAID":("#FFA86B","#F06A38","#C44A22"),
 "WONDERING_IF_THAT_LANDED":("#B98CE8","#7C45C4","#F0E6FF"),
 "SAID_THE_HARD_THING":("#FF9257","#EE4426","#FFD08A"),
 "GLAD_SHE_PICKED_UP":("#FFB3B8","#F2727F","#FFD9DC"),
 "WISHED_IT_WAS_LONGER":("#9CB6F7","#5B7DE8","#3E5BB8"),
 "DREADED_THIS_ONE":("#FFE07A","#F2B62E","#CF8A12"),
}

items=[]
for d,f in raw:
    if not f.startswith('#'): continue
    segs=parse(d); bb=bbox(segs)
    if not bb: continue
    items.append({'d':d,'f':f.upper(),'bb':bb,'segs':segs})

def tile_of(bb):
    cx=(bb[0]+bb[2])/2; cy=(bb[1]+bb[3])/2
    return int(cx//CW), int(cy//CH)


def fmt(v): return str(int(round(v)))
def curve_bbox(segs):
    xs=[];ys=[];cur=(0,0)
    for c,ps in segs:
        if c in 'ML':
            cur=ps[0]; xs.append(cur[0]); ys.append(cur[1])
        elif c=='C':
            p0=cur
            for j in range(13):
                t=j/12.0; mt=1-t
                xs.append(mt**3*p0[0]+3*mt*mt*t*ps[0][0]+3*mt*t*t*ps[1][0]+t**3*ps[2][0])
                ys.append(mt**3*p0[1]+3*mt*mt*t*ps[0][1]+3*mt*t*t*ps[1][1]+t**3*ps[2][1])
            cur=ps[2]
    return (min(xs),min(ys),max(xs),max(ys))
def inter(a,b,pad):
    w=(a[2]-a[0])*pad; h=(a[3]-a[1])*pad
    return not (a[2]+w < b[0] or b[2] < a[0]-w or a[3]+h < b[1] or b[3] < a[1]-h)

NL="\n"; OUT={}; report=[]
for i,name in enumerate(NAMES):
    c,r=i%5,i//5
    x0,y0,x1,y1=c*CW,r*CH,(c+1)*CW,r*CH+CH*0.62
    pool=[]
    for it in items:
        bb=it['bb']
        if not (bb[0]>=x0-2 and bb[2]<=x1+2 and bb[1]>=y0-2 and bb[3]<=y1+2): continue
        R,G,B=rgb(it['f'])
        if max(R,G,B)-min(R,G,B) < 14: continue          # card, text, shadow
        tb=curve_bbox(it['segs']); w=tb[2]-tb[0]; h=tb[3]-tb[1]
        if w < CW*0.06 and h > w*2.5: continue           # the stem
        pool.append(dict(it, tb=tb, area=w*h, i=len(pool)))
    seed=max(pool,key=lambda z:z['area'])
    cluster=[seed]; box=list(seed['tb']); grew=True
    while grew:
        grew=False
        for it in pool:
            if it in cluster: continue
            if inter(tuple(box),it['tb'],0.04):
                cluster.append(it)
                box=[min(box[0],it['tb'][0]),min(box[1],it['tb'][1]),
                     max(box[2],it['tb'][2]),max(box[3],it['tb'][3])]
                grew=True
    bloom=seed['tb']; cw_=box[2]-box[0]; mid=(box[0]+box[2])/2
    def below_ok(it):
        if (it['tb'][1]+it['tb'][3])/2 <= bloom[3]: return True
        w=it['tb'][2]-it['tb'][0]
        h=it['tb'][3]-it['tb'][1]
        return (w < cw_*0.30 and abs((it['tb'][0]+it['tb'][2])/2 - mid) < cw_*0.16
                and inter(bloom, it['tb'], 0.0)
                and w*h > (bloom[2]-bloom[0])*(bloom[3]-bloom[1])*0.0015)
    cluster=[z for z in cluster if below_ok(z)]
    # anything that does not actually touch the bloom is a fragment of the
    # plate around it, not the flower: a leaf tip, a stem nub, trace noise.
    keep=[seed]; grew=True
    while grew:
        grew=False
        for z in cluster:
            if z in keep: continue
            if any(inter(q['tb'], z['tb'], 0.01) for q in keep):
                keep.append(z); grew=True
    cluster=keep
    box=[min(z['tb'][0] for z in cluster), min(z['tb'][1] for z in cluster),
         max(z['tb'][2] for z in cluster), max(z['tb'][3] for z in cluster)]
    sel=sorted(cluster,key=lambda z:z['i'])
    X0,Y0,X1,Y1=box; cx=(X0+X1)/2; cy=(Y0+Y1)/2
    half=max(X1-X0,Y1-Y0)/2; k=1000.0/half; area_head=(X1-X0)*(Y1-Y0)
    kept=[]
    for it in sel:
        if it['area'] < area_head*0.0008: continue
        out=[]
        for c2,ps in it['segs']:
            if c2=='Z': out.append('Z'); continue
            out.append(c2+' '+' '.join("%s %s"%(fmt((p[0]-cx)*k),fmt((p[1]-cy)*k)) for p in ps))
        kept.append({'d':' '.join(out),'f':it['f'],'area':it['area'],
                     'y0':(it['tb'][1]-cy)*k,'y1':(it['tb'][3]-cy)*k})
    for z in kept: z['big'] = z['area'] >= area_head*0.03
    OUT[KINDS[i]]=kept
    report.append((name,len(kept),sum(len(z['d']) for z in kept)))
for n,c2,b in report: print("%-11s %3d paths %6dB"%(n,c2,b))
print("TOTAL", sum(c2 for _,c2,_ in report), "paths", sum(b for _,_,b in report), "bytes")


# --- emit the Kotlin plate --------------------------------------------------
L=[]
L.append('''package app.harbor.ui

import app.harbor.domain.FlowerKind

/**
 * The flower plate, traced.
 *
 * Twenty flowers, as outlines taken straight off the specimen sheet rather
 * than approximated by a shape function. Everything here is generated: the
 * sheet was vectorised, each bloom's shapes were lifted out of the plate
 * (leaves, stem, card and caption dropped), centred on the bloom's own box and
 * scaled so the longer axis runs to [SPAN]. Nothing in this file is written by
 * hand, and editing it by hand is the wrong move -- re-run the trace.
 *
 * It is still drawn geometry, not artwork: these are Bezier outlines filled at
 * draw time, so a flower is resolution-free and costs no bitmap. That is the
 * rule in `docs/05-changing-the-ui.md` and this keeps it.
 *
 * ## Why the colours are here and not taken from [app.harbor.domain.Flowers]
 *
 * The vectoriser turned each bloom's gradient into a handful of flat bands,
 * and those bands *are* the shading -- there is no other lighting in the
 * drawing. The spec's three colours are a summary of the same sheet, kept
 * because the field, the garden marks and the glow still need one colour for a
 * flower. Where the two disagree, this file is the finer record of the same
 * source, not a second opinion.
 *
 * [Mark.y0] and [Mark.y1] are a band's own vertical extent, which is what lets
 * the renderer put a soft gradient across each band and dissolve the stepping
 * the trace introduced.
 */
internal object FlowerArt {

    /** One traced band: its outline, its colour, its extent, its importance. */
    internal class Mark(
        val d: String,
        val colour: Long,
        /** Survives the coarse pass: this band carries the flower's shape. */
        val big: Boolean,
        val y0: Float,
        val y1: Float,
    )

    /** The unit the path data is drawn in: the long axis runs to +-this. */
    const val SPAN = 1000f

    val PLATE: Map<FlowerKind, List<Mark>> = mapOf(''')
for i,k in enumerate(KINDS):
    L.append('        FlowerKind.%s to listOf(' % k)
    for z in OUT[k]:
        L.append('            Mark("%s", 0xFF%s, %s, %df, %df),'
                 % (z['d'], z['f'][1:], 'true' if z['big'] else 'false',
                    round(z['y0']), round(z['y1'])))
    L.append('        ),')
L.append('    )\n}')
out='\n'.join(L)+'\n'
open('FlowerArt.kt','w',encoding='utf-8',newline='\n').write(out)
print("FlowerArt.kt", len(out), "bytes,", out.count('\n'), "lines")
