"""Build smooth, articulated character meshes from the four supplied design references.

No voxel approximation or runtime meshing: generated indexed quad surfaces are loaded
once as articulated custom geometry. Units are 1/16 block, Y points down.
"""
from pathlib import Path
import gzip, json, math

OUT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/bigfatfish'
PI = math.pi
SKIN, HAIR, NAVY, WHITE, CYAN, GOLD, SOLE, DARK = range(8)
ROOT_HAIR=8
TAIL, LACE, BLOUSE, RIBBON, SUMMER_BOW, APRON, STOCKING = range(9,16)
NAVY_EMBROIDERY = 16
NAVY_CUFF = 17
BOW_BLUE = 18
EMBLEM_BLUE = 19
PLAIN_WHITE = 20
TAIL_FIN = 21
BOW_CLOTH = 22
SKIRT_PLEAT_DEPTH = .075

def node(name, pose=(0,0,0)):
    return {'name':name, 'pose':pose, 'quads':[], 'children':[]}

def child(parent, name, pose=(0,0,0)):
    part=node(name,pose); parent['children'].append(part); return part

def uv(material,u,v):
    if material==NAVY: v*=.40
    if material==NAVY_EMBROIDERY: material=NAVY
    if material==NAVY_CUFF: material=NAVY;v=.51+v*.45
    if material==BOW_BLUE: material=HAIR;v=.64+v*.30
    if material==BOW_CLOTH: material=HAIR;u=.43+.10*u;v=.78+.06*v
    if material==EMBLEM_BLUE: material=HAIR;u=.50;v=.25
    if material==PLAIN_WHITE: material=WHITE;v=.05+v*.40
    if material==ROOT_HAIR: v=0.04+v*0.55
    if material==TAIL: u=.18+.12*u;v=.24+.16*v
    if material==TAIL_FIN:
        # Follow the painted left fluke, staying inside its black outline.
        t=v;u=.45-.33*t+(u-.5)*.10*math.sin(PI*t)
        v=.60-.46*t-.07*math.sin(PI*t);material=TAIL
    return ((material%4+0.07+u*0.86)/4,(material//4+0.07+v*0.86)/4)

def quad(part, positions, material, coords=None):
    coords=coords or [(0.2,0.1),(0.8,0.1),(0.8,0.9),(0.2,0.9)]
    part['quads'].append([[round(c,5) for c in (*p,*uv(material,*t))] for p,t in zip(positions,coords)])

def ellipsoid(part, center, radius, material, rings=16, sides=32, lat=(-PI/2,PI/2)):
    def point(i,j):
        a=lat[0]+(lat[1]-lat[0])*i/rings; b=j*2*PI/sides
        return (center[0]+radius[0]*math.cos(a)*math.sin(b),center[1]+radius[1]*math.sin(a),center[2]+radius[2]*math.cos(a)*math.cos(b))
    for i in range(rings):
        for j in range(sides):
            quad(part,[point(i,j),point(i,j+1),point(i+1,j+1),point(i+1,j)],material,[(j/sides,i/rings),((j+1)/sides,i/rings),((j+1)/sides,(i+1)/rings),(j/sides,(i+1)/rings)])

def face(part, center, radius, baby=False, back=False, lat_range=(-PI/2,PI/2), material=None):
    # Front and rear halves share their temple/jaw boundary exactly. The face
    # keeps planar UVs, so the painted features and blink remain in the same place.
    rings=32 if baby else 64;sides=48 if baby else 64
    def p(i,j):
        lon=-PI/2+PI*j/sides
        end=lat_range[1]-(.34*math.cos(lon)**2 if material==ROOT_HAIR and not back else 0)
        lat=lat_range[0]+(end-lat_range[0])*i/rings
        x=math.cos(lat)*math.sin(lon); y=math.sin(lat)
        lower=max(0,min(1,(y-.12)/.88))
        jaw=1-(.18 if baby else .52)*lower*lower*(3-2*lower)
        if not baby:
            jaw*=1-.055*math.exp(-((y-.30)/.28)**2)
        chin=max(0,(y-.30)/.70)
        yy=y-(.10 if baby else .18)*chin**1.5
        if not baby and y>.08:
            # Keep the eye line fixed while shortening the lower oval. Both
            # skin halves share this transition, and the original UV keeps
            # the mouth attached to the reshaped cheeks and chin.
            lower_face=min(1,(y-.08)/.45)
            lower_face=lower_face*lower_face*(3-2*lower_face)
            yy-=.15*(yy-.08)*lower_face
        front=max(0,math.cos(lat)*math.cos(lon))
        if back:
            z=radius[2]*front
        else:
            # Broad cheek planes curve into recessed eyes and a small projecting
            # nose; the lower chin recedes towards the neck in side profile.
            z=-radius[2]*front**(.82 if baby else .77)
            chin_recede=(.08 if baby else .16)*max(0,(y-.45)/.55)
            z*=1-chin_recede
            def bump(cx,cy,sx,sy):return math.exp(-((x-cx)/sx)**2-((y-cy)/sy)**2)
            cheeks=(.17 if baby else .075)*(bump(-.48,.25,.25,.22)+bump(.48,.25,.25,.22))
            sockets=.05*(bump(-.46,.01,.23,.15)+bump(.46,.01,.23,.15)) if baby else .14*(bump(-.46,-.035,.25,.105)+bump(.46,-.035,.25,.105))
            eyelids=0
            if not baby:
                # Shallow almond-shaped lid planes follow the painted eye
                # opening, with a lifted outer corner and a softer lower lid.
                u=(abs(x)-.46)/.27;edge=math.exp(-u**8)
                upper=-.145+.10*u*u-.025*u
                lower_lid=.075-.055*u*u-.010*u
                eyelids=edge*(.045*math.exp(-((y-upper)/.037)**2)+
                              .018*math.exp(-((y-lower_lid)/.035)**2))
            brows=.065*(bump(-.45,-.34,.28,.10)+bump(.45,-.34,.28,.10))
            bridge=(.10 if baby else .23)*bump(0,-.025,.085,.23)
            tip=(.18 if baby else .36)*bump(0,.20,.10,.085)
            lips=.06*bump(0,.47,.23,.13) if baby else .09*bump(0,.47,.20,.07)
            # Blend the mouth surround forwards with the chin instead of
            # leaving it recessed behind an isolated projecting nose tip.
            mouth_plane=0 if baby else .06*bump(0,.49,.37,.24)
            below_lip=0 if baby else .045*bump(0,.57,.23,.065)
            chin_tip=(.055 if baby else .12)*bump(0,.75,.28,.17)
            z-=(cheeks-sockets+eyelids+brows+bridge+tip+lips+mouth_plane-below_lip+chin_tip)*front**2
        if not baby:
            # Move the lower jaw's centre forward into a chin, rather than
            # closing the ellipsoid at the neck centre. Both halves share it.
            t=max(0,min(1,(y-.48)/.52))
            z-=.70*t*t*(3-2*t)
        texture_y=y
        if material is None and not back:
            # The reference has a wider, flatter eye opening. Locally widen
            # the sampled V interval so both open and closed eyes become
            # shorter without moving the brows, nose or mouth.
            eye_weight=math.exp(-((abs(x)-.46)/.30)**4)
            eye_y=y if baby else y+.035
            texture_y+=(.22 if baby else .30)*eye_y*math.exp(-(eye_y/(.34 if baby else .30))**4)*eye_weight
        texture=uv(SKIN if material is None else material,x/2+.5,y/2+.5) if back or material is not None else (x/2+.5,texture_y/2+.5)
        return [round(center[0]+radius[0]*x*jaw,5),round(center[1]+radius[1]*yy,5),round(center[2]+z,5),
                round(texture[0],5),round(texture[1],5)]
    for i in range(rings):
        for j in range(sides):
            points=[p(i,j),p(i+1,j),p(i+1,j+1),p(i,j+1)]
            part['quads'].append(list(reversed(points)) if back else points)

def catmull(points, t):
    t=min(t,len(points)-1.000001); i=int(t); f=t-i
    p0=points[max(0,i-1)];p1=points[i];p2=points[min(i+1,len(points)-1)];p3=points[min(i+2,len(points)-1)]
    return tuple(0.5*((2*b)+(-a+c)*f+(2*a-5*b+4*c-d)*f*f+(-a+3*b-3*c+d)*f*f*f) for a,b,c,d in zip(p0,p1,p2,p3))

def profile_at_y(profiles,y):
    lo,hi=0,len(profiles)-1
    for _ in range(12):
        mid=(lo+hi)/2
        if catmull(profiles,mid)[0]<y: lo=mid
        else: hi=mid
    return catmull(profiles,(lo+hi)/2)

def tube(part, points, widths, material, sections=28, sides=12, depth=1, cap_end=False, start=0, vertical_ends=False):
    rows=[]
    for i in range(sections+1):
        t=start+i/sections*(len(points)-1-start); p=catmull(points,t)
        previous=catmull(points,max(0,t-0.01)); following=catmull(points,min(len(points)-1.000001,t+0.01))
        tangent=[b-a for a,b in zip(previous,following)]; length=math.sqrt(sum(c*c for c in tangent)) or 1
        tangent=[c/length for c in tangent]
        if vertical_ends and i in (0,sections): tangent=[0,1,0]
        side=[tangent[1],-tangent[0],0]; length=math.sqrt(sum(c*c for c in side)) or 1
        side=[c/length for c in side]
        if length<0.01: side=[1,0,0]
        normal=[tangent[1]*side[2]-tangent[2]*side[1],tangent[2]*side[0]-tangent[0]*side[2],tangent[0]*side[1]-tangent[1]*side[0]]
        k=min(int(t),len(widths)-2);f=t-k;w=widths[k]*(1-f)+widths[k+1]*f
        rows.append([tuple(p[c]+w*(math.cos(a)*side[c]+math.sin(a)*normal[c]*depth) for c in range(3)) for a in [j*2*PI/sides for j in range(sides)]])
    if cap_end:
        for i in range(1,7):
            angle=i*PI/12
            rows.append([tuple(p[c]+w*(math.sin(angle)*tangent[c]+
                         math.cos(angle)*(math.cos(a)*side[c]+depth*math.sin(a)*normal[c]))
                         for c in range(3)) for a in [j*2*PI/sides for j in range(sides)]])
    surface_sections=len(rows)-1
    for i in range(surface_sections):
        for j in range(sides):
            k=(j+1)%sides
            quad(part,[rows[i][j],rows[i][k],rows[i+1][k],rows[i+1][j]],material,[(j/sides,i/surface_sections),((j+1)/sides,i/surface_sections),((j+1)/sides,(i+1)/surface_sections),(j/sides,(i+1)/surface_sections)])

def hair_v(height,bounds,highlight_height=None):
    value=max(0,min(1,(height-bounds[0])/(bounds[1]-bounds[0])))
    if highlight_height is None: return value
    peak=(highlight_height-bounds[0])/(bounds[1]-bounds[0])
    return value*.20/peak if value<=peak else .20+(value-peak)*.80/(1-peak)


def hair_lock(part, points, widths, material=HAIR, sections=56, thickness=0.16, uv_range=(0,1), roll=0,bend_limit=None, strands=1, uv_height=None, twist=0, scalp=None, split_ratio=.5, continuous_taper=False, highlight_height=None, embed_root=False, path_offset=None):
    # Sculpted ribbon section: broad front/back sheets joined by thin rounded edges.
    frames=[]; sides=12
    if continuous_taper:
        samples=[catmull(points,i/sections*(len(points)-1)) for i in range(sections+1)]
        if path_offset is not None: samples=[path_offset(p) for p in samples]
        distances=[0.]
        for a,b in zip(samples,samples[1:]): distances.append(distances[-1]+math.dist(a,b))
    side=[math.cos(roll),0,math.sin(roll)]
    for i in range(sections+1):
        t=i/sections*(len(points)-1); p=catmull(points,t)
        a=catmull(points,max(0,t-0.01));b=catmull(points,min(len(points)-1.000001,t+0.01))
        if path_offset is not None: p,a,b=path_offset(p),path_offset(a),path_offset(b)
        tangent=[b[c]-a[c] for c in range(3)]
        length=math.sqrt(sum(c*c for c in tangent)) or 1
        tangent=[c/length for c in tangent]
        along=sum(side[c]*tangent[c] for c in range(3))
        side=[side[c]-along*tangent[c] for c in range(3)]
        length=math.sqrt(sum(c*c for c in side))
        if length<1e-6: side=[0,1,0];length=1
        side=[c/length for c in side]
        normal=[tangent[1]*side[2]-tangent[2]*side[1],tangent[2]*side[0]-tangent[0]*side[2],tangent[0]*side[1]-tangent[1]*side[0]]
        # Turn the ribbon about its centerline without accumulating rotation
        # in the transported frame. Roots and tips retain their original roll.
        angle=twist*math.sin(PI*i/sections)**2
        ribbon_side=[math.cos(angle)*side[c]+math.sin(angle)*normal[c] for c in range(3)]
        normal=[math.cos(angle)*normal[c]-math.sin(angle)*side[c] for c in range(3)]
        k=min(int(t),len(widths)-2);f=t-k;f=f*f*(3-2*f);w=widths[k]*(1-f)+widths[k+1]*f
        if continuous_taper:
            # No width plateaus at individual path controls: they made the
            # short fringe look like a bulb joined to a thin spike.
            # Taper by traveled distance: tightly spaced path controls must
            # not pinch the lock into a bulb followed by an abrupt point.
            u=distances[i]/distances[-1] if distances[-1]>1e-8 else i/sections
            w=widths[0]*(1-u)+widths[-1]*u+max(widths)*math.sin(PI*u)**.85*(1-.65*u)
        if bend_limit is not None:
            # A swept ellipse must fit inside its local turning radius.
            ap=[a[c]-p[c] for c in range(3)];bp=[b[c]-p[c] for c in range(3)]
            cross=[ap[1]*bp[2]-ap[2]*bp[1],ap[2]*bp[0]-ap[0]*bp[2],ap[0]*bp[1]-ap[1]*bp[0]]
            area=math.sqrt(sum(v*v for v in cross))
            curvature=[ap[c]+bp[c] for c in range(3)]
            along=sum(curvature[c]*tangent[c] for c in range(3))
            curvature=[curvature[c]-along*tangent[c] for c in range(3)]
            magnitude=math.sqrt(sum(v*v for v in curvature))
            if area>1e-10 and magnitude>1e-10:
                radius=math.sqrt(sum(v*v for v in ap)*sum(v*v for v in bp)*sum((b[c]-a[c])**2 for c in range(3)))/(2*area)
                direction=[v/magnitude for v in curvature]
                reach=math.sqrt(sum(ribbon_side[c]*direction[c] for c in range(3))**2+
                                (thickness*sum(normal[c]*direction[c] for c in range(3)))**2)
                if reach>1e-8: w=min(w,bend_limit*radius/reach)
        frames.append([p,ribbon_side,normal,w])
    if bend_limit is not None:
        # Spread local curvature narrowing along the strand without increasing
        # any radius beyond its bend limit. Both passes bound the taper slope.
        for indices in [range(1,len(frames)),range(len(frames)-2,-1,-1)]:
            step=-1 if indices.step>0 else 1
            for i in indices:
                previous=frames[i+step]
                frames[i][3]=min(frames[i][3],previous[3]+.5*math.dist(frames[i][0],previous[0]))
    if scalp is not None:
        center,radii=scalp
        radii=tuple(r+.10 for r in radii)
        shifts=[];directions=[]
        for p,side,normal,w in frames:
            distance=math.hypot(p[0]-center[0],p[2]-center[2])
            direction=((p[0]-center[0])/distance,0,(p[2]-center[2])/distance)
            directions.append(direction)
            shift=0
            a=sum((direction[c]/radii[c])**2 for c in range(3))
            # Move the whole section, including both split locks, clear of
            # the rear scalp. Its ellipsoid conservatively encloses the jaw.
            for j in range(sides):
                v=[p[c]-center[c]+w*(math.cos(j*2*PI/sides)*side[c]+
                   thickness*math.sin(j*2*PI/sides)*normal[c]) for c in range(3)]
                b=sum(v[c]*direction[c]/radii[c]**2 for c in range(3))
                inside=sum((v[c]/radii[c])**2 for c in range(3))-1
                if inside<0:
                    shift=max(shift,(-b+math.sqrt(b*b-a*inside))/a)
            shifts.append(shift)
        # A smooth upper envelope avoids a crease where the locks leave the
        # scalp, while never reducing the required clearance at any section.
        for _ in range(8):
            shifts=[shifts[0],*(max(shifts[j],.25*shifts[j-1]+.5*shifts[j]+.25*shifts[j+1])
                                for j in range(1,len(shifts)-1)),shifts[-1]]
        for i,(frame,direction,shift) in enumerate(zip(frames,directions,shifts)):
            if embed_root:
                # Attach crown roots inside the scalp before bringing the
                # complete ribbon section clear of it along the first bend.
                blend=min(1,i/6);shift*=blend*blend*(3-2*blend)
            frame[0]=tuple(frame[0][c]+direction[c]*shift for c in range(3))
    for strand in range(strands):
        offset=(strand+.5)*2/strands-1
        radius=(.94 if strands>1 else 1)/strands
        if strands==2:
            offset=split_ratio-1 if strand==0 else split_ratio
            radius=.94*(split_ratio if strand==0 else 1-split_ratio)
        last=sections
        if strand%2:
            end_y=frames[-1][0][1]-.12*(frames[-1][0][1]-frames[0][0][1])
            last=next((i for i,frame in enumerate(frames) if i>0 and frame[0][1]>=end_y),sections)
        rows=[]
        for i,(p,side,normal,w) in enumerate(frames[:last+1]):
            taper=max(0,min(1,(i/last-.70)/.30)) if last<sections else 0
            section_radius=radius*(1-taper*taper*(3-2*taper))
            rows.append([tuple(p[c]+w*(offset*side[c]+section_radius*(math.cos(j*2*PI/sides)*side[c]+
                     thickness*math.sin(j*2*PI/sides)*normal[c]))
                     for c in range(3)) for j in range(sides)])
        for i in range(last):
            for j in range(sides):
                k=(j+1)%sides
                u0=uv_range[0]+(uv_range[1]-uv_range[0])*(math.cos(j*2*PI/sides)+1)/2
                u1=uv_range[0]+(uv_range[1]-uv_range[0])*(math.cos((j+1)*2*PI/sides)+1)/2
                vertices=[rows[i][j],rows[i][k],rows[i+1][k],rows[i+1][j]]
                vs=[i/last,i/last,(i+1)/last,(i+1)/last] if uv_height is None else [
                    hair_v(p[1],uv_height,highlight_height) for p in vertices]
                quad(part,vertices,material,[(u0,vs[0]),(u1,vs[1]),(u1,vs[2]),(u0,vs[3])])

def skirt(part, top, bottom, rx, rz, material, pleats=24, flare_start=.42, hem_wave=.12, drape=False):
    samples=8 if drape or isinstance(material,tuple) else 4
    sides=pleats*samples; rings=12
    start=len(part['quads'])
    def point(t,base):
        # Periodic changes in panel width and depth keep the seam closed and
        # let the fabric gather unevenly while retaining its outer envelope.
        a=base+(.045*math.sin(3*base+.4)*t*t if drape else 0)
        fold=math.cos(base*pleats)
        depth=.78+.16*math.sin(3*base+.4)+.06*math.sin(7*base-.2) if drape else 1
        ripple=1+SKIRT_PLEAT_DEPTH*depth*fold*(0.3+0.7*t)
        flare=(flare_start+(1-flare_start)*math.sin(t*PI/2)**1.3)*ripple
        hem=hem_wave*fold*(t*t*(.75+.25*math.sin(2*base+.2)) if drape else t)
        return (rx*flare*math.sin(a),top+(bottom-top)*t+hem,rz*flare*math.cos(a))
    def p(i,j):
        return point(i/rings,j*2*PI/sides)
    for i in range(rings):
        for j in range(sides):
            repeat=sides//4 if material==NAVY_EMBROIDERY else sides
            u0=(j%repeat)/repeat;u1=(j%repeat+1)/repeat
            quad(part,[p(i,j),p(i,j+1),p(i+1,j+1),p(i+1,j)],material if not isinstance(material,tuple) else material[(j%samples)*len(material)//samples],[(u0,i/rings),(u1,i/rings),(u1,(i+1)/rings),(u0,(i+1)/rings)])
    def surface(t,angle):
        row=min(rings-1,int(t*rings));f=t*rings-row
        column=(angle%(2*PI))*sides/(2*PI);j=int(column);u=column-j
        q=part['quads'][start+row*sides+j]
        weights=(1-u,u-f,f,0) if f<=u else (1-f,0,u,f-u)
        return tuple(sum(q[k][c]*weights[k] for k in range(4)) for c in range(3))
    return surface if drape else point

def torso(part, profiles, material, shoulder_drop=0):
    rows=[]
    for i in range(25):
        y,rx,rz=catmull(profiles,i/24*(len(profiles)-1))
        rows.append([(rx*math.sin(j*2*PI/40),y+shoulder_drop*math.sin(j*2*PI/40)**2*(1-i/24)**2,
                      rz*math.cos(j*2*PI/40)) for j in range(40)])
    for i in range(24):
        for j in range(40):
            k=(j+1)%40
            quad(part,[rows[i][j],rows[i][k],rows[i+1][k],rows[i+1][j]],material,[(j/40,i/24),((j+1)/40,i/24),((j+1)/40,(i+1)/24),(j/40,(i+1)/24)])

def bodice(part, profiles, material):
    rows=[]
    for i in range(25):
        y,rx,rz=catmull(profiles,i/24*(len(profiles)-1))
        rows.append([((rx+.1)*math.sin(a),y,-(rz+.18)*math.cos(a)) for a in [(j/24-.5)*1.6 for j in range(25)]])
    for i in range(24):
        for j in range(24): quad(part,[rows[i][j],rows[i+1][j],rows[i+1][j+1],rows[i][j+1]],material,[(j/24,i/24),(j/24,(i+1)/24),((j+1)/24,(i+1)/24),((j+1)/24,i/24)])

def lace(part, y, rx, rz, material=WHITE, scallops=36, depth=1):
    def p(i,j):
        a=j*2*PI/(scallops*8); t=i/4
        edge=depth*(0.35+0.22*math.cos(a*scallops))
        return ((rx+t*0.3*depth)*math.sin(a),y+t*edge,(rz+t*0.3*depth)*math.cos(a))
    for i in range(4):
        for j in range(scallops*8):
            quad(part,[p(i,j),p(i,j+1),p(i+1,j+1),p(i+1,j)],material,
                 [(j/(scallops*8),.65+.32*i/4),((j+1)/(scallops*8),.65+.32*i/4),
                  ((j+1)/(scallops*8),.65+.32*(i+1)/4),(j/(scallops*8),.65+.32*(i+1)/4)])

def bow(part,center,size,material,tails=0,profiles=None,scale=(1,1,1),wing_scale=(1,1,1)):
    start=len(part['quads'])
    for sign in [-1,1]:
        def p(i,j):
            t=i/20;a=j*2*PI/16
            width=size*(.16+.72*math.sin(t*PI*.66))
            gather=math.sin(t*PI)
            return (center[0]+sign*size*(1.65*t+.10*math.cos(a*2)*t),
                    center[1]+width*math.sin(a)-size*.12*t,
                    center[2]+size*(.09+.26*gather)*math.cos(a)+size*.08*math.cos(a*3)*gather)
        for i in range(20):
            for j in range(16):
                points=[p(i,j),p(i,j+1),p(i+1,j+1),p(i+1,j)]
                coords=[(i/20,j/16),(i/20,(j+1)/16),((i+1)/20,(j+1)/16),((i+1)/20,j/16)]
                # Mirroring reverses winding; both wings must face outwards.
                if sign>0: points.reverse();coords.reverse()
                quad(part,points,material,coords)
        tip=(center[0]+sign*size*1.65,center[1]-size*.12,center[2])
        for j in range(16):
            points=[p(20,j),p(20,j+1),tip,tip]
            if sign>0: points=[p(20,j+1),p(20,j),tip,tip]
            quad(part,points,material)
    ellipsoid(part,center,(size*0.3,size*0.4,size*0.35),material,8,12)
    # Flatten tied fabric wings independently of the ribbons that follow the
    # blouse. Scaling projected ribbon depth would bury them inside the chest.
    if wing_scale!=(1,1,1):
        for q in part['quads'][start:]:
            for v in q:
                for c in range(3): v[c]=round(center[c]+(v[c]-center[c])*wing_scale[c],5)
    if tails:
        # Thin fabric, with a curved fold and a split cut at the free edge.
        # The signed value selects the outward side of the front/back outfit.
        for sign in [-1,1]:
            def ribbon(i,j,back=False):
                t=i/24;u=j/8*2-1
                x=sign*(.18+.86*t)+u*(.20+.30*math.sin(t*PI/2))
                y=.20+2.55*t-.28*(1-abs(u))*t**12
                z=-tails*(.08+(.10 if tails<0 else .27)*t)+.08*math.sin(u*PI)*math.sin(t*PI)
                x=center[0]+size*x;y=center[1]+size*y;z=center[2]+size*z
                if profiles is not None:
                    _,rx,rz=profile_at_y(profiles,y)
                    z=-(rz+.18)*math.sqrt(max(0,1-(x/(rx+.10))**2))-.06-.08*math.sin(t*PI)*(1-u*u)
                return (x,y,z+(.025*size if back else 0))
            for i in range(24):
                for j in range(8):
                    points=[ribbon(i,j),ribbon(i+1,j),ribbon(i+1,j+1),ribbon(i,j+1)]
                    coords=[(j/8,i/24),(j/8,(i+1)/24),((j+1)/8,(i+1)/24),((j+1)/8,i/24)]
                    quad(part,points,material,coords)
                    quad(part,[ribbon(i,j,True),ribbon(i,j+1,True),ribbon(i+1,j+1,True),ribbon(i+1,j,True)],material,[coords[k] for k in [0,3,2,1]])
            for edge in [0,8]:
                for i in range(24):
                    points=[ribbon(i,edge),ribbon(i,edge,True),ribbon(i+1,edge,True),ribbon(i+1,edge)]
                    if edge==8: points.reverse()
                    quad(part,points,material)
            for j in range(8):
                quad(part,[ribbon(24,j),ribbon(24,j,True),ribbon(24,j+1,True),ribbon(24,j+1)],material)
                quad(part,[ribbon(0,j),ribbon(0,j+1),ribbon(0,j+1,True),ribbon(0,j,True)],material)
    if scale!=(1,1,1):
        for q in part['quads'][start:]:
            for v in q:
                for c in range(3): v[c]=round(center[c]+(v[c]-center[c])*scale[c],5)

def ear(part,side,radius_x):
    # A shallow concha inside a rounded helix; the back sits in the head.
    # Keep the small lobe tapered so the ear fits behind the cheek locks.
    rows=12;sides=40
    def point(i,j,back=False):
        r=i/rows;a=j*2*PI/sides
        y=-.40+.65*r*math.sin(a)
        z=-.45+.24*r*math.cos(a)*(1-.18*max(0,math.sin(a)))+.06*r*math.sin(a)
        base=radius_x*.95-.20*max(0,y+.40)
        ridge=.20*math.exp(-((r-.76)/.16)**2)
        x=base-.045 if back else base+.035+ridge
        return (side*x,y,z)
    def coords(i,j):
        r=i/rows;a=j*2*PI/sides
        return (.44+.08*r*math.cos(a),.43+.10*r*math.sin(a))
    for i in range(rows):
        for j in range(sides):
            corners=[(i,j),(i,(j+1)%sides),(i+1,(j+1)%sides),(i+1,j)]
            for back in (False,True):
                order=list(reversed(corners)) if (side<0)!=back else corners
                quad(part,[point(a,b,back) for a,b in order],SKIN,[coords(a,b) for a,b in order])
    for j in range(sides):
        k=(j+1)%sides
        points=[point(rows,j),point(rows,k),point(rows,k,True),point(rows,j,True)]
        tex=[coords(rows,j),coords(rows,k),coords(rows,k),coords(rows,j)]
        if side<0: points.reverse();tex.reverse()
        quad(part,points,SKIN,tex)


def head_fin(part,side):
    # One closed, thin curved surface carries both the blue fin and its
    # white lower edge, so the lining cannot float above or cut through it.
    rows=40;columns=12
    outline=[(3.25,-2.35,-1.15),(4.1,-2.12,-.95),
             (5.0,-1.80,-1.12),(6.10,-2.03,-2.03)]
    def point(i,j,back=False):
        t=i/rows;u=j/columns
        x,top,bottom=outline[-1] if i==rows else catmull(outline,t*3)
        # Three soft scallops belong to the white lining, not the blue fin.
        # Fade them at both attachments and keep the shell continuously sewn.
        scallop=.16*math.sin(PI*t)**.8*abs(math.sin(3*PI*t))**.7
        depth=(1-t)**.35
        # The white lip turns gently forward instead of facing down into
        # shadow; it is still part of the same shell as the blue surface.
        z=(.015+.10*math.sin(PI*u)) if back else (
            -.015-.16*math.sin(PI*min(u,.75))-.02*max(0,(u-.75)/.25))
        # Sweep the tip behind the temple so the fin has a readable side
        # silhouette, instead of collapsing into a dark edge in profile.
        sweep=-.25+1.75*t**1.2
        return (side*x,top+(bottom-top)*u+scallop*max(0,(u-.75)/.25),sweep+z*depth)
    for i in range(rows):
        for j in range(columns):
            corners=[(i,j),(i+1,j),(i+1,j+1),(i,j+1)]
            material=PLAIN_WHITE if j>=9 else TAIL
            for back in (False,True):
                order=list(reversed(corners)) if (side>0)!=back else corners
                quad(part,[point(a,b,back) for a,b in order],material,
                     [(.43+.09*a/rows,b/columns) for a,b in order])
    perimeter=([(i,0) for i in range(rows+1)]+[(rows,j) for j in range(1,columns+1)]+
               [(i,columns) for i in range(rows-1,-1,-1)]+[(0,j) for j in range(columns-1,0,-1)])
    if side>0: perimeter.reverse()
    for a,b in zip(perimeter,perimeter[1:]+perimeter[:1]):
        material=PLAIN_WHITE if min(a[1],b[1])>=9 else TAIL
        quad(part,[point(*b),point(*a),point(*a,True),point(*b,True)],material)


def hair_bow(part,center,size):
    # Thin gathered fabric: two shaped sheets and a sewn perimeter, rather
    # than a round tube closed with a flat disc at either end.
    rows=24;columns=12
    for sign in (-1,1):
        def p(i,j,back=False):
            t=i/rows;u=j/columns*2-1;gather=math.sin(PI*t)
            return (center[0]+sign*size*(1.50*t+.10*(1-u*u)*t),
                    center[1]+size*((.12+.67*math.sin(t*PI/2))*u-.22*t),
                    center[2]-size*(.07+.18*gather+.055*math.cos(u*PI*2)*gather)+(.04*size if back else 0))
        for i in range(rows):
            for j in range(columns):
                corners=[(i,j),(i+1,j),(i+1,j+1),(i,j+1)]
                for back in (False,True):
                    order=list(reversed(corners)) if (sign>0)!=back else corners
                    quad(part,[p(a,b,back) for a,b in order],BOW_CLOTH,[(a/rows,b/columns) for a,b in order])
        perimeter=([(i,0) for i in range(rows+1)]+[(rows,j) for j in range(1,columns+1)]+
                   [(i,columns) for i in range(rows-1,-1,-1)]+[(0,j) for j in range(columns-1,0,-1)])
        if sign>0: perimeter.reverse()
        for a,b in zip(perimeter,perimeter[1:]+perimeter[:1]):
            quad(part,[p(*b),p(*a),p(*a,True),p(*b,True)],BOW_CLOTH)
    ellipsoid(part,(center[0],center[1],center[2]-.10*size),
              (size*.23,size*.30,size*.17),BOW_CLOTH,10,16)

def emblem(part, center, size):
    start=len(part['quads'])
    ellipsoid(part,center,(size,size*0.65,0.035),EMBLEM_BLUE,8,24)
    tube(part,[center,(center[0]+size,center[1]-size*0.2,center[2]),(center[0]+size*1.3,center[1]-size,center[2])],[size*0.22,size*0.15,0.01],EMBLEM_BLUE,12,8)
    for s in [-1,1]:
        ellipsoid(part,(center[0]+size*(1.3+s*.13),center[1]-size*1.04,center[2]),
                  (size*.18,size*.22,.028),EMBLEM_BLUE,8,16)
    ellipsoid(part,(center[0]-size*0.4,center[1]+size*0.15,center[2]-0.04),(size*0.06,size*0.06,0.015),WHITE,6,8)
    tube(part,[(center[0]-size*.64,center[1]+size*.30,center[2]-.045),
               (center[0]-size*.12,center[1]+size*.48,center[2]-.045),
               (center[0]+size*.40,center[1]+size*.45,center[2]-.045)],
         [size*.025]*3,WHITE,16,6)
    for i in range(3): ellipsoid(part,(center[0]-size*0.2+i*size*0.3,center[1]-size*(1.1+0.2*(i%2)),center[2]),(size*0.1,size*0.16,0.02),EMBLEM_BLUE,6,8)
    # Printed motifs follow the cloth with only a shallow stitched relief.
    for q in part['quads'][start:]:
        for v in q: v[2]=round(center[2]+(v[2]-center[2])*.18,5)

def jewel(part,center,radius,material):
    rings=[(.64,-.9),(.98,-.2),(.95,.6)]
    def point(ring,j):
        scale,depth=rings[ring];a=j*2*PI/12
        return (center[0]+radius[0]*scale*math.sin(a),center[1]+radius[1]*scale*math.cos(a),center[2]+radius[2]*depth)
    def add(points):
        quad(part,points,material,[(.5+(x-center[0])/(2*radius[0]),.5+(y-center[1])/(2*radius[1])) for x,y,z in points])
    for ring in range(2):
        for j in range(12):
            add([point(ring,j+1),point(ring,j),point(ring+1,j),point(ring+1,j+1)])
    for ring,depth in [(0,-.9),(2,.6)]:
        cap=(center[0],center[1],center[2]+radius[2]*depth)
        for j in range(12):
            points=[cap,point(ring,j),point(ring,j+1),cap]
            if ring==2: points.reverse()
            add(points)

def hair_band_offsets(quads,radius,depth):
    # Fit the band to hair in its own depth slab. Keep the center slightly
    # embedded so the outward half is visible without a floating gap.
    offsets=[0.]*49
    rx=radius[0]+.30;ry=radius[1]
    for q in quads:
        if min(v[2] for v in q)>depth+.08 or max(v[2] for v in q)<-depth-.08: continue
        for x,y,z,*_ in q:
            if y>-1.3: continue
            a=math.atan2(max(0,-(y+1.4))/ry,x/rx)
            scale=math.hypot(x/rx,(y+1.4)/ry)
            delta=max(0,(scale-1)*math.hypot(rx*math.cos(a),ry*math.sin(a))-.04)
            slot=round(a/PI*48)
            for i in range(max(0,slot-1),min(49,slot+2)):
                offsets[i]=max(offsets[i],delta)
    for _ in range(8):
        offsets=[offsets[0],*(max(offsets[i],.25*offsets[i-1]+.5*offsets[i]+.25*offsets[i+1]) for i in range(1,48)),offsets[-1]]
    return [(value,) for value in offsets]


def character(baby):
    root=node('root'); head=child(root,'head',(0,14.5 if baby else 1,0));child(head,'hat')
    center=(0,-1.2,0); radius=(3.55,3.6,2.35) if baby else (3.25,3.65,2.35)
    face(child(head,'head_back'),center,radius,baby,back=True)
    front=node('face');face(front,center,radius,baby)
    head['children'].append(front)
    scalp_radius=tuple(r*s for r,s in zip(radius,(1.028,1.03,1.028)))
    length=7.7 if baby else 14.4
    for back_half in [False,True]:
        scalp_start=len(head['quads'])
        face(head,center,scalp_radius,baby,back_half,(-PI/2,.65 if back_half else -.16),ROOT_HAIR)
        if not back_half: front_scalp=head['quads'][scalp_start:]
        else:
            # The rear scalp is visible between the locks. Share their
            # height-based painted gradient instead of exposing dark patches.
            for q in head['quads'][scalp_start:]:
                for v in q:
                    u=max(.40,min(.57,.485+.085*v[0]/radius[0]))
                    v[3:5]=[round(t,5) for t in uv(HAIR,u,hair_v(v[1],(-4.77,length+.4),-2.8))]
    scalp_triangles=[]
    for q in front_scalp:
        for a,b,c in ((q[0],q[1],q[2]),(q[0],q[2],q[3])):
            det=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
            if abs(det)>1e-10:
                scalp_triangles.append((min(a[0],b[0],c[0]),max(a[0],b[0],c[0]),
                                        min(a[1],b[1],c[1]),max(a[1],b[1],c[1]),a,b,c,det))
    def scalp_front_z(x,y,required=True):
        # Sample the actual scalp triangles so roots follow the same surface
        # as the rendered head, including its sculpted forehead.
        for left,right,top,bottom,a,b,c,det in scalp_triangles:
            if x<left-1e-6 or x>right+1e-6 or y<top-1e-6 or y>bottom+1e-6: continue
            u=((b[1]-c[1])*(x-c[0])+(c[0]-b[0])*(y-c[1]))/det
            v=((c[1]-a[1])*(x-c[0])+(a[0]-c[0])*(y-c[1]))/det
            if min(u,v,1-u-v)>=-1e-6:
                return u*a[2]+v*b[2]+(1-u-v)*c[2]
        if required: raise ValueError(f'Hair root outside scalp: {x}, {y}')
        return None
    # Three ordered depth layers share a flowing curve, rather than 29 unrelated
    # phases crossing each other. Two broad S turns end in open, tapered curls.
    index=0
    for layer,count in [(0,9),(1,10),(2,10)]:
        for k in range(count):
            if layer==2:
                side=-1 if k<5 else 1
                a=PI*(.035+.065*(k%5))
                if side<0:a=PI-a
            else:a=PI*(k+.5)/count
            x=math.cos(a)*radius[0];z=math.sin(a)*radius[2]
            s=1 if x>=0 else -1
            spread=(1.7 if baby else 2.7)+layer*(.2 if baby else .35)
            stagger=[.035,-.055,.010,-.025,.050,-.045,.020,-.010,.045,-.035][k]
            end=length*(.95-layer*.11+stagger*2)
            if not baby and layer==1:
                # Mid-length overlaps break up the crown-to-waist curtain;
                # the inner and silhouette layers keep the full hair length.
                end=length*(.57+stagger*2)
            root_y=-1.65+.32*math.sin(k*1.7+layer*.6)
            root_scale=1.04*math.sqrt(1-((root_y+1.2)/(radius[1]*1.03))**2)
            points=[(x*.10,-4.77,z*.10+.04),(x*.72,-3.85,z*.72+.08),
                    (x*root_scale,root_y,z*root_scale+.07)]
            side_weight=abs(math.cos(a))**.65
            phase=layer*.45+[-.52,.24,.75,-.31,.58,.06,-.72,.43,-.18,.91][k]
            sculpted=not baby and ((layer==0 and k in (1,3,5,7)) or (layer==1 and k in (0,2,4,5,7,9)))
            for t in [.17,.38,.60,.78]:
                flow=spread*math.sin(t*PI*.78)+(0.72 if baby else 1.05)*math.sin(t*PI*4+.2+phase)
                central=(.45 if baby else 1.10)*(math.sin(t*PI*3+phase)-math.sin(phase))*math.sin(t*PI/2)
                lateral=s*(flow*side_weight+central*(1-side_weight))
                if layer==1 and k in (4,5):
                    lateral+=s*(.65 if baby else .60)*math.sin(t*PI)**2
                depth=-.32*math.sin(t*PI) if layer==1 and k in (2,7) else 0
                if sculpted:
                    # Offset selected broad locks in both visible projections:
                    # alternating S bends separate shoulder and waist layers.
                    direction=1 if (k//2+layer)%2==0 else -1
                    wave=math.sin(t*PI*2+.2)*math.sin(t*PI)
                    lateral+=s*(.70 if layer==0 else .60)*direction*wave
                    depth+=.22*math.sin(t*PI)+.42*direction*wave
                if layer==2 and k%5==2 and t==.78: depth+=.20
                if not baby and layer==2 and k%5==0:
                    # The outer temple pair controls the side silhouette.
                    # Bend its shoulder and waist in opposite depth directions
                    # so the inner S curves are not hidden by a straight sheet.
                    depth+={.17:.22,.38:-.05,.60:-.15,.78:-.15}[t]
                if layer==2 and k%5==4:
                    depth-=.05 if t==.78 else (.40 if t==.60 else (.20 if t==.38 else 0))
                    lateral-=s*(.40 if t>=.60 else (.20 if t==.38 else 0))
                y=end*(.86 if t==.78 and layer==2 and k%5==4 else t)
                if baby:
                    rear=.95+1.35*math.sin(t*PI)+layer*.60
                    # Bring the two nape falls together while retaining
                    # their depth order and the lower curls.
                    if t==.17:
                        if layer==1 and k==4: lateral+=.25
                        elif layer==0 and k==3: lateral-=.22
                else:
                    # Keep the first fall close to the nape. Alternating
                    # shoulder/waist depths make an S profile instead of
                    # carrying every layer into the same rearward fan.
                    lateral*=min(1,t/.38)**2
                    rear={.17:.25+layer*.18,.38:1.15+layer*.30,
                          .60:.50+layer*.25,.78:.90+layer*.35}[t]
                    if layer==1 and k in (0,9) and t==.17: rear+=.10
                    if layer==1 and 3<=k<=6: lateral*=.35
                points.append((x+lateral,y,z+rear+depth))
            if (layer==2 and k%5==0) or (not baby and layer==1 and k==0):
                # Keep the broad temple lock flowing down: a sharp outward
                # elbow flips its ribbon section into a flat horizontal shelf.
                px,py,pz=points[5]
                points[5]=((points[4][0]+points[6][0])*.5+s*.25,py,pz)
            tip=x+s*spread*.72*side_weight
            if not baby and layer==1 and 3<=k<=6:
                tip=x+s*spread*.25*side_weight
            curl=(.65 if baby else 1.15)*[1.00,.67,.86,1.10,.73][k%5]
            if not baby and layer<2: curl*=.70
            if layer==2 and k%5 in (1,3): curl*=.72 if baby else .40
            turn=s*(-1 if (k+layer)%3==1 else 1)
            tip_z=z+.85+layer*.60
            if layer==2 and k%5==2: tip_z+=.55
            # Most ends continue the wave into a pointed, open hook. Only a few
            # accent strands turn upwards; repeated closed curls read as rings.
            upturn=(k+layer)%4==0
            side_curl=layer==2 and k%5==4
            full_curl=not baby and ((layer==2 and k%5==2) or (layer==0 and k in (2,4,7)))
            last_wave_x=points[-1][0]
            points.extend([((last_wave_x+s*.15) if side_curl else tip+turn*curl*.45,end-curl*.65,tip_z+.2),
                           ((last_wave_x*.65+(tip+turn*curl*.20)*.35) if side_curl else tip+turn*curl*.20,end,tip_z+(.35*curl if side_curl else 0)),
                           (tip-turn*curl*.40,end+curl*(.35 if side_curl else .15),tip_z+(.75*curl if side_curl else -.15)),
                           (tip-turn*curl*.78,end+curl*(.65 if side_curl else (-.30 if upturn else .18)),tip_z+(curl if side_curl else -.28))])
            if not baby and layer<2:
                # A short, open finishing bend keeps a full lock before the tip
                # rather than a long horizontal wire or an upturned loop.
                points[7:]=[(tip+turn*curl*.28,end-curl*.45,tip_z+.15),
                            (tip+turn*curl*.15,end,tip_z+.15),
                            (tip-turn*curl*.20,end+curl*.22,tip_z+.25),
                            (tip-turn*curl*.42,end+curl*.32,tip_z+.45)]
            if not baby and layer==2 and k%5 in (1,3):
                # Let the narrow accents finish in an open downward hook.
                # The former short reversing segment pinched the swept width.
                points[7:]=[(tip+turn*curl*.35,end-.75,tip_z+.10),
                            (tip+turn*curl*.15,end,tip_z+.15),
                            (tip-turn*curl*.35,end+.25,tip_z+.25),
                            (tip-turn*curl*.60,end+.40,tip_z+.45)]
            if full_curl:
                # Silhouette and selected central locks keep their volume
                # through an open C bend, with staggered ends rather than
                # a row of straight tapered ribbons down the back.
                curl*=1.35
                points[7:]=[(tip+s*curl*1.15,end-curl*.75,tip_z+.25),
                            (tip+s*curl*1.15,end+curl*.10,tip_z+.40),
                            (tip+s*curl*.35,end+curl*.65,tip_z+.65),
                            (tip-s*curl*.15,end+curl*.45,tip_z+.80)]
            base_width=(.95 if k%5 in (0,2,4) else (.35 if baby else .50)) if layer==2 else (.82,.55)[layer]
            width=base_width*(.87 if baby else 1)*[1.10,.58,.93,.66,1.04][k%5]
            lock_twist=0
            if not baby:
                if layer==2 and k%5==0: lock_twist=s*.72
                # Keep the close-fitting shoulder locks and the left lower
                # underlay edge flat; the freely hanging locks can turn.
                elif (layer==0 and k in (2,4)) or (layer==1 and 0<k<9):
                    lock_twist=.38*(-1 if k%2 else 1)
            widths=[.015,width*.30,width*.72,width*.85,width,width,width*.75,
                       width*(.80 if full_curl else (.40 if side_curl else (.45 if upturn else .65))),
                       width*(.65 if full_curl else (.28 if side_curl else (.34 if upturn else .48))),
                       width*(.42 if full_curl else (.30 if not baby and layer<2 else (.14 if side_curl else .24))),.008]
            if baby and layer==1 and k==4:
                widths[3]+=.18
            if not baby:
                # Keep fitted roots intact, but carry broad wave volume down
                # the hanging lengths instead of narrowing into thin cords.
                fullness=1.55 if layer==2 and k%5 in (1,3) else 1.30
                for j in range(4,10):
                    widths[j]*=fullness
            if layer>0:
                # Keep one crown layer; the overlapping lengths begin
                # behind the ears instead of inflating the crown silhouette.
                points=points[2:];widths=widths[2:]
                widths[0]=.015
                if not baby and layer==2:
                    px,py,pz=points[0]
                    inset=.85 if k==8 else .92
                    points[0]=(px*inset,py,pz*inset)
            lock=child(head,'hair_'+str(index))
            path_offset=None
            if not baby and layer==2:
                # Build the clearance into the centerline before transporting
                # sections; moving finished vertices sheared the narrow roots.
                clearance=[.80,.60,.65,.40,.50][k%5]+(.08 if k==5 else 0)
                def path_offset(p):
                    x,y,z=p
                    if k==7:
                        rise=max(0,min(1,(y+2)/1.5));rise=rise*rise*(3-2*rise)
                        fall=max(0,min(1,(y-2)/2));fall=fall*fall*(3-2*fall)
                        x-=.24*rise*(1-fall)
                    fade=max(0,min(1,(y-2)/4));fade=fade*fade*(3-2*fade)
                    attachment=max(0,min(1,(y-points[0][1])/3))
                    attachment=attachment*attachment*(3-2*attachment)
                    return x,y,z+clearance*attachment*(1-fade)
            # Keep the two side curls as complete locks: splitting their
            # already tapered bends made parallel wire-like loops.
            hair_lock(lock,points,widths,
                      thickness=.18 if layer==2 else .30,uv_range=(.40,.57),roll=(a-PI/2)*(.45 if layer==2 else 1),bend_limit=.80,
                      strands=2 if (not full_curl or layer==0) and layer in (0,2) and k%5 in (0,2,4)
                      and not (not baby and layer==2 and k%5==4) else 1,
                      split_ratio=.5 if baby else .60,uv_height=(-4.77,length+.4),
                      highlight_height=-2.8,
                      twist=lock_twist,
                      scalp=(center,scalp_radius),embed_root=layer==0 or (not baby and layer==2),
                      path_offset=path_offset)
            if not baby and layer==1 and k in (1,9):
                # Keep the short underlayer inside the split outer lock,
                # including its idle sway, without moving its attached root.
                for q in lock['quads']:
                    for v in q:
                        rise=max(0,min(1,v[1]));rise=rise*rise*(3-2*rise)
                        fall=max(0,min(1,(v[1]-3)/2));fall=fall*fall*(3-2*fall)
                        v[0]=round(v[0]+(-.40 if k==1 else .85)*rise*(1-fall),5)
            index+=1
    # A left-of-centre part feeds three broad, swept locks and six slimmer
    # temple/overlap pieces. Side pieces curve around the head, not a flat visor.
    fringe=[(-.96,-1.03,-.65,.34),(-.76,-.85,-1.28,.46),
            (-.60,-.48,-1.72,.78),(-.37,-.10,-1.15,.70),
            (-.12,.18,-1.86,.43),(.15,.43,-1.38,.75),
            (.45,.70,-1.78,.52),(.72,.90,-1.02,.40),(.94,1.02,-.55,.30)]
    for i,(start,tip,end,width) in enumerate(fringe):
        if not baby:
            # Keep the swept fringe near the brow instead of ending as long
            # spears between the eyes and across the upper eyelid.
            if i==3: end=-1.45
            elif i==5: end=-1.56
        x=start*radius[0];tip_x=tip*radius[0]
        root_x=(-.13+start*.43)*radius[0]
        bend=x+(tip_x-x)*.68
        mid_z=-2.48*math.sqrt(max(.22,1-(bend/radius[0]*.70)**2))
        tip_z=-2.55*math.sqrt(max(.18,1-(tip*.83)**2))
        points=[(root_x,-4.58+abs(start)*.15,-1.0),
                (x*.72,-3.58,-2.25),(bend,-2.30,mid_z),(tip_x,end,tip_z)]
        widths=[.085,width*.90,width*.46,.006]
        if not baby:
            if i in (2,3,5):
                # Three side-part locks carry the silhouette, with a long
                # fine taper rather than a bulb ending in a broad hard point.
                points[1]=(x*.72,-3.58,-2.28)
                points[2]=(bend,-2.50,mid_z-.04)
                points.insert(3,(bend*.35+tip_x*.65,max(end-.65,points[2][1]+.40),
                                 mid_z*.35+tip_z*.65-.04))
                widths=[.008,width*.90,width*.68,width*.25,.006]
            else:
                # Narrower underlapping pieces fill the gaps between the main
                # locks, rather than making nine equally prominent petals.
                points[1]=(x*.72,-3.58,-2.22)
                points[2]=(bend,-2.30,mid_z+.04)
                points[3]=(tip_x,end-.10,tip_z+.015)
                widths=[.05,width*.65,width*.40,.006]
                if i in (4,6):
                    # Begin narrowing sooner; a broad shoulder just above
                    # these short tips read as a blunt cut from three quarters.
                    points[2]=(bend,-2.65,mid_z+.04)
                if i==4:
                    # Broaden the short central underlock at the forehead;
                    # its unchanged tip leaves both eyes visible.
                    widths[1]=width*.85
                    widths[2]=width*.60
        # Follow the scalp in both age variants; the complete rings below
        # then preserve a small clearance across the full strand width.
        if not baby and i in (2,3,5):
            # Stagger the three main roots around the side part instead of
            # fanning every lock out from one narrow crown region.
            dx,dy={2:(-.20,.18),3:(-.15,.02),5:(.30,.18)}[i]
            root_x+=dx
            points[0]=(root_x,points[0][1]+dy,points[0][2])
        root_y=max(points[0][1],center[1]-scalp_radius[1]*
                   math.sqrt(max(0,1-(root_x/scalp_radius[0])**2))+.18)
        points[0]=(root_x,root_y,scalp_front_z(root_x,root_y)+.025)
        px,py,_=points[1]
        points[1]=(px,py,scalp_front_z(px,py)-.035)
        if not baby and i in (2,3,5):
            turn_x=(root_x+px)*.5+(-.08 if i==2 else -.04 if i==3 else .10)
            turn_y=root_y*.52+py*.48
            points.insert(1,(turn_x,turn_y,scalp_front_z(turn_x,turn_y)-.025))
            widths.insert(1,width*.55)
            widths=[w*1.12 if j not in (0,len(widths)-1) else w for j,w in enumerate(widths)]
        elif not baby and i in (1,4,6):
            # Fine brow tips emerge underneath the three broad swept locks,
            # rather than each drawing a separate leaf up to the crown.
            a,b=points[1:3]
            t=(-3.3-a[1])/(b[1]-a[1])
            x=a[0]+(b[0]-a[0])*t
            if i==6: x-=.35
            points=[(x,-3.3,scalp_front_z(x,-3.3)+.025),*points[2:]]
            widths=[.008,*widths[2:]]
        fringe_start=len(head['quads'])
        hair_lock(head,points,widths,ROOT_HAIR,32,.085,uv_range=(.38,.55),
                  continuous_taper=not baby and i in (2,3,5))
        fringe_quads=head['quads'][fringe_start:]
        shifts=[]
        for row in range(33):
            ring=[v for q in fringe_quads[min(row,31)*12:min(row,31)*12+12]
                  for v in (q[:2] if row<32 else q[2:])]
            shift=0
            for v in ring:
                scalp_z=scalp_front_z(v[0],v[1],required=False)
                if scalp_z is not None: shift=max(shift,v[2]-scalp_z+.012)
            shifts.append(shift)
        if baby and i in (0,8):
            # The narrow temple strands leave the curved scalp abruptly.
            # Spread that clearance transition without moving any ring back
            # inside the scalp or changing the attachment and pointed tip.
            for _ in range(8):
                shifts=[shifts[0],*(max(shifts[j],.25*shifts[j-1]+.5*shifts[j]+.25*shifts[j+1])
                                    for j in range(1,32)),shifts[-1]]
        # Move each complete ring by one offset: keeping its thickness
        # avoids coincident front/back faces while clearing the scalp.
        for qi,q in enumerate(fringe_quads):
            for vi,v in enumerate(q):
                row=qi//12+(vi>=2)
                # The swept main locks sit in front of their shorter fillers.
                # Move complete rings, retaining both attachment and tip,
                # so adjacent sheets do not exchange depth at the forehead.
                layer=(.08 if i==2 else .17)*math.sin(PI*row/32)**.5 if not baby and i in (2,3,5) else 0
                v[2]=round(v[2]-shifts[row]-layer,5)
        # Both ages have the reference's blue forehead highlight, rather
        # than making every fringe tip cyan like the ends of the long hair.
        for q in fringe_quads:
            for v in q:
                shine=.08+.57*math.exp(-((v[1]+3.1)/.60)**2)
                v[4]=round(uv(ROOT_HAIR,0,shine)[1],5)
    # Temple locks bridge the fringe to the rear hair. Their broad surface
    # faces sideways, covering the scalp below the cap without hiding the eyes.
    for s in [-1,1]:
        side=s*radius[0]/3.25
        # A short ear-back layer covers the gap between the face-framing
        # locks and the rear hair, following the taper of the skull.
        # The rear cheek layer ends above the jaw; a long inward-hooking
        # blade previously hid the ear and joined the chin to the back hair.
        hair_lock(head,[(side*2.80,-3.05,.20),(side*3.30,-1.35,.18),
                        (side*3.08,.25,.22) if baby else (side*3.08,-.05,.30),
                        (side*2.90,.85,.65) if baby else (side*2.87,.65,.55)],
                  [.04,.58,.44,.006] if baby else [.04,.62,.40,.006],ROOT_HAIR,40,.085,
                  uv_range=(.38,.55),roll=s*PI/2,uv_height=(-4.8,7.0),scalp=(center,scalp_radius))
        temple=[(side*2.35,-4.0,-.30),(side*3.12,-2.1,-.60),
                (side*3.12,-.25,-.80),(side*2.60,1.55,-1.05)]
        temple_widths=[.06,.60,.43,.006] if baby else [.06,.48,.32,.006]
        wisps=[(side*2.75,-3.1,-1.0),(side*3.20,-1.7,-1.18),
               (side*3.16,-.15 if baby else -.45,-1.25),(side*2.95,1.0,-1.28)]
        wisp_widths=[.05,.32,.23,.006]
        # Cheek-length locks turn gently outwards and forwards. Raise adult
        # tips above the jaw and keep each wisp's descent free of sharp hooks.
        lower=-.20 if baby else -.35
        temple[-1:]=[(side*(2.92 if baby else 3.07),.45+lower,-.96),(side*3.12,.90+lower,-1.32)]
        temple_widths[-1:]=[.18,.006]
        wisps[-1:]=[(side*(3.05 if baby else 3.17),.10+lower,-1.30),(side*3.23,.42+lower,-1.44)]
        wisp_widths[-1:]=[.11,.006]
        if not baby:
            # Closely packed tip controls stalled the uniform spline, forcing
            # curvature clearance to pinch and then widen the lock again.
            del temple[-2],temple_widths[-2],wisps[-2],wisp_widths[-2]
        hair_lock(head,temple,temple_widths,ROOT_HAIR,40,.10,
                  uv_range=(.38,.55),roll=s*PI/2,uv_height=(-4.8,7.0),
                  bend_limit=.8,scalp=(center,scalp_radius),continuous_taper=True)
        hair_lock(head,wisps,wisp_widths,ROOT_HAIR,32,.12,
                  uv_range=(.42,.54),roll=s*PI*.33,uv_height=(-4.8,7.0),
                  bend_limit=.8,scalp=(center,scalp_radius),continuous_taper=True)
    ahoge=[(.7,-4.78,.02),(1.45,-5.35,.02),(1.05,-6.35,.04),
           (-.65,-6.95,.08),(-2.0,-6.55,.05),(-2.15,-5.90,0),(-1.65,-5.45,-.03)]
    ahoge=[(x,y,z+.40*(x-.7)) for x,y,z in ahoge]
    if baby: ahoge=[(x,y+.55,z) for x,y,z in ahoge]
    hair_lock(child(head,'ahoge'),ahoge,[.13,.16,.15,.12,.08,.04,.003],
              ROOT_HAIR,64,.18,uv_range=(.40,.55),bend_limit=.8)
    for s in [-1,1]:
        fin=child(head,'fin_'+str(s))
        head_fin(fin,s)
        hair_bow(head,(s*radius[0]*1.06,-2.70,-1.55),.34)
    maid=child(head,'maid_head');summer=child(head,'summer_head')
    band_hair=head['quads']+[q for part in head['children'] if part['name'].startswith('hair_') for q in part['quads']]
    band_profiles={}
    for part,mat in [(maid,WHITE),(summer,BOW_BLUE)]:
        profile=hair_band_offsets(band_hair,radius,.32 if part is maid else .14)
        if part is summer:
            # The thinner band needs a little more inset to retain contact.
            profile=[(offset-.05,) for (offset,) in profile]
        band_profiles[part['name']]=profile
        points=[((radius[0]+.30+profile[i][0])*math.cos(a),-1.4-(radius[1]+profile[i][0])*math.sin(a),0)
                for i,a in enumerate(j*PI/48 for j in range(49))]
        # A thin cloth band tapers into the hair beside the ears. The old
        # constant elliptical section ended in a broad, visibly open bar.
        taper=4 if part is maid else 10
        widths=[(.16 if part is maid else .07)*(.12+.88*min(1,i/taper,(48-i)/taper)) for i in range(49)]
        tube(part,points,widths,mat,96,12,2)
    frill_sides=224;frill_rows=8
    def headdress(i,j):
        a=.15+i*(PI-.30)/frill_sides;t=j/frill_rows
        # Broad rounded cloth panels rise between narrow gathered seams.
        # Each panel bows forward within a backward-leaning frill, exposing
        # its upper face to light. The folds fade out beside the ears.
        panel=abs(math.sin(a*14))
        end_fade=min(1,i/16,(frill_sides-i)/16)
        end_fade=end_fade*end_fade*(3-2*end_fade)
        r=.12+t*(.43+.13*panel**.45)*end_fade+max(0,catmull(band_profiles['maid_head'],a/PI*48)[0])
        return ((radius[0]+r+.30)*math.cos(a),-1.2-(radius[1]+r)*math.sin(a)-.15,
                -.05+(.30-.16*panel**1.4)*math.sin(a)*t**1.2)
    for i in range(frill_sides):
        for j in range(frill_rows):
            points=[headdress(i,j),headdress(i+1,j),headdress(i+1,j+1),headdress(i,j+1)]
            coords=[(i/frill_sides,.12+j/frill_rows*.5),((i+1)/frill_sides,.12+j/frill_rows*.5),
                    ((i+1)/frill_sides,.12+(j+1)/frill_rows*.5),(i/frill_sides,.12+(j+1)/frill_rows*.5)]
            quad(maid,list(reversed(points)),WHITE,list(reversed(coords)))
            quad(maid,[(x,y,z+.04) for x,y,z in points],WHITE,coords)
    # A narrow sewn rim finishes the two-sided frill instead of leaving a
    # visibly open pair of fabric sheets along its outer silhouette.
    rim=[(x,y,z+.02) for x,y,z in (headdress(i,frill_rows) for i in range(frill_sides+1))]
    tube(maid,rim,[.025]*len(rim),PLAIN_WHITE,frill_sides,6)
    if not baby:
        # Smaller adult cranium; keep the neck contact and the long hair's reach.
        # All head children have zero offsets, so face, scalp and accessories
        # receive exactly the same deformation before normals are generated.
        def adult_head(part):
            assert part is head or part['pose']==(0,0,0)
            for q in part['quads']:
                for v in q:
                    t=max(0,min(1,(v[1]-2)/5));t=t*t*(3-2*t)
                    v[0]=round(v[0]*(.84+.16*t),5)
                    v[2]=round(v[2]*(.88+.12*t),5)
                    v[1]=round(v[1]*.82+.30 if v[1]<=2 else v[1]-.06,5)
                    # Mature head proportions taper into the full-length hair.
                    scale=.90+.10*t
                    v[0]=round(v[0]*scale,5);v[2]=round(v[2]*scale,5)
                    v[1]=round(2+(v[1]-2)*scale,5)
            for c in part['children']: adult_head(c)
        adult_head(head)
    body=child(root,'body'); m=child(body,'maid_body');su=child(body,'summer_body')
    shoulder, waist, hip, skirt_end=(16.6,19.1,19.7,21.7) if baby else (3.6,8.3,10.6,13.7)
    bw=2.1 if baby else 2.9
    profiles=[(shoulder,bw+.35,1.1),(shoulder+1,bw,1.55),(waist,bw*0.72,1.12),(hip,bw*0.9,1.5)]
    skin_profiles=[(y+(.10 if i==0 else 0),rx,rz) for i,(y,rx,rz) in enumerate(profiles)]
    torso(body,skin_profiles,SKIN,.45)
    tube(body,[(0,shoulder-(1.3 if baby else 1.95),0),(0,shoulder+0.30,0)],[0.55,0.62],SKIN,12,24)
    torso(m,[(y,rx+0.08,rz+0.06) for y,rx,rz in profiles],NAVY,.45)
    blouse_profiles=[profiles[0],(shoulder+(1 if baby else 1.9),bw if baby else bw*1.04,1.55 if baby else 2.32),
                    (waist-.7,bw*(.77 if baby else .74),1.2)]
    bodice(m,blouse_profiles,BLOUSE)
    # A fitted standing collar joins the neck to the blouse; its flared lower
    # edge sits over the shoulder cloth instead of exposing a bare neck socket.
    collar_rx,collar_rz=(1.10,.94) if baby else (.78,.72)
    torso(m,[(shoulder-1.05,.62,.62),(shoulder-.55,.66,.65),
             (shoulder+.12,collar_rx,collar_rz)],NAVY)
    # Close the shoulder opening between the collar and the bodice. The front
    # white panel meets its upper seam, while the navy yoke covers the shoulders.
    def yoke(i,j,skin=False):
        t=i/8;a=j*2*PI/64
        white=math.cos(a)<0 and abs(math.sin(a))<=math.sin(.80)
        if skin:
            rx=.62+(bw+.35-.62)*t;rz=.62+(1.10-.62)*t
            y=shoulder+.30*(1-t)+(.10+.45*math.sin(a)**2)*t
        else:
            rx=collar_rx+(bw+.43-collar_rx)*t
            rz=collar_rz+((1.28 if white else 1.16)-collar_rz)*t
            y=shoulder+.12*(1-t)+(0 if white else .45*math.sin(a)**2)*t
        return (rx*math.sin(a),y,rz*math.cos(a))
    for i in range(8):
        for j in range(64):
            a=(j+.5)*2*PI/64
            mat=BLOUSE if math.cos(a)<0 and abs(math.sin(a))<=math.sin(.80) else NAVY
            points=[yoke(i,j),yoke(i,j+1),yoke(i+1,j+1),yoke(i+1,j)]
            coords=[(j/64,i/8),((j+1)/64,i/8),((j+1)/64,(i+1)/8),(j/64,(i+1)/8)]
            quad(m,points,mat,coords)
            quad(body,[yoke(i,j,True),yoke(i,j+1,True),yoke(i+1,j+1,True),yoke(i+1,j,True)],SKIN,coords)
    lace(m,shoulder-1.09,.63,.63,WHITE,8,.45)
    bow(m,(0,shoulder+0.3,-1.8),0.75,NAVY,1,blouse_profiles,
        wing_scale=(1,1,1) if baby else (1,.65,.45))
    ellipsoid(m,(0,shoulder+0.4,-2.02),(0.28,0.37,0.1),GOLD,10,16)
    if baby:
        ellipsoid(m,(0,shoulder+0.4,-2.13),(0.18,0.24,0.05),BOW_BLUE,10,16)
    else:
        gem=child(m,'brooch_gem')
        jewel(gem,(0,shoulder+.4,-2.13),(.235,.315,.065),BOW_BLUE)
    for i in range(3):
        y=shoulder+(.65+i*.40 if baby else 1.2+i*.65)
        _,_,rz=profile_at_y(blouse_profiles,y)
        start=len(m['quads']);center_z=-rz-.23
        ellipsoid(m,(0,y,center_z),(0.13,0.13,0.05),NAVY,6,12)
        for q in m['quads'][start:]:
            for v in q:
                _,rx,local_rz=profile_at_y(blouse_profiles,v[1])
                surface=-(local_rz+.18)*math.sqrt(max(0,1-(v[0]/(rx+.10))**2))
                v[2]=round(surface-.05+v[2]-center_z,5)
    for s in [-1,1]:
        for i in range(2): ellipsoid(m,(s*1.35,waist-.2+i*.65,-1.08),(.1,.12,.04),GOLD,8,12)
    # Continuous folded strips frame the blouse. The waves grow towards the
    # free outer edge, leaving the stitched inner edge attached to the bodice.
    for s in [-1,1]:
        def bib_frill(i,j):
            t=i/64;v=j/6
            y,rx,rz=catmull(blouse_profiles,t*2)
            fold=math.sin(t*PI*18)
            width=.30*(1+.16*math.cos(t*PI*18))
            return (s*((rx+.10)*math.sin(.80)+v*width),
                    y+.035*fold*v,
                    -(rz+.20)*math.cos(.80)-.10*v-(.16 if baby else .11)*fold*v)
        for i in range(64):
            for j in range(6):
                points=[bib_frill(i,j),bib_frill(i+1,j),bib_frill(i+1,j+1),bib_frill(i,j+1)]
                if s<0: points.reverse()
                coords=[(i/64,j/6),((i+1)/64,j/6),((i+1)/64,(j+1)/6),(i/64,(j+1)/6)]
                quad(m,points,LACE,coords)
                quad(m,[(x,y,z+.025) for x,y,z in reversed(points)],WHITE,list(reversed(coords)))
    maid_skirt=skirt(m,waist,skirt_end,bw*2.15,2.7 if baby else 3.5,NAVY_EMBROIDERY,drape=not baby)
    skirt(m,skirt_end-.55,skirt_end+.25,bw*2.13,2.75 if baby else 3.55,WHITE,24,.93,drape=not baby)
    lace(m,skirt_end+.15,bw*2.15,2.75 if baby else 3.55,depth=.80 if baby else .60)
    lace(m,skirt_end+.33,bw*2.08,2.65 if baby else 3.45,depth=.80 if baby else .60)
    # Smooth curved apron follows the front of the skirt; its edge has distinct ruffles.
    apron=child(m,'apron');top=waist+0.3;bottom=skirt_end-0.25
    def apron_point(t,a):
        y=top+(bottom-top)*t
        skirt_t=(y-waist)/(skirt_end-waist)
        flare=(.42+.58*math.sin(skirt_t*PI/2)**1.3)*1.09
        x=bw*1.8*(.45+.55*math.sin(t*PI/2)**1.3)*math.sin(a)
        z=(2.7 if baby else 3.5)*flare*math.sqrt(max(0,1-(x/(bw*2.15*flare))**2))+.15
        return (x,y,-z)
    for i in range(16):
        for j in range(24):
            def p(ii,jj):
                t=ii/16;a=(jj/24-0.5)*1.55
                return apron_point(t,a)
            # Reduce the print around its centre; the white border extends
            # over the remaining cloth without sampling another atlas cell.
            def print_uv(ii,jj):
                return (max(0,min(1,.5+(jj/24-.5)/.75)),
                        max(0,min(1,.65+(ii/16-.65)/.75)))
            quad(apron,[p(i,j),p(i,j+1),p(i+1,j+1),p(i+1,j)],APRON,
                 [print_uv(i,j),print_uv(i,j+1),print_uv(i+1,j+1),print_uv(i+1,j)])
    def apron_ruffle(i,j):
        a=(i/96-.5)*1.55;t=j/5
        x,y,z=apron_point(1,a)
        return (x*(1+.055*t),y+t*(.32+.15*math.cos(i/96*PI*36)),z-.12*t)
    for i in range(96):
        for j in range(5):
            quad(apron,[apron_ruffle(i,j),apron_ruffle(i+1,j),apron_ruffle(i+1,j+1),apron_ruffle(i,j+1)],WHITE,
                 [(i/96,.65+.32*j/5),((i+1)/96,.65+.32*j/5),((i+1)/96,.65+.32*(j+1)/5),(i/96,.65+.32*(j+1)/5)])
    # Sew narrow ruffles along the two actual polygon edges. Interpolating the
    # apron's 16 segments keeps the finer fold mesh attached without T gaps.
    for s in [-1,1]:
        def apron_side(i,j,back=False):
            t=i/64;v=j/5;row=min(int(t*16),15);f=t*16-row
            a=s*.775
            left=apron_point(row/16,a);right=apron_point((row+1)/16,a)
            seam=tuple(left[c]*(1-f)+right[c]*f for c in range(3))
            fade=max(0,min(1,t/.06,(1-t)/.10));fade=fade*fade*(3-2*fade)
            fold=math.sin(t*PI*18)
            width=(.22 if baby else .30)*(1+.15*math.cos(t*PI*18))*fade
            rx=bw*1.8*(.45+.55*math.sin(t*PI/2)**1.3)
            outer=apron_point(t,a+s*v*width/(rx*math.cos(.775)))
            base=apron_point(t,a)
            x,y,z=(seam[c]+outer[c]-base[c] for c in range(3))
            return (x,y+.025*fold*fade*v,z-(.08+.06*fold)*fade*v+(.02*fade*v if back else 0))
        for i in range(64):
            for j in range(5):
                points=[apron_side(i,j),apron_side(i+1,j),apron_side(i+1,j+1),apron_side(i,j+1)]
                reverse=[apron_side(i,j,True),apron_side(i,j+1,True),apron_side(i+1,j+1,True),apron_side(i+1,j,True)]
                coords=[(i/64,j/5),((i+1)/64,j/5),((i+1)/64,(j+1)/5),(i/64,(j+1)/5)]
                back_coords=[coords[k] for k in [0,3,2,1]]
                if s<0: points.reverse();reverse.reverse();coords.reverse();back_coords.reverse()
                quad(apron,points,LACE,coords)
                quad(apron,reverse,PLAIN_WHITE,back_coords)
            edge=[apron_side(i,5),apron_side(i+1,5),apron_side(i+1,5,True),apron_side(i,5,True)]
            if s<0: edge.reverse()
            quad(apron,edge,PLAIN_WHITE)
    # Gold piping and stars, stockings bows, and a large white back ribbon.
    hem_t=(skirt_end-.5-waist)/(skirt_end-waist)
    hem=[]
    for i in range(193):
        a=i*2*PI/192
        x,y,z=maid_skirt(hem_t,a)
        if baby:
            x+=bw*2.15*.03*math.sin(a);z+=2.7*.03*math.cos(a)
        else:
            left=maid_skirt(hem_t,a-.0001);right=maid_skirt(hem_t,a+.0001)
            upper=maid_skirt(hem_t-.0001,a);lower=maid_skirt(hem_t+.0001,a)
            along=[right[c]-left[c] for c in range(3)]
            down=[lower[c]-upper[c] for c in range(3)]
            normal=[along[1]*down[2]-along[2]*down[1],along[2]*down[0]-along[0]*down[2],along[0]*down[1]-along[1]*down[0]]
            length=math.sqrt(sum(v*v for v in normal))
            sign=1 if normal[0]*x+normal[2]*z>0 else -1
            # Include clearance for the tube radius and its smoothed bends.
            x+=sign*.044*normal[0]/length;y+=sign*.044*normal[1]/length;z+=sign*.044*normal[2]/length
        hem.append((x,y,z))
    tube(m,hem,[.018]*193,GOLD,384,6)
    # The waist ribbon is tied over the rear hair, as in the reference, rather
    # than buried against the bodice underneath every long lock.
    bow(m,(0,waist+0.6,5.15 if baby else 5.45),1.15,PLAIN_WHITE,-1)
    # The short summer blouse has shaped shoulders, a sewn hem and a button
    # placket; use the plain part of the white fabric instead of its lace border.
    shirt_bottom=waist-.58
    shirt_profiles=[(shoulder-.10,bw+.43,1.18),(shoulder+1,bw+.08,1.64 if baby else 1.75),
                    (shirt_bottom,bw*.79,1.24)]
    def summer_shirt(i,j):
        t=i/32;a=j*2*PI/64
        y,rx,rz=catmull(shirt_profiles,t*2)
        shoulder_cut=.25*abs(math.sin(a))*(1-t)**4
        fold=.025*math.cos(a*12)*math.sin(t*PI)
        return ((rx+fold)*math.sin(a),y+shoulder_cut,(rz+fold)*math.cos(a))
    for i in range(32):
        for j in range(64):
            quad(su,[summer_shirt(i,j),summer_shirt(i,j+1),summer_shirt(i+1,j+1),summer_shirt(i+1,j)],
                 WHITE,[(j/64,.08+i/32*.38),((j+1)/64,.08+i/32*.38),
                        ((j+1)/64,.08+(i+1)/32*.38),(j/64,.08+(i+1)/32*.38)])
    def summer_yoke(i,j):
        t=i/12;a=j*2*PI/64
        outer=summer_shirt(0,j)
        inner=(.67*math.sin(a),shoulder-.75,.65*math.cos(a))
        return tuple(inner[c]*(1-t)+outer[c]*t+(.08*math.sin(t*PI) if c==1 else 0) for c in range(3))
    for i in range(12):
        for j in range(64):
            quad(su,[summer_yoke(i,j),summer_yoke(i,j+1),summer_yoke(i+1,j+1),summer_yoke(i+1,j)],
                 PLAIN_WHITE,[(j/64,i/12),((j+1)/64,i/12),((j+1)/64,(i+1)/12),(j/64,(i+1)/12)])
    tube(su,[(.67*math.sin(i*2*PI/64),shoulder-.76,.65*math.cos(i*2*PI/64)) for i in range(65)],
         [.028]*65,CYAN,128,8)
    for s in [-1,1]:
        collar=[(s*.38,shoulder-.73,-.70),(s*1.12,shoulder+.06,-1.35),
                (s*.83,shoulder+.84,-1.69),(s*.18,shoulder+.39,-1.65)]
        if s<0: collar.reverse()
        quad(su,collar,PLAIN_WHITE)
        quad(su,[(x,y,z+.035) for x,y,z in reversed(collar)],PLAIN_WHITE)
        tube(su,[(s*.38,shoulder-.73,-.73),(s*1.12,shoulder+.06,-1.38),
                 (s*.83,shoulder+.84,-1.72)],[.035]*3,CYAN,16,8)
    placket_end=min(shoulder+2.05,shirt_bottom-.15)
    def placket(t,x):
        y=shoulder+.32+(placket_end-shoulder-.32)*t
        _,_,rz=profile_at_y(shirt_profiles,y)
        return (x,y,-rz-.04)
    for i in range(12):
        quad(su,[placket(i/12,-.14),placket((i+1)/12,-.14),
                 placket((i+1)/12,.14),placket(i/12,.14)],BLOUSE)
    for i in range(3):
        x,y,z=placket((i+.35)/3,0)
        ellipsoid(su,(x,y,z-.055),(.085,.085,.035),WHITE,8,12)
    tube(su,[(bw*.79*math.sin(i*2*PI/64),shirt_bottom+.02,
              1.26*math.cos(i*2*PI/64)) for i in range(65)],[.065]*65,WHITE,128,8)
    emblem_start=len(su['quads'])
    emblem(su,(bw*.49,shoulder+1.13,-1.58),.30)
    for q in su['quads'][emblem_start:]:
        for v in q:
            _,rx,rz=profile_at_y(shirt_profiles,v[1])
            surface=-(rz+.03)*math.sqrt(max(0,1-(v[0]/(rx+.03))**2))
            v[2]=round(surface-.025+v[2]+1.58,5)
    summer_top=hip-.72;summer_bottom=skirt_end-.52
    skirt(su,summer_top,summer_bottom,bw*1.65,2.45 if baby else 3.1,
         (PLAIN_WHITE,PLAIN_WHITE,CYAN,PLAIN_WHITE),20,.55,.07,drape=not baby)
    # A solid waist band hides the pleated skirt's sharp top edge.
    torso(su,[(summer_top-.22,bw*.93,1.43),(summer_top+.17,bw*.95,1.48)],PLAIN_WHITE)
    tube(su,[(bw*.95*math.sin(i*2*PI/64),summer_top+.18,
              1.49*math.cos(i*2*PI/64)) for i in range(65)],[.045]*65,CYAN,128,8)
    for s in [-1,1]:
        bow(su,(s*bw*.78,summer_top+.17,-.91),.60,SUMMER_BOW)
        for side in [-1,1]:
            tube(su,[(s*bw*.78+side*.14,summer_top+.35,-.98),
                     (s*bw*.78+side*.48,summer_top+1.08,-1.56),
                     (s*bw*.78+side*.65,summer_top+1.75,-1.92)],
                 [.19,.31,.06],SUMMER_BOW,24,8,.12)
    # A broad curved whale tail with tapered sculpted lobes, articulated as a separate bone.
    tail=child(body,'tail',(0,hip,1.4))
    tail_len=6 if baby else 9
    tube(tail,[(0,0,0),(2.2,2.8,2.2),(tail_len*.72,2,2.7),(tail_len,-1,2.2),(tail_len,-3.7,1.9)],
         [.85,1.45,1.85,1.25,.5] if baby else [.75,1.20,1.45,1.0,.42],TAIL,48,20,1 if baby else .72)
    for s in [-1,1]:
        span=1 if baby else .80
        rise=1 if baby else .90
        hair_lock(tail,[(tail_len,-3.5,1.9),(tail_len+s*1.0*span,-3.5-rise,1.9),
                        (tail_len+s*2.4*span,-3.5-1.9*rise,1.9),(tail_len+s*3.5*span,-3.5-2.5*rise,1.9),
                        (tail_len+s*4.1*span,-3.5-3.7*rise,1.9)],
                  [.45,1.0,1.25,.65,.008],TAIL if baby else TAIL_FIN,40,.23,bend_limit=.8)
    for s in [-1,1]:
        arm=child(root,'left_arm' if s==1 else 'right_arm',(s*(bw+0.8),shoulder+0.35,0))
        arm_len=3.4 if baby else 7.2
        ellipsoid(arm,(0,-.2,0),(.66,.4,.63),SKIN,10,24,(-PI/2,0))
        # Horizontal elbow rings match the forearm exactly. A slanted tube
        # end left a thin wedge even before the elbow started bending.
        def upper_arm_point(i,j):
            t=i/24;a=j*2*PI/24
            radius=.66+(.58-.66)*t
            return (s*.05*t+radius*math.sin(a),-.2+(arm_len*.5+.2)*t,radius*math.cos(a))
        for i in range(24):
            for j in range(24):
                quad(arm,[upper_arm_point(i,j),upper_arm_point(i,j+1),
                          upper_arm_point(i+1,j+1),upper_arm_point(i+1,j)],SKIN,
                     [(j/24,i/24),((j+1)/24,i/24),((j+1)/24,(i+1)/24),(j/24,(i+1)/24)])
        forearm=child(arm,'forearm',(0,arm_len*.5,0))
        wrist=arm_len*.5
        arm_profiles=[(0,.58,.58,s*.05),(wrist*.35,.51,.46,s*.07),
                      (wrist*.65,.41,.34,s*.09),(wrist*.85,.33,.27,s*.10),
                      (wrist,.29,.22,s*.10),(wrist+.20,.34,.18,s*.05),
                      (wrist+.42,.44 if baby else .40,.19 if baby else .15,0),
                      (wrist+.65,.40 if baby else .36,.16 if baby else .12,0),
                      (wrist+.83,0,0,0)]
        # One continuous skin surface narrows through the wrist and broadens
        # into a flattened palm, instead of intersecting spherical joints.
        def forearm_point(i,j):
            y,rx,rz,x=catmull(arm_profiles,i/48*(len(arm_profiles)-1))
            a=j*2*PI/24
            return (x+max(0,rx)*math.sin(a),y,max(0,rz)*math.cos(a))
        for i in range(48):
            for j in range(24):
                quad(forearm,[forearm_point(i,j),forearm_point(i,j+1),
                              forearm_point(i+1,j+1),forearm_point(i+1,j)],SKIN,
                     [(j/24,i/48),((j+1)/24,i/48),((j+1)/24,(i+1)/48),(j/24,(i+1)/48)])
        hand=child(forearm,'hand',(0,-arm_len*.5,0))
        lengths=[.47,.60,.55,.41] if baby else [.70,.87,.81,.60]
        for f,length in enumerate(lengths):
            x=-.30+f*.21;spread=(f-1.5)*.028
            tube(hand,[(s*x,arm_len+.65,-.07),(s*(x+spread),arm_len+.65+length*.47,-.10),
                       (s*(x+spread*.8),arm_len+.65+length*.90,-.21),
                       (s*(x+spread*.6),arm_len+.65+length,-.27)],[.105,.098,.076,.045],SKIN,14,10,cap_end=True)
        # Mirrored inward thumbs oppose the fingers; relaxed digits bend at their joints.
        tube(hand,[(-s*.33,arm_len+.25,-.02),(-s*.58,arm_len+.45,-.15),
                   (-s*.62,arm_len+.69,-.28),(-s*.55,arm_len+.83,-.34)],[.15,.13,.10,.045],SKIN,16,12,cap_end=True)
        # Shoulder flounces belong to the arm so they follow its animation.
        summer_arm=child(arm,'summer_arm')
        ellipsoid(summer_arm,(0,-.2,0),(.685,.41,.655),PLAIN_WHITE,10,24,(-PI/2,0))
        def summer_flounce(i,j):
            a=i*2*PI/64;v=j/6
            outer=max(0,s*math.sin(a))
            fold=math.sin(a*12)
            r=.64+v*(.16+.17*outer+.045*fold)
            return (r*math.sin(a),-.20+v*(.42+.25*outer+.07*fold),r*.96*math.cos(a))
        for i in range(64):
            for j in range(6):
                points=[summer_flounce(i,j),summer_flounce(i+1,j),
                        summer_flounce(i+1,j+1),summer_flounce(i,j+1)]
                coords=[(i/64,j/6*.45),((i+1)/64,j/6*.45),
                        ((i+1)/64,(j+1)/6*.45),(i/64,(j+1)/6*.45)]
                quad(summer_arm,points,WHITE,coords)
                quad(summer_arm,[(x*.985,y,z*.985) for x,y,z in reversed(points)],CYAN,list(reversed(coords)))
        sleeve=child(arm,'maid_arm')
        ellipsoid(sleeve,(0,-.38,0),(.63,.30,.63*.95),NAVY,10,24,(-PI/2,0))
        def puff_sleeve(i,j):
            t=i/24;a=j*2*PI/48
            y,r=catmull([(-.38,.63),(.30,.90),(.95,.96),(1.65,.70)],t*3)
            fold=1+.055*math.cos(a*10)*math.sin(t*PI)
            return (r*fold*math.sin(a),y,r*.95*fold*math.cos(a))
        for i in range(24):
            for j in range(48):
                quad(sleeve,[puff_sleeve(i,j),puff_sleeve(i,j+1),puff_sleeve(i+1,j+1),puff_sleeve(i+1,j)],
                     NAVY,[(j/48,.05+i/24*.40),((j+1)/48,.05+i/24*.40),((j+1)/48,.05+(i+1)/24*.40),(j/48,.05+(i+1)/24*.40)])
        tube(sleeve,[(0,1.5,0),(0,arm_len*.5,0)],[0.7,0.63],NAVY,10,16)
        sleeve=child(forearm,'maid_forearm',(0,-arm_len*.5,0))
        tube(sleeve,[(0,arm_len*.5,0),(0,arm_len-0.4,0)],[0.63,0.55],NAVY,10,16)
        # A broad navy cuff anchors a thin, fluted lace skirt around the wrist.
        # Its closed inner band and continuous outer hem replace the bead ring.
        tube(sleeve,[(0,arm_len-.85,0),(0,arm_len-.28,0)],[.68,.70],NAVY_CUFF,8,32)
        tube(sleeve,[(.71*math.sin(i*2*PI/48),arm_len-.43,.68*math.cos(i*2*PI/48)) for i in range(49)],
             [.025]*49,GOLD,96,6)
        def cuff_frill(i,j):
            a=i*2*PI/96;v=j/6
            fold=math.cos(a*12)
            radius=.59+v*(.27+.09*fold)
            return (radius*math.sin(a),arm_len-.27+v*(.47+.08*fold),radius*.95*math.cos(a))
        for i in range(96):
            for j in range(6):
                points=[cuff_frill(i,j),cuff_frill(i+1,j),cuff_frill(i+1,j+1),cuff_frill(i,j+1)]
                coords=[(i/96,j/6),((i+1)/96,j/6),((i+1)/96,(j+1)/6),(i/96,(j+1)/6)]
                quad(sleeve,points,LACE,coords)
                quad(sleeve,[(x*.985,y,z*.985) for x,y,z in reversed(points)],WHITE,list(reversed(coords)))
        leg_y=20.5 if baby else 12.8;leg_len=24-leg_y
        leg=child(root,'left_leg' if s==1 else 'right_leg',(s*(0.9 if baby else 1.35),leg_y,0))
        thigh=[(0,0,0),(0,leg_len*.16,-.03),(0,leg_len*.34,-.02),(0,leg_len*.5,0)]
        thigh_width=[.70,.73,.67,.60] if baby else [1.01,1.06,.90,.71]
        tube(leg,thigh,thigh_width,SKIN,24,24,vertical_ends=True)
        shin=child(leg,'shin',(0,leg_len*.5,0))
        calf_len=leg_len*.5-.7
        calf=[(0,0,0),(0,calf_len*.23,.045),(0,calf_len*.48,.08),(0,calf_len*.78,.025),(0,calf_len,0)]
        calf_width=[.60,.62,.60,.48,.37] if baby else [.71,.77,.78,.55,.40]
        tube(shin,calf,calf_width,SKIN,24,24,vertical_ends=True)
        stocking=child(leg,'maid_leg')
        if baby:
            tube(stocking,[(x,max(.25,y),z) for x,y,z in thigh],[w+.035 for w in thigh_width],STOCKING,24,24,vertical_ends=True)
            bow(stocking,(s*0.6,0.7,-0.5),0.25,NAVY)
        else:
            tube(stocking,thigh,[w+.035 for w in thigh_width],STOCKING,24,24,start=1.4,vertical_ends=True)
            stock_top=catmull(thigh,1.4)[1]
            stock_radius=thigh_width[1]*.6+thigh_width[2]*.4+.09
            lace(stocking,stock_top,stock_radius,stock_radius,WHITE,12,.36)
            bow_start=len(stocking['quads'])
            bow(stocking,(s*.75,stock_top+.3,-.78),.25,NAVY,.7)
            sock_profile=[(p[1],w+.035,p[2]) for p,w in zip(thigh,thigh_width)]
            for q in stocking['quads'][bow_start:]:
                for v in q:
                    _,r,center_z=profile_at_y(sock_profile,v[1])
                    surface=center_z-math.sqrt(max(0,r*r-v[0]*v[0]))
                    v[2]=round(surface-.12+v[2]+.78,5)
        stocking=child(shin,'maid_shin',(0,-leg_len*.5,0))
        tube(stocking,[(x,y+leg_len*.5,z) for x,y,z in calf],[w+.035 for w in calf_width],STOCKING,24,24,vertical_ends=True)
        def shoe_sole(part,y,rx,rz,material,height):
            # Flat contact surface; the narrower heel and broad toe read as a foot.
            def p(j,yy):
                a=j*2*PI/48
                return (rx*(.88-.12*math.cos(a))*math.sin(a),yy,-.43+rz*math.cos(a))
            for j in range(48):
                quad(part,[p(j,y-height),p(j+1,y-height),p(j+1,y),p(j,y)],material,
                     [(j/48,0),((j+1)/48,0),((j+1)/48,1),(j/48,1)])
                quad(part,[(0,y,-.43),p(j,y),p(j+1,y),(0,y,-.43)],material)
        # Mary Jane: a rounded toe cap, white stocking visible through the
        # instep opening, one arched foot strap, a thin outsole and a low heel.
        stocking=child(stocking,'shoe')
        shoe_sole(stocking,leg_len,.79,1.24,DARK,.17)
        ellipsoid(stocking,(0,leg_len-.60,-.72),(.75,.40,.95),NAVY,16,32)
        ellipsoid(stocking,(0,leg_len-.72,.28),(.54,.28,.44),NAVY,12,24)
        ellipsoid(stocking,(0,leg_len-.98,-.20),(.50,.065,.44),STOCKING,10,24)
        tube(stocking,[(-.62,leg_len-.92,-.23),(-.32,leg_len-1.07,-.24),
                       (0,leg_len-1.10,-.24),(.32,leg_len-1.07,-.24),(.62,leg_len-.92,-.23)],
             [.085]*5,NAVY,24,10)
        ellipsoid(stocking,(s*.62,leg_len-.91,-.24),(.105,.135,.05),GOLD,10,16)
        ellipsoid(stocking,(0,leg_len-.28,.40),(.35,.28,.34),DARK,12,24)
        # Summer sneakers have a separate sole, rounded bumper, heel counter,
        # tongue and crossed laces rather than a single patterned ellipsoid.
        shoe=child(shin,'summer_shin',(0,-leg_len*.5,0))
        tube(shoe,[(0,leg_len-1.95,0),(0,leg_len-.80,0)],[.55,.43],STOCKING,12,20)
        shoe=child(shoe,'shoe')
        shoe_sole(shoe,leg_len,.88,1.30,WHITE,.25)
        shoe_sole(shoe,leg_len-.035,.89,1.31,CYAN,.07)
        ellipsoid(shoe,(0,leg_len-.61,-.57),(.79,.43,1.11),STOCKING,16,32)
        ellipsoid(shoe,(0,leg_len-.82,.35),(.55,.34,.38),CYAN,12,24)
        ellipsoid(shoe,(0,leg_len-.32,-1.51),(.67,.17,.22),NAVY,10,24)
        ellipsoid(shoe,(0,leg_len-1.03,-.44),(.34,.13,.66),WHITE,12,24)
        for side in [-1,1]:
            tube(shoe,[(side*.72,leg_len-.64,-1.08),(side*.75,leg_len-.75,-.40),
                       (side*.63,leg_len-.76,.23)],[.065,.085,.065],CYAN,18,10)
        for j in range(4):
            z=-.93+j*.27
            tube(shoe,[(-.34,leg_len-1.10,z),(0,leg_len-1.17,z+.13),(.34,leg_len-1.10,z+.25)],
                 [.037]*3,NAVY,10,8)
            tube(shoe,[(.34,leg_len-1.10,z),(0,leg_len-1.17,z+.13),(-.34,leg_len-1.10,z+.25)],
                 [.037]*3,NAVY,10,8)
        bow(shoe,(0,leg_len-1.14,.18),.15,NAVY)
    return root

def main():
    for baby in [False,True]:
        model=character(baby)
        if baby:
            ys=[]
            def bounds(n,offset=0):
                offset+=n['pose'][1]
                ys.extend(v[1]+offset for q in n['quads'] for v in q)
                for c in n['children']: bounds(c,offset)
            bounds(model)
            factor=16/(max(ys)-min(ys));bias=8-min(ys)*factor
            def resize(n):
                n['pose']=[n['pose'][0],n['pose'][1]*factor,n['pose'][2]]
                for q in n['quads']:
                    for v in q: v[1]=round(v[1]*factor,5)
                for c in n['children']: resize(c)
            resize(model)
            for part in model['children']: part['pose'][1]+=bias
        smooth_normals(model)
        if not baby: blend_adult_hair_roots(model)
        raw=json.dumps(model,separators=(',',':')).encode()
        path=OUT/('models/entity/juvenile.mesh.json.gz' if baby else 'models/entity/adult.mesh.json.gz')
        path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(gzip.compress(raw,mtime=0))
        def count(n): return len(n['quads'])+sum(count(c) for c in n['children'])
        print(path.name, count(model),'quads',len(raw),'JSON bytes')

def blend_adult_hair_roots(model):
    # Roots emerge from either the scalp or an inner lock. Sample the actual
    # covering surface, rather than painting every join with the scalp color.
    head=next(p for p in model['children'] if p['name']=='head')
    radii=(3.25*1.028*.84*.90,3.65*1.03*.82*.90,2.35*1.028*.88*.90)
    center_y=2+(-1.2*.82+.30-2)*.90
    bins={}
    underlays=[head]+[p for p in head['children']
                     if p['name'].startswith('hair_') and int(p['name'][5:])<19]
    for part in underlays:
        for q in part['quads']:
            if any((int(v[3]*4),int(v[4]*4))!=(HAIR%4,HAIR//4) for v in q): continue
            for a,b,c in ((q[0],q[1],q[2]),(q[0],q[2],q[3])):
                det=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
                if abs(det)<1e-10 or max(a[2],b[2],c[2])<=0: continue
                tri=(a,b,c,det)
                for x in range(math.floor(min(a[0],b[0],c[0])*4),math.floor(max(a[0],b[0],c[0])*4)+1):
                    for y in range(math.floor(min(a[1],b[1],c[1])*4),math.floor(max(a[1],b[1],c[1])*4)+1):
                        bins.setdefault((x,y),[]).append(tri)
    for part in head['children']:
        if not part['name'].startswith('hair_') or int(part['name'][5:])<19: continue
        root_y=min(v[1] for q in part['quads'] for v in q)
        for q in part['quads']:
            for v in q:
                t=max(0,min(1,((v[1]-root_y)/(.82*.90)-1.5)/2))
                if t>=1: continue
                target=[v[0]/radii[0]**2,(v[1]-center_y)/radii[1]**2,v[2]/radii[2]**2]
                length=math.sqrt(sum(n*n for n in target))
                target=[n/length for n in target]
                facing=max(0,min(1,4*sum(a*b for a,b in zip(v[5:8],target))))
                weight=(1-t*t*(3-2*t))*facing*facing*(3-2*facing)
                sample=None
                for a,b,c,det in bins.get((math.floor(v[0]*4),math.floor(v[1]*4)),()):
                    u=((b[1]-c[1])*(v[0]-c[0])+(c[0]-b[0])*(v[1]-c[1]))/det
                    w=((c[1]-a[1])*(v[0]-c[0])+(a[0]-c[0])*(v[1]-c[1]))/det
                    if min(u,w,1-u-w)<-1e-6: continue
                    candidate=[u*a[i]+w*b[i]+(1-u-w)*c[i] for i in range(2,8)]
                    if sample is None or candidate[0]>sample[0]: sample=candidate
                if sample is not None:
                    v[3:5]=[round(a*(1-weight)+b*weight,5) for a,b in zip(v[3:5],sample[1:3])]
                    target=sample[3:6]
                    length=math.sqrt(sum(n*n for n in target))
                    target=[n/length for n in target]
                normal=[a*(1-weight)+b*weight for a,b in zip(v[5:8],target)]
                length=math.sqrt(sum(n*n for n in normal))
                v[5:8]=[round(n/length,6) for n in normal]


def smooth_normals(part):
    # Position welding preserves normals over UV seams; material boundaries keep their edges.
    sums={}
    flat_normals=[] if part['name']=='brooch_gem' else None
    def key(v): return (*v[:3],int(v[3]*4),int(v[4]*4))
    for q in part['quads']:
        n=[0.,0.,0.]
        # A pole quad has a degenerate first triangle; its second still gives
        # the surface direction. Sum both triangle areas before welding.
        for j,k in [(1,2),(2,3)]:
            a=[q[j][i]-q[0][i] for i in range(3)];b=[q[k][i]-q[0][i] for i in range(3)]
            face=(a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
            for i in range(3): n[i]+=face[i]
        if flat_normals is not None:
            flat_normals.append(n)
        else:
            for v in q:
                total=sums.setdefault(key(v),[0.,0.,0.])
                for i in range(3): total[i]+=n[i]
    for qi,q in enumerate(part['quads']):
        for v in q:
            # Jewel cuts retain one normal per face; cloth and skin stay smooth.
            n=flat_normals[qi] if flat_normals is not None else sums[key(v)]
            length=math.sqrt(sum(x*x for x in n))
            if part['name'] in ('face','head_back'):
                # Both skin halves share the soft anime lighting bias, so
                # the jaw boundary does not become a dark diagonal seam.
                actual=[x/length for x in n] if length>1e-8 else [0,0,-1]
                blended=[actual[0]*.45,actual[1]*.45-.44,actual[2]*.45-.33]
                soft_length=math.sqrt(sum(x*x for x in blended))
                v[5:]=[round(x/soft_length,6) for x in blended]
            else:
                v[5:]=[round(x/length,6) for x in n] if length>1e-8 else [0,1,0]
    for c in part['children']: smooth_normals(c)

if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--procedural',action='store_true',
                        help='Explicitly replace edited runtime meshes with procedural geometry')
    args=parser.parse_args()
    models=Path(__file__).resolve().parents[1]/'models'
    if not args.procedural and any(models.glob('*.bbmodel')):
        parser.error('Edited Blockbench models are authoritative. Import them with '
                     'python tools/import_blockbench.py models/bigfatfish_pixel.bbmodel --pixel and '
                     'python tools/import_blockbench.py models/bigfatfish_pixel_juvenile.bbmodel --pixel --juvenile. '
                     'Use --procedural only to intentionally replace those meshes.')
    main()
