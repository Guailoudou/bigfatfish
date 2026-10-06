"""Dress the original MMD character in the white/blue summer outfit.

Original face, hair, skin, morphs and weights are preserved. Missing skin beneath
the maid clothes is filled; garments use the existing summer atlas and helpers.
"""
import base64
import copy
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
        mesh['indices'].extend(source['indices'][start:start+material['count']])
        mesh['materials'].append(material)
    scale=source['scale']
    skirt_bone=len(mesh['bones'])
    mesh['bones'].append(dict(name='summer_skirt',parent=13,position=[0,24-8.65*scale,0]))
    garment_vertices={}

    def add(part,bone,skirt=False,skin=False,elbow=None):
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
                if elbow is not None:
                    amount=min(1,max(0,(abs(x)-3.0)/.85))
                    amount=amount*amount*(3-2*amount)
                    second=elbow
                if skin:u,v=.904541015625,.001708984375
                vertex=[x*scale,24+y*scale,z*scale,nx,ny,nz,u,v,
                    bone,second,-1,-1,1-amount,amount,0,0]
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
    g.torso(torso,[(-11.6,.43,.65),(-10.8,1.48,.94),(-10,1.7,1.25),
                   (-8.65,1.15,1.27),(-7.5,1.4,1.12)],g.SKIN)
    add(torso,12,skin=True)
    for side,upper,elbow in ((1,19,24),(-1,49,54)):
        arm=g.node('summer_arm_completion_'+str(side))
        g.tube(arm,[(side*1.30,-10.75,.35),(side*2.2,-10.14,.35),
                    (side*3.43194,-9.27113,.35424),(side*4.55,-8.49,.35)],
               [.50,.46,.38,.29],g.SKIN,sections=32,sides=24,cap_end=True)
        add(arm,upper,skin=True,elbow=elbow)

    top=g.node('summer_sleeveless_blouse')
    g.torso(top,[(-11.52,.5,.75),(-10.9,1.75,1.03),(-10.0,1.95,1.42),(-8.8,1.25,1.35)],g.PLAIN_WHITE)
    for y,rx,rz in [(-11.54,.52,.77),(-8.82,1.28,1.38)]:
        g.tube(top,[(rx*math.sin(a*math.tau/48),y,rz*math.cos(a*math.tau/48)) for a in range(49)],
               [.045]*49,g.CYAN,48,6)
    for side in (-1,1):
        points=[(side*.15,-11.48,-.76),(side*.6,-11.35,-.95),
                (side*.85,-10.82,-1.10),(side*.2,-11.08,-1.13)]
        if side<0:points.reverse()
        g.quad(top,points,g.PLAIN_WHITE)
    for y in (-10.95,-10.6,-10.25):
        g.ellipsoid(top,(0,y,-1.45),(.065,.065,.035),g.BOW_BLUE,6,8)
    g.emblem(top,(.8,-10.15,-1.40),.24)
    add(top,12)

    skirt=g.node('summer_pleated_skirt')
    g.skirt(skirt,-8.65,-5.0,3.05,2.15,(g.PLAIN_WHITE,g.PLAIN_WHITE,g.CYAN,g.PLAIN_WHITE),
            pleats=16,flare_start=.46,hem_wave=.045)
    g.torso(skirt,[(-8.84,1.4,1.38),(-8.53,1.46,1.4)],g.PLAIN_WHITE)
    for side in (-1,1):
        g.bow(skirt,(side*1.2,-8.50,-1.0),.29,g.SUMMER_BOW)
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
    print(f'MMD summer: {len(mesh["vertices"])} vertices; original face, hair, body and morphs unchanged.')


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
