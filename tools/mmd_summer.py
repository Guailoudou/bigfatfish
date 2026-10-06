"""Dress the original MMD character in the white/blue summer outfit.

Original face, hair, morphs and source vertices are preserved. Shoulder surfaces
are replaced and missing skin is filled; garments reuse the summer atlas.
"""
import base64
import copy
from collections import Counter, defaultdict
import gzip
import json
import math
import struct
import uuid
from pathlib import Path
import generate_character as g

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/bigfatfish'


def generate():
    source=json.loads(gzip.decompress((ASSETS/'models/entity/mmd.mesh.json.gz').read_bytes()))
    mesh=copy.deepcopy(source)
    mesh['indices']=[];mesh['materials']=[]
    # Remove maid headdress, shoes, socks, bow, sleeves, dress and stockings.
    for i,material in enumerate(source['materials']):
        if i==25 or 27<=i<=37 or i==42:
            continue
        material=copy.deepcopy(material)
        start=material['start'];material['start']=len(mesh['indices'])
        visible=source['indices'][start:start+material['count']]
        if i==9:
            # Replace the old sleeve-opening shoulder patches as well as the
            # missing arm. Otherwise two independently weighted skin surfaces
            # cross each other at the shoulder during raised-arm poses.
            visible=[index for t in range(0,len(visible),3)
                     if not all(abs(source['vertices'][v][0])/source['scale']>.75
                                and 10<(24-source['vertices'][v][1])/source['scale']<11.5
                                for v in visible[t:t+3])
                     for index in visible[t:t+3]]
            # The source hides part of the forearms using texture alpha too.
            # Give summer its own opaque UVs while retaining exact skinning.
            opaque={}
            for index in set(visible):
                v=source['vertices'][index]
                if 3.8<abs(v[0])/source['scale']<5.4 and 7.7<(24-v[1])/source['scale']<9.3:
                    v=v.copy();v[6:8]=[.904541015625,.001708984375]
                    opaque[index]=len(mesh['vertices']);mesh['vertices'].append(v)
            visible=[opaque.get(index,index) for index in visible]
        material['count']=len(visible)
        mesh['indices'].extend(visible)
        mesh['materials'].append(material)
    scale=source['scale']
    skirt_bone=len(mesh['bones'])
    mesh['bones'].append(dict(name='summer_skirt',parent=13,position=[0,24-8.65*scale,0]))
    garment_vertices={tuple(v):i for i,v in enumerate(mesh['vertices'])}
    skin_seams={}
    blouse_bone=len(mesh['bones'])
    mesh['bones'].append(dict(name='summer_blouse',parent=12,position=[0,24-10.5*scale,0]))

    def add(part,bone,skirt=False,skin=False,elbow=None,blouse=False):
        start=len(mesh['indices'])
        g.smooth_normals(part)
        for quad in part['quads']:
            corners=[]
            for x,y,z,u,v,nx,ny,nz in quad:
                amount=min(1,max(0,(y+8.65)/3.65)) if skirt else 0
                amount=amount*amount*(3-2*amount)
                # Keep the back against the hips while the front clears bent knees.
                if skirt:amount*=.5-.5*max(-1,min(1,z/2.15))
                second=skirt_bone if skirt else -1
                if blouse:
                    amount=min(1,max(0,(y+10.8)/1.05))**2*.50
                    second=blouse_bone
                if elbow is not None:
                    amount=min(1,max(0,(abs(x)-3.0)/.85))
                    amount=amount*amount*(3-2*amount)
                    second=elbow
                if skin:u,v=.904541015625,.001708984375
                vertex=[x*scale,24+y*scale,z*scale,nx,ny,nz,u,v,
                    bone,second,-1,-1,1-amount,amount,0,0]
                if elbow is not None:
                    # Anchor the inner shoulder to the chest; blend into the
                    # upper arm instead of rotating an entire cylinder root.
                    shoulder=min(1,max(0,(abs(x)-1.15)/.95))
                    shoulder=shoulder*shoulder*(3-2*shoulder)
                    vertex[10]=12
                    vertex[12]=(1-amount)*shoulder
                    vertex[14]=(1-amount)*(1-shoulder)
                    if (x,y,z) in skin_seams:
                        vertex=skin_seams[x,y,z].copy()
                key=tuple(vertex)
                if key not in garment_vertices:
                    garment_vertices[key]=len(mesh['vertices']);mesh['vertices'].append(vertex)
                corners.append(garment_vertices[key])
            mesh['indices'].extend(corners[i] for i in (0,1,2,0,2,3))
        mesh['materials'].append(dict(name=part['name'],start=start,count=len(mesh['indices'])-start,
            color=[1,1,1,1],texture='bigfatfish:textures/entity/mmd/10.png' if skin else 'bigfatfish:textures/entity/character_atlas.png',double_sided=True))

    # The source omits skin hidden by the maid sleeves/bodice. Retaining its
    # visible skin material alone leaves detached hands and an empty torso.
    # Extend beneath the original shoulder/forearm boundaries, retaining hands.
    torso=g.node('summer_body_completion')
    g.torso(torso,[(-11.6,.43,.65),(-10.8,1.43,.91),(-10,1.55,1.14),
                   (-8.65,1.15,1.27),(-7.5,1.4,1.12)],g.SKIN)
    add(torso,12,skin=True)
    # Find the actual open forearm loops, welding coincident UV-seam vertices.
    # Joining these instead of capping a tube avoids a bulb/step at the wrist.
    skin_material=source['materials'][9]
    edges=Counter(); skin_indices={}
    for t in range(skin_material['start'],skin_material['start']+skin_material['count'],3):
        ids=source['indices'][t:t+3]
        points=[tuple(round(c,5) for c in source['vertices'][i][:3]) for i in ids]
        skin_indices.update(zip(points,ids))
        for j in range(3):edges[tuple(sorted((points[j],points[(j+1)%3])))]+=1
    neighbors=defaultdict(set)
    for (a,b),count in edges.items():
        if count==1:neighbors[a].add(b);neighbors[b].add(a)
    loops=[];seen=set()
    for p in neighbors:
        if p in seen:continue
        todo=[p];seen.add(p);loop=[]
        while todo:
            q=todo.pop();loop.append(skin_indices[q])
            for r in neighbors[q]-seen:seen.add(r);todo.append(r)
        loops.append(loop)
    for side,upper,elbow in ((1,19,24),(-1,49,54)):
        arm=g.node('summer_arm_completion_'+str(side))
        loop=next(loop for loop in loops if 4<sum(source['vertices'][i][0] for i in loop)/len(loop)/scale*side<4.5)
        end=[(source['vertices'][i][0]/scale,(source['vertices'][i][1]-24)/scale,source['vertices'][i][2]/scale) for i in loop]
        center=[sum(p[k] for p in end)/len(end) for k in range(3)]
        radial=(.574,-side*.819,0)
        def angle(p):return math.atan2(-side*(p[2]-center[2]),sum((p[k]-center[k])*radial[k] for k in range(3)))
        order=sorted(range(len(end)),key=lambda j:angle(end[j]))
        end=[end[j] for j in order];loop=[loop[j] for j in order]
        angles=[angle(p) for p in end]
        profiles=[(side*1.10,-10.83,.35,.28),(side*1.85,-10.38,.35,.42),
                  (side*2.65,-9.82,.35,.36),(side*3.43194,-9.27113,.35424,.285),
                  (*center,.30)]
        rows=[]
        for row in range(33):
            t=row/32
            x,y,z,r=g.catmull(profiles,t*(len(profiles)-1))
            blend=max(0,(t-.75)/.25);blend=blend*blend*(3-2*blend)
            ring=[]
            for p,a in zip(end,angles):
                delta=(r*math.cos(a)*radial[0],r*math.cos(a)*radial[1],-side*r*math.sin(a))
                ring.append(tuple(c+(1-blend)*d+blend*(p[k]-center[k]) for k,(c,d) in enumerate(zip((x,y,z),delta))))
            rows.append(ring)
        rows[-1]=end
        for p,i in zip(end,loop):
            v=source['vertices'][i].copy();v[6:8]=[.904541015625,.001708984375]
            skin_seams[tuple(round(c,5) for c in p)]=v
        for row in range(32):
            for j in range(len(end)):
                k=(j+1)%len(end)
                g.quad(arm,[rows[row][j],rows[row][k],rows[row+1][k],rows[row+1][j]],g.SKIN)
        add(arm,upper,skin=True,elbow=elbow)

    top=g.node('summer_sleeveless_blouse')
    # Front/back panels with actual armholes. A closed torso shell covers the
    # shoulder joint and clips the upper arm whenever it rotates forward/up.
    profiles=[(-11.52,.5,.75),(-10.9,1.59,1.04),(-10.3,1.73,1.31),
              (-9.85,1.68,1.34)]
    ruffles=g.node('summer_shoulder_ruffles')
    def cloth(t,a):
        y,rx,rz=g.catmull(profiles,t*(len(profiles)-1))
        # Small gathered folds build toward the free hem, not a spherical shell.
        fold=(.018+.065*t*t)*math.sin(9*a+.65*t)+.018*t*math.sin(15*a)
        return ((rx+fold)*math.sin(a),y+.045*t**5*math.cos(9*a),
                (rz+fold)*math.cos(a))
    for center in (0,math.pi):
        rows=[]
        for i in range(49):
            y,rx,rz=g.catmull(profiles,i/48*(len(profiles)-1))
            gap=.90*math.sqrt(max(0,1-((y+10.50)/.95)**2))
            rows.append([cloth(i/48,a)
                         for a in [center-math.pi/2+gap+j/24*(math.pi-2*gap) for j in range(25)]])
        for i in range(48):
            for j in range(24):
                midx=sum(p[0] for p in (rows[i][j],rows[i][j+1]))/2
                # Straight blue fabric stripes, independent of armhole curvature.
                material=g.CYAN if .62<abs(midx)<.84 else g.PLAIN_WHITE
                g.quad(top,[rows[i][j],rows[i][j+1],rows[i+1][j+1],rows[i+1][j]],material)
        # Flat binding follows the armhole, giving the edge the thickness of cloth.
        for j in (0,24):
            edge=[row[j] for row in rows]
            for i in range(6,37):
                p,q=edge[i],edge[i+1]
                def flounce(p,t):
                    width=.14+.065*math.cos(t*.95)
                    return (p[0]+math.copysign(width,p[0]),p[1]+.035*math.sin(t*.95),p[2]-.035)
                g.quad(ruffles,[p,q,flounce(q,i+1),flounce(p,i)],g.PLAIN_WHITE)
    for y,rx,rz in [(-11.54,.52,.77)]:
        g.tube(top,[(rx*math.sin(a*math.tau/48),y,rz*math.cos(a*math.tau/48)) for a in range(49)],
                [.025]*49,g.CYAN,48,6)
    for side in (-1,1):
        points=[(side*.10,-11.50,-.81),(side*.62,-11.34,-.98),
                (side*.53,-10.92,-1.15),(side*.14,-11.13,-1.14)]
        if side<0:points.reverse()
        g.quad(top,points,g.PLAIN_WHITE)
    # Sewn button placket follows the front surface; no floating buttons.
    for i in range(8,30):
        p=cloth(i/48,math.pi);q=cloth((i+1)/48,math.pi)
        g.quad(top,[(-.10,p[1],p[2]-.028),(.10,p[1],p[2]-.028),
                    (.10,q[1],q[2]-.028),(-.10,q[1],q[2]-.028)],g.CYAN)
    for t in (.24,.39,.54):
        x,y,z=cloth(t,math.pi)
        g.ellipsoid(top,(0,y,z-.046),(.045,.045,.022),g.BOW_BLUE,6,8)
    # A shallow doubled hem and scalloped folds separate the blouse from the belt.
    for j in range(80):
        a=j*math.tau/80;b=(j+1)*math.tau/80
        p=cloth(1,a);q=cloth(1,b)
        def hem(p,a):return (p[0]*1.018,p[1]+.07+.018*math.cos(9*a),p[2]*1.018)
        g.quad(top,[p,q,hem(q,b),hem(p,a)],g.PLAIN_WHITE)
    g.emblem(top,(.72,-10.25,-1.22),.19)
    add(top,12,blouse=True)
    add(ruffles,12,blouse=True)

    skirt=g.node('summer_pleated_skirt')
    g.skirt(skirt,-8.65,-5.0,3.05,2.15,(g.PLAIN_WHITE,g.PLAIN_WHITE,g.CYAN,g.PLAIN_WHITE),
            pleats=16,flare_start=.46,hem_wave=.045)
    g.torso(skirt,[(-8.84,1.4,1.38),(-8.53,1.46,1.4)],g.PLAIN_WHITE)
    # Reference skirt has a side ring and hanging ribbon, not two maid bows.
    ring=(-1.22,-8.55,-1.15)
    for radius,width,material in ((.25,.055,g.CYAN),(.18,.028,g.PLAIN_WHITE)):
        g.tube(skirt,[(ring[0]+radius*math.cos(a*math.tau/32),ring[1]+radius*math.sin(a*math.tau/32),ring[2]-.09)
                      for a in range(33)],[width]*33,material,32,8)
    for offset in (-.12,.15):
        g.quad(skirt,[(-1.30+offset,-8.38,-1.27),(-1.05+offset,-8.38,-1.27),
                      (-1.42+offset,-5.75,-2.0),(-1.75+offset,-5.95,-1.99)],g.CYAN if offset<0 else g.PLAIN_WHITE)
    add(skirt,13,skirt=True)

    for side,bone in ((-1,88),(1,92)):
        shoe=g.node('summer_sneaker_'+str(side));x=side*.895
        g.ellipsoid(shoe,(x,-.35,-.3),(.65,.27,1.12),g.PLAIN_WHITE,8,24)
        g.ellipsoid(shoe,(x,-.77,-.26),(.59,.59,1.01),g.PLAIN_WHITE,12,24)
        g.ellipsoid(shoe,(x,-.55,-1.13),(.42,.20,.20),g.BOW_BLUE,6,16)
        for y,z in ((-1.18,-.46),(-1.08,-.69),(-.94,-.89)):
            g.tube(shoe,[(x-.24,y,z),(x+.24,y,z)],[.035,.035],g.BOW_BLUE,2,6)
        add(shoe,bone)
        sock=g.node('summer_sock_'+str(side))
        g.torso(sock,[(-2.25,.47,.51),(-1.4,.51,.52),(-1.05,.48,.52)],g.PLAIN_WHITE)
        for q in sock['quads']:
            for v in q:v[0]+=x;v[2]+=.2
        add(sock,bone)

    band=g.node('summer_blue_headband')
    g.tube(band,[(2.82*math.cos(a*math.pi/64),-14.2-3.05*math.sin(a*math.pi/64),.2)
                for a in range(65)],[.095]*65,g.BOW_BLUE,64,8,depth=2)
    for side in (-1,1):
        g.bow(band,(side*2.78,-14.8,-.6),.23,g.BOW_BLUE)
    add(band,15)
    target=ASSETS/'models/entity/mmd_summer.mesh.json.gz'
    target.write_bytes(gzip.compress(json.dumps(mesh,separators=(',',':'),ensure_ascii=False).encode(),mtime=0))
    preview(mesh)
    print(f'MMD summer: {len(mesh["vertices"])} vertices; original face/hair/morphs, fitted body and summer garments.')


def preview(mesh):
    project=dict(meta=dict(format_version='5.0',model_format='free',box_uv=False),name='bigfatfish_mmd_summer',
        resolution=dict(width=128,height=128),elements=[],outliner=[],textures=[])
    textures={}
    for material in mesh['materials']:
        if material['color'][3]<=0:continue
        texture=material['texture']
        if texture not in textures:
            data=(ASSETS/texture.split(':')[1]).read_bytes();width,height=struct.unpack('>II',data[16:24])
            textures[texture]=len(project['textures'])
            project['textures'].append(dict(name=Path(texture).name,id=str(len(textures)-1),uuid=str(uuid.uuid4()),
                width=width,height=height,uv_width=width,uv_height=height,visible=True,internal=True,
                source='data:image/png;base64,'+base64.b64encode(data).decode()))
        tex=project['textures'][textures[texture]]
        ids=mesh['indices'][material['start']:material['start']+material['count']]
        vertices={str(i):[mesh['vertices'][i][0],24-mesh['vertices'][i][1],mesh['vertices'][i][2]] for i in set(ids)}
        faces={}
        for start in range(0,len(ids),3):
            tri=ids[start:start+3]
            faces[str(start//3)]=dict(vertices=[str(i) for i in reversed(tri)],texture=textures[texture],
                uv={str(i):[mesh['vertices'][i][6]*tex['width'],mesh['vertices'][i][7]*tex['height']] for i in tri})
        identity=str(uuid.uuid4())
        project['elements'].append(dict(name=material['name'],type='mesh',uuid=identity,origin=[0,0,0],rotation=[0,0,0],
            vertices=vertices,faces=faces,visibility=True))
        project['outliner'].append(identity)
    (ROOT/'build/bigfatfish_mmd_summer.bbmodel').write_text(json.dumps(project,separators=(',',':')),encoding='utf-8')


if __name__=='__main__':generate()
