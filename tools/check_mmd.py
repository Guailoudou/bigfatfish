"""Check packaged MMD assets without requiring the licensed source archive."""
import gzip
import json
import math
from pathlib import Path

assets = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/bigfatfish'
mesh = json.loads(gzip.decompress((assets / 'models/entity/mmd.mesh.json.gz').read_bytes()))
assert mesh['format'] == 1
vertices, indices, bones = (mesh[key] for key in ('vertices', 'indices', 'bones'))
assert vertices and indices and len(indices) % 3 == 0
assert all(isinstance(i, int) and 0 <= i < len(vertices) for i in indices)
for i, bone in enumerate(bones):
    ancestors = {i}
    parent = bone['parent']
    while parent != -1:
        assert 0 <= parent < len(bones) and parent not in ancestors, 'Invalid bone hierarchy'
        ancestors.add(parent)
        parent = bones[parent]['parent']
for vertex in vertices:
    assert len(vertex) == 16 and all(math.isfinite(x) for x in vertex)
    assert abs(sum(vertex[12:16]) - 1) < 1e-5
    for bone, weight in zip(vertex[8:12], vertex[12:16]):
        assert weight >= 0
        assert weight == 0 or (isinstance(bone, int) and 0 <= bone < len(bones))
    assert abs(sum(x*x for x in vertex[3:6]) - 1) < .01, 'Non-unit source normal'
offset = 0
for material in mesh['materials']:
    assert material['start'] == offset and material['count'] % 3 == 0
    offset += material['count']
    namespace, texture = material['texture'].split(':')
    assert namespace == 'bigfatfish' and '..' not in Path(texture).parts
    assert (assets / texture).read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
assert offset == len(indices)
# Single-sided eyes must face outward after the PMX-to-Minecraft reflection.
# Missing iris surfaces otherwise expose the gray interior of the head.
eye = next(m for m in mesh['materials'] if m['texture'].endswith('/16.png'))
for start in range(eye['start'], eye['start'] + eye['count'], 3):
    p, q, r = (vertices[i] for i in indices[start:start+3])
    u, v = ([point[k] - p[k] for k in range(3)] for point in (q, r))
    cross = (u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0])
    assert sum(cross[k]*p[k+3] for k in range(3)) >= -1e-9, 'Inverted iris face'
for morph in mesh['morphs'].values():
    for index, *delta in morph:
        assert 0 <= index < len(vertices) and len(delta) == 3 and all(map(math.isfinite, delta))
for credit in ('MMD-CREDITS.txt', 'MMD-SOURCE-LICENSE.txt'):
    assert (assets / 'models/entity' / credit).stat().st_size > 0
print(f'MMD assets valid: {len(vertices)} weighted vertices, {len(indices)//3} triangles, {len(bones)} bones.')
summer=json.loads(gzip.decompress((assets/'models/entity/mmd_summer.mesh.json.gz').read_bytes()))
assert summer['vertices'][:len(vertices)]==vertices, 'Summer must preserve the original character geometry and skin weights'
assert summer['morphs']==mesh['morphs'] and summer['bones'][:len(bones)]==bones
assert summer['bones'][-1]['name']=='summer_skirt'
offset=0
for material in summer['materials']:
    assert material['start']==offset
    offset+=material['count']
    assert (assets/material['texture'].split(':')[1]).is_file()
assert offset==len(summer['indices'])
assert all(0<=i<len(summer['vertices']) for i in summer['indices'])
for vertex in summer['vertices'][len(vertices):]:
    assert len(vertex)==16 and all(map(math.isfinite,vertex))
    assert abs(sum(vertex[12:])-1)<1e-5
    assert all(w>=0 and (w==0 or 0<=b<len(summer['bones'])) for b,w in zip(vertex[8:12],vertex[12:]))
print('Summer MMD preserves original identity and adds weighted summer garments.')
