"""Export an editable Blockbench head, or the complete character with --full."""
import base64
import gzip
import json
from pathlib import Path
import struct
import uuid

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/bigfatfish'


def export_head(juvenile=False, overwrite=False, full=False):
    age = 'juvenile' if juvenile else 'adult'
    source = ASSETS / f'models/entity/{age}.mesh.json.gz'
    with gzip.open(source, 'rt', encoding='utf-8') as stream:
        model = json.load(stream)
    head = next(node for node in model['children'] if node['name'] == 'head')
    textures = []
    for name in ('character_atlas', 'face' if juvenile else 'adult_face'):
        raw = (ASSETS / f'textures/entity/{name}.png').read_bytes()
        width, height = struct.unpack('>II', raw[16:24])
        textures.append({'name': name, 'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, name)),
                         'width': width, 'height': height, 'uv_width': width, 'uv_height': height,
                         'source': 'data:image/png;base64,' + base64.b64encode(raw).decode(),
                         'mode': 'bitmap'})
    elements = []

    def convert(node, parent, path, parent_visible=True):
        path += '/' + node['name']
        visible = parent_visible and not node['name'].startswith('summer_')
        offset = [parent[i] + node['pose'][i] for i in range(3)]
        origin = [-offset[0], 24-offset[1], offset[2]]
        group = {'name': node['name'], 'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, path)),
                 'origin': origin, 'rotation': [0, 0, 0], 'children': [],
                 'visibility': visible, 'isOpen': False}
        if node['quads']:
            mesh_id = str(uuid.uuid5(uuid.NAMESPACE_URL, path + '/mesh'))
            positions, vertices, faces = {}, {}, {}
            texture = 1 if node['name'] == 'face' else 0
            width, height = textures[texture]['width'], textures[texture]['height']
            for index, quad in enumerate(node['quads']):
                keys, uv = [], {}
                for vertex in quad:
                    position = tuple(vertex[:3])
                    key = positions.setdefault(position, 'v' + str(len(positions)))
                    # Within one pole face the same position can carry two
                    # UV corners. Keep a second vertex there; ordinary seams
                    # across faces still share geometry and their own face UVs.
                    coordinates = [vertex[3]*width, vertex[4]*height]
                    if key in uv and uv[key] != coordinates:
                        key = positions.setdefault((position, tuple(vertex[3:5])), 'v' + str(len(positions)))
                    vertices[key] = [-position[0], -position[1], position[2]]
                    keys.append(key)
                    uv[key] = coordinates
                # Pole quads can contain repeated corners; Blockbench uses a triangle there.
                keys = list(dict.fromkeys(keys))
                if len({tuple(vertices[key]) for key in keys}) >= 3:
                    faces['f'+str(index)] = {'vertices': keys, 'uv': uv, 'texture': texture}
            elements.append({'name': node['name']+'_surface', 'uuid': mesh_id, 'type': 'mesh',
                             'origin': origin, 'rotation': [0, 0, 0], 'shading': 'smooth',
                             'visibility': visible, 'vertices': vertices, 'faces': faces})
            group['children'].append(mesh_id)
        # Head UUIDs match the head-only project, so replacing the full
        # project's head with an edited head does not remap its hierarchy.
        child_path = age if full and node is model else path
        group['children'].extend(convert(child, offset, child_path, visible) for child in node['children'])
        return group

    outliner = [convert(model if full else head, [0, 0, 0], age)]
    suffix = '' if full else '_head'
    project = {'meta': {'format_version': '4.10', 'model_format': 'free', 'box_uv': False},
               'name': f'bigfatfish_{age}{suffix}', 'resolution': {'width': 1024, 'height': 1024},
               'elements': elements, 'outliner': outliner, 'textures': textures,
               'ai_used': True, 'ai_agents': 'Codex'}
    target = ROOT / 'models' / f'{age}{suffix}.bbmodel'
    if target.exists() and not overwrite:
        raise FileExistsError(f'{target} already exists; use --overwrite only to replace editor work')
    target.parent.mkdir(exist_ok=True)
    target.write_text(json.dumps(project, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
    return target, project


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--juvenile', action='store_true')
    parser.add_argument('--overwrite', action='store_true')
    parser.add_argument('--full', action='store_true', help='Export the whole character, retaining all bones')
    args = parser.parse_args()
    target, project = export_head(args.juvenile,args.overwrite,args.full)
    print(f'{target}: {len(project["elements"])} meshes, '
          f'{sum(len(mesh["faces"]) for mesh in project["elements"])} faces')
