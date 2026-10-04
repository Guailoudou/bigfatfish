"""Build smooth, articulated character meshes from the three supplied design references.

No voxel approximation or runtime meshing: generated indexed quad surfaces are loaded
once into Minecraft's native ModelPart polygons. Units are 1/16 block, Y points down.
"""
from pathlib import Path
import gzip, json, math
from generate_assets import png, canvas

OUT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/bigfatfish'
PI = math.pi
SKIN, HAIR, NAVY, WHITE, CYAN, GOLD, SOLE, DARK = range(8)
ROOT_HAIR=8

def node(name, pose=(0,0,0)):
    return {'name':name, 'pose':pose, 'quads':[], 'children':[]}

def child(parent, name, pose=(0,0,0)):
    part=node(name,pose); parent['children'].append(part); return part

def uv(material,u,v):
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

def face(part, center, radius):
    # Front hemisphere, planar-projected UVs preserve the painted eyes and smile.
    def p(i,j):
        lat=-PI/2+PI*i/24; lon=-PI/2+PI*j/32
        x=math.cos(lat)*math.sin(lon); y=math.sin(lat)
        return [round(center[0]+radius[0]*x,5),round(center[1]+radius[1]*y,5),round(center[2]-radius[2]*math.cos(lat)*math.cos(lon),5),round(x/2+0.5,5),round(y/2+0.5,5)]
    for i in range(24):
        for j in range(32): part['quads'].append([p(i,j),p(i+1,j),p(i+1,j+1),p(i,j+1)])

def catmull(points, t):
    t=min(t,len(points)-1.000001); i=int(t); f=t-i
    p0=points[max(0,i-1)];p1=points[i];p2=points[min(i+1,len(points)-1)];p3=points[min(i+2,len(points)-1)]
    return tuple(0.5*((2*b)+(-a+c)*f+(2*a-5*b+4*c-d)*f*f+(-a+3*b-3*c+d)*f*f*f) for a,b,c,d in zip(p0,p1,p2,p3))

def tube(part, points, widths, material, sections=28, sides=12, depth=1):
    rows=[]
    for i in range(sections+1):
        t=i/sections*(len(points)-1); p=catmull(points,t)
        previous=catmull(points,max(0,t-0.01)); following=catmull(points,min(len(points)-1.000001,t+0.01))
        tangent=[b-a for a,b in zip(previous,following)]; length=math.sqrt(sum(c*c for c in tangent)) or 1
        tangent=[c/length for c in tangent]
        side=[tangent[1],-tangent[0],0]; length=math.sqrt(sum(c*c for c in side)) or 1
        side=[c/length for c in side]
        if length<0.01: side=[1,0,0]
        normal=[tangent[1]*side[2]-tangent[2]*side[1],tangent[2]*side[0]-tangent[0]*side[2],tangent[0]*side[1]-tangent[1]*side[0]]
        k=min(int(t),len(widths)-2);f=t-k;w=widths[k]*(1-f)+widths[k+1]*f
        rows.append([tuple(p[c]+w*(math.cos(a)*side[c]+math.sin(a)*normal[c]*depth) for c in range(3)) for a in [j*2*PI/sides for j in range(sides)]])
    for i in range(sections):
        for j in range(sides):
            k=(j+1)%sides
            quad(part,[rows[i][j],rows[i][k],rows[i+1][k],rows[i+1][j]],material,[(j/sides,i/sections),(k/sides,i/sections),(k/sides,(i+1)/sections),(j/sides,(i+1)/sections)])

def skirt(part, top, bottom, rx, rz, material, pleats=24):
    sides=pleats*4; rings=12
    def p(i,j):
        t=i/rings;a=j*2*PI/sides
        ripple=1+0.055*math.cos(a*pleats)*(0.3+0.7*t)
        flare=(0.66+0.34*t*t)*ripple
        return (rx*flare*math.sin(a),top+(bottom-top)*t,rz*flare*math.cos(a))
    for i in range(rings):
        for j in range(sides): quad(part,[p(i,j),p(i,j+1),p(i+1,j+1),p(i+1,j)],material if not isinstance(material,tuple) else material[(j//4)%len(material)])

def torso(part, profiles, material):
    rows=[]
    for i in range(25):
        y,rx,rz=catmull(profiles,i/24*(len(profiles)-1))
        rows.append([(rx*math.sin(j*2*PI/40),y,rz*math.cos(j*2*PI/40)) for j in range(40)])
    for i in range(24):
        for j in range(40):
            k=(j+1)%40
            quad(part,[rows[i][j],rows[i][k],rows[i+1][k],rows[i+1][j]],material)

def lace(part, y, rx, rz, material=WHITE, scallops=36):
    def p(i,j):
        a=j*2*PI/(scallops*4); t=i/4
        edge=0.35+0.22*math.cos(a*scallops)
        return ((rx+t*0.3)*math.sin(a),y+t*edge,(rz+t*0.3)*math.cos(a))
    for i in range(4):
        for j in range(scallops*4): quad(part,[p(i,j),p(i,j+1),p(i+1,j+1),p(i+1,j)],material)

def bow(part,center,size,material):
    for sign in [-1,1]:
        ellipsoid(part,(center[0]+sign*size*0.75,center[1],center[2]),(size,0.55*size,0.25*size),material,8,16)
    ellipsoid(part,center,(size*0.3,size*0.4,size*0.35),material,8,12)

def emblem(part, center, size):
    ellipsoid(part,center,(size,size*0.65,0.035),HAIR,8,24)
    tube(part,[center,(center[0]+size,center[1]-size*0.2,center[2]),(center[0]+size*1.3,center[1]-size,center[2])],[size*0.22,size*0.15,0.01],HAIR,12,8)
    ellipsoid(part,(center[0]-size*0.4,center[1]-size*0.15,center[2]-0.04),(size*0.08,size*0.08,0.015),WHITE,6,8)
    for i in range(3): ellipsoid(part,(center[0]-size*0.2+i*size*0.3,center[1]-size*(1.1+0.2*(i%2)),center[2]),(size*0.1,size*0.16,0.02),HAIR,6,8)

def character(baby):
    root=node('root'); head=child(root,'head',(0,14.5 if baby else 0,0));child(head,'hat')
    center=(0,-1.2,0); radius=(3.65,3.7,2.85) if baby else (4.0,4.5,3.05)
    ellipsoid(head,center,radius,SKIN,24,48)
    front=node('face');face(front,center,tuple(v*1.003 for v in radius))
    head['children'].append(front)
    ellipsoid(head,(0,-1.35,0.1),(radius[0]*1.025,radius[1]*1.04,radius[2]*1.04),ROOT_HAIR,14,48,(-PI/2,-0.13))
    # Broad layered locks, with twisting silhouettes and tapered curled ends.
    length=10 if baby else 24
    for i in range(29):
        a=PI*i/28;x=math.cos(a)*(radius[0]+0.12);z=math.sin(a)*radius[2]+0.3
        s=1 if x>=0 else -1
        points=[(x,-3,z),(x+s*0.5,0,z+0.4)]
        for j in range(1,7):
            points.append((x+s*(1.1+1.2*math.sin(j*2.0+i*0.55)),length*j/7,z+0.6+1.1*math.sin(j*1.7+i)))
        points.extend([(x+s*1.2,length,z),(x+s*0.15,length-0.8,z-0.45)])
        tube(child(head,'hair_'+str(i)),points,[0.65,0.82,0.92,0.92,0.85,0.8,0.75,0.55,0.32,0.015],HAIR,48,12,0.52)
    for i in range(9):
        x=(i-4)*0.8;end=-2.5+(i%3)*0.25
        tube(head,[(x*0.72,-5.1,-1.3),(x,-4.1,-2.9),(x+0.15,-3.0,-3.05),(x-0.25,end,-3.1)],[0.4,0.72,0.58,0.015],ROOT_HAIR,20,12,0.3)
    ahoge=[(-1.5,-5.1,0),(-2.0,-6.0,0),(-0.8,-6.45,0),(1,-5.8,0),(1.4,-5.1,0),(0.7,-4.8,0)]
    if not baby: ahoge=[(x,y-0.7,z) for x,y,z in ahoge]
    tube(child(head,'ahoge'),ahoge,[0.14,0.14,0.13,0.11,0.09,0.025],ROOT_HAIR,40,8)
    for s in [-1,1]:
        fin=child(head,'fin_'+str(s))
        tube(fin,[(s*3.7,-1.9,0),(s*4.6,-1.7,0),(s*5.3,-1.6,0),(s*6.1,-2.0,0)],[0.65,0.62,0.35,0.02],NAVY,24,12,0.3)
        tube(fin,[(s*3.8,-1.5,-0.12),(s*4.7,-1.2,-0.12),(s*5.4,-1.45,-0.1)],[0.28,0.28,0.01],WHITE,16,8,0.3)
        bow(head,(s*3.9,-3.0,-1.3),0.43,CYAN)
    maid=child(head,'maid_head');summer=child(head,'summer_head')
    for part,mat in [(maid,WHITE),(summer,CYAN)]:
        points=[(radius[0]*math.cos(a),-1.2-radius[1]*math.sin(a)-0.2,0) for a in [i*PI/24 for i in range(25)]]
        tube(part,points,[0.18]*25,mat,48,8,3)
    for i in range(17):
        a=i*PI/16
        ellipsoid(maid,(radius[0]*math.cos(a),-1.2-radius[1]*math.sin(a)-0.5,0),(0.42,0.58,0.65),WHITE,8,12)
    body=child(root,'body'); m=child(body,'maid_body');su=child(body,'summer_body')
    shoulder, waist, hip, skirt_end=(16.6,19.1,19.7,21.7) if baby else (4.1,10.3,12.5,17)
    bw=2.1 if baby else 2.65
    profiles=[(shoulder,bw*0.85,1.1),(shoulder+1,bw,1.55),(waist,bw*0.72,1.12),(hip,bw*0.9,1.5)]
    torso(body,profiles,SKIN)
    ellipsoid(body,(0,shoulder-0.5,0),(0.85,0.8,0.8),SKIN,10,20)
    torso(m,[(y,rx+0.08,rz+0.06) for y,rx,rz in profiles],NAVY)
    ellipsoid(m,(0,shoulder+1.6,-1.3),(bw*0.73,1.7 if not baby else 1.15,0.4),WHITE,16,32)
    bow(m,(0,shoulder+0.3,-1.8),0.75,NAVY)
    ellipsoid(m,(0,shoulder+0.4,-2.02),(0.28,0.37,0.1),GOLD,10,16)
    ellipsoid(m,(0,shoulder+0.4,-2.13),(0.18,0.24,0.05),CYAN,10,16)
    for i in range(3): ellipsoid(m,(0,shoulder+1.2+i*0.65,-1.72),(0.13,0.13,0.05),NAVY,6,12)
    for s in [-1,1]:
        for i in range(9):
            ellipsoid(m,(s*(1.65+i*0.08),shoulder+0.15+i*0.25,-1.2),(0.22,0.3,0.23),WHITE,6,12)
    skirt(m,waist,skirt_end,bw*1.85,2.7 if baby else 3.5,NAVY)
    lace(m,skirt_end,bw*1.85,2.7 if baby else 3.5)
    lace(m,skirt_end+0.25,bw*1.75,2.5 if baby else 3.3)
    # Smooth curved apron follows the front of the skirt; its edge has distinct ruffles.
    apron=child(m,'apron');top=waist+0.3;bottom=skirt_end-0.25
    for i in range(16):
        for j in range(24):
            def p(ii,jj):
                t=ii/16;a=(jj/24-0.5)*1.55
                return (bw*1.8*(0.7+0.3*t)*math.sin(a),top+(bottom-top)*t,-(2.8 if baby else 3.6)*(0.72+0.28*t)*math.cos(a)-0.04)
            quad(apron,[p(i,j),p(i,j+1),p(i+1,j+1),p(i+1,j)],WHITE)
    for j in range(25):
        a=(j/24-0.5)*1.55
        ellipsoid(apron,(bw*1.8*math.sin(a),bottom,-(2.8 if baby else 3.6)*math.cos(a)-0.07),(0.24,0.32,0.12),WHITE,6,10)
    emblem(apron,(0,bottom-0.8,-(2.8 if baby else 3.6)-0.1),0.55 if baby else 0.8)
    # Gold piping and stars, stockings bows, and a large white back ribbon.
    tube(m,[(bw*1.85*math.sin(a),skirt_end-0.8,(2.7 if baby else 3.5)*math.cos(a)) for a in [i*2*PI/64 for i in range(65)]],[0.04]*65,GOLD,128,6)
    bow(m,(0,waist+0.6,1.9),1.15,WHITE)
    for s in [-1,1]: tube(m,[(s*0.4,waist+0.7,2.2),(s*1.1,waist+1.7,2.8),(s*1.0,waist+3,3.1)],[0.5,0.65,0.03],WHITE,18,8,0.1)
    torso(su,[(shoulder,bw*0.87,1.18),(shoulder+1,bw+0.07,1.64),(waist-0.5,bw*0.78,1.2)],WHITE)
    for s in [-1,1]: tube(su,[(s*0.5,shoulder,-1.5),(s*0.9,shoulder+0.45,-1.8),(s*1.2,shoulder+0.9,-1.6)],[0.18,0.28,0.02],CYAN,12,8,0.3)
    emblem(su,(bw*0.5,shoulder+1.1,-1.64),0.33)
    skirt(su,hip-0.7,skirt_end-0.5,bw*1.65,2.45 if baby else 3.1,(WHITE,CYAN,WHITE,WHITE),20)
    for s in [-1,1]: bow(su,(s*bw,hip-0.7,0),0.55,CYAN)
    # A broad curved whale tail with tapered sculpted lobes, articulated as a separate bone.
    tail=child(body,'tail',(0,hip,1.4))
    tail_len=7 if baby else 13
    tube(tail,[(0,0,0),(1,1,tail_len*0.35),(2,0.5,tail_len*0.7),(2.8,-2.4,tail_len*0.95),(3,-4.2,tail_len)],[0.9,1.6,2.0,1.25,0.55],NAVY,40,16)
    for s in [-1,1]: tube(tail,[(3,-4,tail_len),(3+s*1.2,-4.9,tail_len),(3+s*2.8,-6.2,tail_len-0.2),(3+s*3.8,-8.6,tail_len-0.4)],[0.65,1.4,1.2,0.01],NAVY,24,16,0.28)
    for s in [-1,1]:
        arm=child(root,'left_arm' if s==1 else 'right_arm',(s*(bw+0.8),shoulder+0.35,0))
        arm_len=3.4 if baby else 7.2
        tube(arm,[(0,-0.2,0),(s*0.05,arm_len*0.5,0),(s*0.1,arm_len,0)],[0.66,0.58,0.42],SKIN,18,16)
        hand=child(arm,'hand');ellipsoid(hand,(s*0.1,arm_len+0.55,0),(0.55,0.7,0.32),SKIN,10,16)
        for f in range(4): tube(hand,[(s*(-0.28+f*0.18),arm_len+0.7,-0.02),(s*(-0.28+f*0.18),arm_len+1.35,-0.02)],[0.12,0.08],SKIN,6,8)
        sleeve=child(arm,'maid_arm');ellipsoid(sleeve,(0,0.8,0),(0.95,1.3,0.9),NAVY,12,24)
        tube(sleeve,[(0,1.5,0),(0,arm_len-0.4,0)],[0.7,0.55],NAVY,14,16)
        ellipsoid(sleeve,(0,arm_len-0.3,0),(0.78,0.32,0.74),WHITE,8,24)
        for j in range(12):
            a=j*2*PI/12;ellipsoid(sleeve,(0.7*math.sin(a),arm_len,0.7*math.cos(a)),(0.2,0.28,0.18),WHITE,6,8)
        leg_y=20.5 if baby else 14.0;leg_len=24-leg_y
        leg=child(root,'left_leg' if s==1 else 'right_leg',(s*(0.9 if baby else 1.35),leg_y,0))
        tube(leg,[(0,0,0),(0,leg_len*0.5,0),(0,leg_len-1.0,0)],[0.7 if baby else 0.83,0.63,0.43],SKIN,20,20)
        stocking=child(leg,'maid_leg');tube(stocking,[(0,0.4,0),(0,leg_len*0.5,0),(0,leg_len-0.7,0)],[0.74 if baby else 0.87,0.66,0.47],WHITE,20,20)
        bow(stocking,(s*0.6,0.7,-0.5),0.25,NAVY)
        ellipsoid(stocking,(0,leg_len-0.5,-0.5),(0.77,0.5,1.3),NAVY,12,24)
        tube(stocking,[(-0.68,leg_len-0.7,-0.1),(0,leg_len-1.1,-0.45),(0.68,leg_len-0.7,-0.1)],[0.1,0.1,0.1],NAVY,12,8)
        ellipsoid(stocking,(s*0.69,leg_len-0.65,-0.1),(0.12,0.17,0.05),GOLD,8,12)
        shoe=child(leg,'summer_leg');tube(shoe,[(0,leg_len-2.1,0),(0,leg_len-0.7,0)],[0.6,0.48],WHITE,10,16)
        ellipsoid(shoe,(0,leg_len-0.58,-0.5),(0.82,0.52,1.35),WHITE,12,24)
        ellipsoid(shoe,(0,leg_len-0.22,-0.5),(0.84,0.2,1.37),SOLE,8,24)
        for j in range(4): tube(shoe,[(-0.45,leg_len-1.02,-1.1+j*0.27),(0.45,leg_len-1.02,-1.1+j*0.27)],[0.045,0.045],NAVY,5,6)
    return root

def main():
    colors=[(251,224,213),(43,73,157),(33,39,79),(246,244,253),(109,181,229),(212,170,85),(103,144,201),(24,34,70),(43,66,142)]
    pixels,draw=canvas(256,256)
    for material,color in enumerate(colors):
        for y in range(64):
            for x in range(64):
                shine=math.sin(x/64*PI)*10-5
                if material==HAIR:
                    t=y/63; rgb=tuple(int(a+(b-a)*t+shine) for a,b in zip(color,(65,173,208)))
                else: rgb=tuple(max(0,min(255,int(c+shine-y/64*5))) for c in color)
                draw(material%4*64+x,material//4*64+y,material%4*64+x+1,material//4*64+y+1,rgb)
    png(OUT/'textures/entity/materials.png',256,256,pixels)
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
        raw=json.dumps(model,separators=(',',':')).encode()
        path=OUT/('models/entity/juvenile.mesh.json.gz' if baby else 'models/entity/adult.mesh.json.gz')
        path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(gzip.compress(raw,mtime=0))
        def count(n): return len(n['quads'])+sum(count(c) for c in n['children'])
        print(path.name, count(model),'quads',len(raw),'JSON bytes')

if __name__=='__main__': main()
