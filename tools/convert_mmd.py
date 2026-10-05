"""Convert the authorized PMX into indexed runtime data and a Blockbench rest-pose preview.

The runtime keeps original vertex weights/normals and facial morphs. The preview
is only for visual inspection; it is never used to recover skinning information.
"""
from pathlib import Path
import base64
import gzip
import json
import shutil
import struct
import uuid
from read_pmx import read

ROOT = Path(__file__).resolve().parents[1]


def convert(source):
    source = Path(source).resolve()
    model = read(source)
    assets = ROOT / 'src/main/resources/assets/bigfatfish'
    target = assets / 'models/entity/mmd.mesh.json.gz'
    textures = assets / 'textures/entity/mmd'
    textures.mkdir(parents=True, exist_ok=True)
    scale = 20.8 / 17.18  # Crown at 1.30 blocks; decorative ahoge extends above it.
    def position(p):
        return [round(p[0]*scale,6),round(24-p[1]*scale,6),round(p[2]*scale,6)]
    vertices = []
    for v in model['vertices']:
        vertices.append(position(v['position']) + [v['normal'][0],-v['normal'][1],v['normal'][2]]
                        + list(v['uv']) + (v['bones']+[-1]*4)[:4] + (v['weights']+[0.0]*4)[:4])
    # The Y reflection reverses front faces. Keep the original vertex normals
    # and reverse triangle winding so culling agrees with those normals.
    indices = [i for start in range(0,len(model['indices']),3)
               for i in (model['indices'][start],model['indices'][start+2],model['indices'][start+1])]
    runtime = dict(format=1,source=source.name,scale=scale,vertices=vertices,indices=indices,
                   bones=[dict(name=b['name'],parent=b['parent'],position=position(b['position'])) for b in model['bones']],
                   materials=[],morphs={})
    selected = ('Fcl_EYE_Close','Fcl_EYE_Joy','Fcl_MTH_A','Fcl_MTH_Joy','Fcl_BRW_Joy')
    for morph in model['morphs']:
        if morph['name'] in selected and morph['kind']==1:
            runtime['morphs'][morph['name']] = [[i,delta[0]*scale,-delta[1]*scale,delta[2]*scale] for i,delta in morph['offsets']]
    preview = dict(meta=dict(format_version='5.0',model_format='free',box_uv=False),name='bigfatfish_mmd',
                   resolution=dict(width=1024,height=1024),elements=[],outliner=[],textures=[])
    texture_indices = {}
    for index,name in enumerate(model['textures']):
        path=(source.parent/name.replace('\\','/')).resolve()
        if not path.is_relative_to(source.parent):
            raise ValueError('Texture escapes source folder')
        if path.suffix.lower()!='.png':
            raise ValueError('Expected an original PNG texture')
        data=path.read_bytes()
        width,height=struct.unpack('>II',data[16:24])
        filename=f'{index:02d}.png'
        shutil.copyfile(path,textures/filename)
        texture_indices[index]=len(preview['textures'])
        preview['textures'].append(dict(name=path.name,id=str(index),uuid=str(uuid.uuid4()),
            width=width,height=height,uv_width=width,uv_height=height,render_mode='default',render_sides='double',
            visible=True,internal=True,source='data:image/png;base64,'+base64.b64encode(data).decode()))
    start=0
    for index,mat in enumerate(model['materials']):
        runtime['materials'].append(dict(name=mat['name'],start=start,count=mat['count'],
            color=mat['diffuse'],texture=f'bigfatfish:textures/entity/mmd/{mat["texture"]:02d}.png',
            double_sided=bool(mat['flags']&1)))
        ids=model['indices'][start:start+mat['count']];start+=mat['count']
        if mat['diffuse'][3]<=0:
            continue
        points={str(i):list(model['vertices'][i]['position']) for i in set(ids)}
        # PMX and Blockbench both use Y-up. Reverse PMX's triangle winding.
        faces={}
        tex=preview['textures'][texture_indices[mat['texture']]]
        for n in range(0,len(ids),3):
            keys=[str(ids[n]),str(ids[n+2]),str(ids[n+1])]
            faces[str(n//3)]=dict(vertices=keys,texture=texture_indices[mat['texture']],
                uv={str(i):[model['vertices'][i]['uv'][0]*tex['width'],model['vertices'][i]['uv'][1]*tex['height']] for i in ids[n:n+3]})
        identity=str(uuid.uuid4())
        preview['elements'].append(dict(name=mat['name'],type='mesh',uuid=identity,origin=[0,0,0],rotation=[0,0,0],
            vertices=points,faces=faces,shading='smooth',visibility=True))
        preview['outliner'].append(identity)
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_bytes(gzip.compress(json.dumps(runtime,separators=(',',':'),ensure_ascii=False).encode(),mtime=0))
    (ROOT/'build/bigfatfish_mmd.bbmodel').write_text(json.dumps(preview,separators=(',',':'),ensure_ascii=False),encoding='utf-8')
    source_license=next(source.parent.glob('*.txt'))
    shutil.copyfile(source_license,assets/'models/entity/MMD-SOURCE-LICENSE.txt')
    print(f'Converted {len(vertices)} weighted vertices, {len(model["indices"])//3} triangles, {len(model["materials"])} materials.')
    print(f'Runtime: {target.relative_to(ROOT)} ({target.stat().st_size} bytes); Blockbench preview: build/bigfatfish_mmd.bbmodel')


if __name__=='__main__':
    import sys
    convert(sys.argv[1] if len(sys.argv)>1 else next((ROOT/'build/mmd_source').glob('*.pmx')))
