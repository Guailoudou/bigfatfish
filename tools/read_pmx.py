"""Read PMX geometry, skin weights, materials and bones without a DCC dependency."""
import struct
from pathlib import Path


class Reader:
    def __init__(self, data):
        self.data, self.at = data, 0

    def take(self, size):
        result = self.data[self.at:self.at+size]
        if len(result) != size:
            raise ValueError('Truncated PMX')
        self.at += size
        return result

    def unpack(self, fmt):
        return struct.unpack('<'+fmt, self.take(struct.calcsize('<'+fmt)))

    def integer(self):
        return self.unpack('i')[0]

    def text(self):
        size = self.integer()
        if size < 0:
            raise ValueError('Negative PMX string size')
        return self.take(size).decode(self.encoding)

    def index(self, kind):
        size = self.sizes[kind]
        return self.unpack(({1:'B', 2:'H', 4:'I'} if kind == 'vertex' else {1:'b', 2:'h', 4:'i'})[size])[0]


def read(path):
    r = Reader(Path(path).read_bytes())
    if r.take(4) != b'PMX ':
        raise ValueError('Not a PMX file')
    version = r.unpack('f')[0]
    header = r.take(r.unpack('B')[0])
    r.encoding = 'utf-16-le' if header[0] == 0 else 'utf-8'
    r.sizes = dict(zip(('vertex','texture','material','bone','morph','rigid'), header[2:8]))
    model = dict(version=version, name=r.text(), english_name=r.text(), comment=r.text(), english_comment=r.text())
    vertices = []
    for _ in range(r.integer()):
        position, normal, uv = r.unpack('3f'), r.unpack('3f'), r.unpack('2f')
        r.take(header[1]*16)
        kind = r.unpack('B')[0]
        if kind == 0:
            bones, weights = [r.index('bone')], [1.0]
        elif kind in (1,3):
            bones = [r.index('bone'),r.index('bone')]
            weight = r.unpack('f')[0]
            weights = [weight,1-weight]
            if kind == 3:
                r.take(36)
        elif kind in (2,4):
            bones, weights = [r.index('bone') for _ in range(4)], list(r.unpack('4f'))
        else:
            raise ValueError(f'Unsupported PMX weight type {kind}')
        edge = r.unpack('f')[0]
        vertices.append(dict(position=position,normal=normal,uv=uv,bones=bones,weights=weights,weight_type=kind,edge=edge))
    count = r.integer()
    if count % 3:
        raise ValueError('PMX triangle index count is not divisible by three')
    indices = [r.index('vertex') for _ in range(count)]
    textures = [r.text() for _ in range(r.integer())]
    materials = []
    for _ in range(r.integer()):
        material = dict(name=r.text(),english_name=r.text(),diffuse=r.unpack('4f'),specular=r.unpack('3f'),
                        specular_power=r.unpack('f')[0],ambient=r.unpack('3f'),flags=r.unpack('B')[0],
                        edge_color=r.unpack('4f'),edge_size=r.unpack('f')[0],texture=r.index('texture'),sphere=r.index('texture'),
                        sphere_mode=r.unpack('B')[0])
        shared = r.unpack('B')[0]
        material.update(shared_toon=shared,toon=r.unpack('B')[0] if shared else r.index('texture'),comment=r.text(),count=r.integer())
        materials.append(material)
    bones = []
    for _ in range(r.integer()):
        bone = dict(name=r.text(),english_name=r.text(),position=r.unpack('3f'),parent=r.index('bone'),layer=r.integer(),flags=r.unpack('H')[0])
        flags = bone['flags']
        bone['tail'] = r.index('bone') if flags & 1 else r.unpack('3f')
        if flags & 0x300:
            bone['inherit'] = (r.index('bone'),r.unpack('f')[0])
        if flags & 0x400:
            bone['axis'] = r.unpack('3f')
        if flags & 0x800:
            bone['local_axes'] = r.unpack('6f')
        if flags & 0x2000:
            bone['external_parent'] = r.integer()
        if flags & 0x20:
            target, loops, angle = r.index('bone'), r.integer(), r.unpack('f')[0]
            links = []
            for _ in range(r.integer()):
                index, limited = r.index('bone'), r.unpack('B')[0]
                links.append((index,r.unpack('6f') if limited else None))
            bone['ik'] = dict(target=target,loops=loops,angle=angle,links=links)
        bones.append(bone)
    morphs = []
    for _ in range(r.integer()):
        morph = dict(name=r.text(),english_name=r.text(),panel=r.unpack('B')[0],kind=r.unpack('B')[0])
        offsets = []
        for _ in range(r.integer()):
            kind = morph['kind']
            if kind in (0,9):
                offsets.append((r.index('morph'),r.unpack('f')[0]))
            elif kind == 1:
                offsets.append((r.index('vertex'),r.unpack('3f')))
            elif kind == 2:
                offsets.append((r.index('bone'),r.unpack('7f')))
            elif 3 <= kind <= 7:
                offsets.append((r.index('vertex'),r.unpack('4f')))
            elif kind == 8:
                offsets.append((r.index('material'),r.unpack('B')[0],r.unpack('28f')))
            elif kind == 10:
                offsets.append((r.index('rigid'),r.unpack('B')[0],r.unpack('6f')))
            else:
                raise ValueError(f'Unsupported PMX morph type {kind}')
        morph['offsets'] = offsets
        morphs.append(morph)
    if sum(m['count'] for m in materials) != len(indices):
        raise ValueError('Material triangle ranges do not cover the mesh')
    if any(i < 0 or i >= len(vertices) for i in indices):
        raise ValueError('Vertex index outside geometry')
    for vertex in vertices:
        if abs(sum(vertex['weights'])-1) > 1e-4:
            raise ValueError('Unnormalized PMX weights')
        if any(w > 0 and not 0 <= b < len(bones) for b,w in zip(vertex['bones'],vertex['weights'])):
            raise ValueError('Weighted bone index outside skeleton')
    model.update(vertices=vertices,indices=indices,textures=textures,materials=materials,bones=bones,morphs=morphs)
    return model


if __name__ == '__main__':
    import json, sys
    model = read(sys.argv[1])
    summary = {k:len(model[k]) for k in ('vertices','indices','textures','materials','bones','morphs')}
    summary.update(name=model['name'],bone_names=[b['name'] for b in model['bones']],morph_names=[m['name'] for m in model['morphs']])
    print(json.dumps(summary,ensure_ascii=False,indent=2))
