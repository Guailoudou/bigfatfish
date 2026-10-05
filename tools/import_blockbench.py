"""Import the authoritative complete Blockbench character (default/--full).

Use --head with an explicit head-only project for a partial import.

Use --check to validate and report changes without writing a file. Rotations
must be applied to mesh vertices in Blockbench before export: the runtime bone
format stores translation only.
"""
import argparse
import copy
import gzip
import json
import math
import os
from pathlib import Path
import tempfile

from generate_character import smooth_normals, blend_adult_hair_roots

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'src/main/resources/assets/bigfatfish/models/entity/adult.mesh.json.gz'


def vector(value, length, label):
    if not isinstance(value, list) or len(value) != length:
        raise ValueError(f'{label}: expected {length} coordinates')
    if any(isinstance(x, bool) or not isinstance(x, (int, float)) or not math.isfinite(x) for x in value):
        raise ValueError(f'{label}: coordinates must be finite numbers')
    return value


def paths(node, parent=''):
    path = parent + '/' + node['name']
    result = {path: node}
    for child in node['children']:
        descendants = paths(child, path)
        if result.keys() & descendants.keys():
            raise ValueError(f'Duplicate bone path below {path}')
        result.update(descendants)
    return result


def hard_normals(node):
    """Keep each polygon's corners flat, including shared-position edges."""
    for quad in node['quads']:
        normal = [0.0, 0.0, 0.0]
        for j in (1, 2):
            a = [quad[j][i]-quad[0][i] for i in range(3)]
            b = [quad[j+1][i]-quad[0][i] for i in range(3)]
            normal[0] += a[1]*b[2]-a[2]*b[1]
            normal[1] += a[2]*b[0]-a[0]*b[2]
            normal[2] += a[0]*b[1]-a[1]*b[0]
        length = math.sqrt(sum(component*component for component in normal))
        if length <= 1e-10:
            raise ValueError(f'{node["name"]}: pixel model contains a zero-area face')
        normal = [component/length for component in normal]
        for vertex in quad:
            vertex[5:] = normal
    for child in node['children']:
        hard_normals(child)


def import_model(project, source, reblend_root_uv=False, full=False, juvenile=False, pixel=False):
    """Return a new model and validation report; never mutate either input."""
    originals = [n for n in source['children'] if n['name'] == 'head']
    if len(originals) != 1:
        raise ValueError('Source must have exactly one head bone')
    original = source if full else originals[0]
    old_paths = paths(original)
    textures = project.get('textures', [])
    if len(textures) != 2:
        raise ValueError('Keep texture 0 (atlas) and texture 1 (face) only')
    dimensions = []
    for index, texture in enumerate(textures):
        size = [texture.get('uv_width', texture.get('width')),
                texture.get('uv_height', texture.get('height'))]
        vector(size, 2, f'texture {index} dimensions')
        if min(size) <= 0:
            raise ValueError('Texture dimensions must be positive')
        dimensions.append(size)

    def indexed(entries, label):
        result = {}
        for entry in entries:
            identifier = entry.get('uuid')
            if not isinstance(identifier, str) or not identifier or identifier in result:
                raise ValueError(f'{label}: missing/duplicate UUID')
            result[identifier] = entry
        return result

    elements = indexed(project.get('elements', []), 'elements')
    groups = indexed(project.get('groups', []), 'groups')
    if elements.keys() & groups.keys():
        raise ValueError('Group and mesh UUIDs must be distinct')
    seen_groups, seen_meshes = set(), set()
    triangle_count = 0
    source_faces = {}

    def face_signature(vertices):
        return tuple(tuple(round(c, 8) for c in vertex[:5]) for vertex in vertices)

    def original_face(bone_path, quad):
        if bone_path not in source_faces:
            lookup = {}
            for source_quad in old_paths.get(bone_path, {}).get('quads', []):
                exported = {}
                for vertex in source_quad:
                    exported[tuple(vertex[:3])] = vertex[:5]
                lookup.setdefault(face_signature(exported.values()), source_quad)
            source_faces[bone_path] = lookup
        return source_faces[bone_path].get(face_signature(quad))

    def transform(node):
        for field, expected in [('rotation', [0, 0, 0]), ('scale', [1, 1, 1])]:
            value = vector(node.get(field, expected), 3, node.get('name', '') + ' ' + field)
            if any(abs(a-b) > 1e-8 for a, b in zip(value, expected)):
                raise ValueError(f'{node.get("name")}: apply {field} to vertices before importing')
        return vector(node.get('origin'), 3, node.get('name', '') + ' origin')

    def texture_index(value):
        if isinstance(value, int) and not isinstance(value, bool) and 0 <= value < 2:
            return value
        for index, texture in enumerate(textures):
            if value in (str(index), '#' + str(index), texture.get('uuid', object())):
                return index
        raise ValueError(f'Unknown or untextured face: {value!r}')

    def mesh_quads(mesh, group_origin, bone_name, bone_path):
        nonlocal triangle_count
        identifier = mesh['uuid']
        if identifier in seen_meshes:
            raise ValueError(f'Mesh referenced twice: {identifier}')
        seen_meshes.add(identifier)
        if mesh.get('type') != 'mesh':
            raise ValueError(f'{mesh.get("name")}: only mesh elements can be imported')
        origin = transform(mesh)
        vertices = mesh.get('vertices', {})
        for key, point in vertices.items():
            vector(point, 3, f'{mesh.get("name")} vertex {key}')
        result = []
        for key, face in mesh.get('faces', {}).items():
            corners = face.get('vertices', [])
            if len(corners) not in (3, 4) or len(set(corners)) != len(corners):
                raise ValueError(f'{mesh.get("name")} face {key}: expected distinct triangle/quad vertices')
            texture = texture_index(face.get('texture'))
            if texture != (1 if bone_name == 'face' else 0):
                raise ValueError(f'{bone_name}: face bone must use face texture; other bones must use atlas')
            width, height = dimensions[texture]
            quad = []
            for corner in corners:
                if corner not in vertices or corner not in face.get('uv', {}):
                    raise ValueError(f'{mesh.get("name")} face {key}: missing vertex or UV {corner}')
                point = vertices[corner]
                uv = vector(face['uv'][corner], 2, f'face {key} UV')
                # BB vertices are mesh-local; origins are absolute BB positions.
                bb = [point[i] + origin[i] - group_origin[i] for i in range(3)]
                quad.append([-bb[0], -bb[1], bb[2], uv[0]/width, uv[1]/height])
            # The exporter welds positions, so repeated pole corners can lose
            # distinct UVs in Blockbench's per-vertex face UV dictionary. For
            # an unchanged original face, recover those corners from source.
            # Match geometry/UV rather than fN: discarded degenerate faces
            # change runtime array indices after the first import.
            source_quad = original_face(bone_path, quad)
            if source_quad is not None:
                result.append([vertex[:5] for vertex in source_quad])
                if len(quad) == 3:
                    triangle_count += 1
                continue
            if len(quad) == 3:
                triangle_count += 1
                quad.append(quad[-1].copy())
            result.append(quad)
        return result

    def resolve_group(entry):
        if isinstance(entry, str):
            if entry not in groups:
                raise ValueError(f'Unknown group UUID {entry}')
            return groups[entry], groups[entry].get('children', [])
        if not isinstance(entry, dict):
            raise ValueError('Invalid outliner entry')
        identifier = entry.get('uuid')
        # 5.x stores attributes in groups and nesting in outliner records.
        node = dict(groups.get(identifier, {}))
        node.update(entry)
        return node, entry.get('children', node.get('children', []))

    def convert(entry, parent_origin, parent_path=''):
        group, children = resolve_group(entry)
        identifier = group.get('uuid')
        if not isinstance(identifier, str) or identifier in seen_groups:
            raise ValueError('Missing, cyclic or repeated group UUID')
        seen_groups.add(identifier)
        name = group.get('name')
        if not isinstance(name, str) or not name or '/' in name:
            raise ValueError('Bone names must be nonempty and cannot contain /')
        if name.startswith('hair_') and not name[5:].isdigit():
            raise ValueError('Animated hair bones must retain numeric hair_N names')
        origin = transform(group)
        bone_path = parent_path + '/' + name
        delta = [origin[i]-parent_origin[i] for i in range(3)]
        node = {'name': name, 'pose': [-delta[0], -delta[1], delta[2]], 'quads': [], 'children': []}
        for child in children:
            mesh_id = child if isinstance(child, str) else child.get('uuid') if isinstance(child, dict) else None
            if mesh_id in elements:
                node['quads'].extend(mesh_quads(elements[mesh_id], origin, name, bone_path))
            else:
                node['children'].append(convert(child, origin, bone_path))
        return node

    outliner = project.get('outliner', [])
    if len(outliner) != 1:
        raise ValueError('Project must have exactly one top-level group')
    imported = convert(outliner[0], [0, 24, 0])
    if imported['name'] != original['name']:
        raise ValueError(f'Top-level bone must be {original["name"]}')
    if pixel and full and any(abs(value) > 1e-8 for value in imported['pose']):
        raise ValueError('Pixel root pose must be zero: set the Blockbench root origin to [0, 24, 0] '
                         'without moving world geometry; body and face use separate render passes')
    if seen_meshes != elements.keys() or groups.keys() - seen_groups:
        raise ValueError('Unreferenced meshes/groups would be lost; restore them to the outliner')
    new_paths = paths(imported)
    missing = old_paths.keys() - new_paths.keys()
    if missing:
        raise ValueError('Required bone names/hierarchy were removed: ' + ', '.join(sorted(missing)))
    head_path = '/' + source['name'] + '/head' if full else '/head'
    if head_path+'/face' not in new_paths or not new_paths[head_path+'/face']['quads']:
        raise ValueError('The direct head/face bone must retain its geometry')
    for path, node in new_paths.items():
        if not pixel and node['name'].startswith('hair_') and not node['quads']:
            raise ValueError(f'{path}: animated hair cannot be empty')
    # Recompute only the imported subtree; head-only imports leave the body intact.
    if pixel:
        hard_normals(imported)
    else:
        smooth_normals(imported)
    imported_uvs = [[vertex[3:5] for quad in node['quads'] for vertex in quad]
                    for node in new_paths.values()]
    if not juvenile and not pixel:
        blend_adult_hair_roots({'children': [new_paths[head_path]]})
    # Root blending also paints UVs and is not idempotent. Keep the editor's
    # mapping by default while retaining the freshly blended root normals.
    if not reblend_root_uv:
        for node, uvs in zip(new_paths.values(), imported_uvs):
            for vertex, uv in zip((v for q in node['quads'] for v in q), uvs):
                vertex[3:5] = uv
    for path, node in new_paths.items():
        for quad in node['quads']:
            for vertex in quad:
                vector(vertex, 8, path + ' imported vertex')
    if full:
        model = copy.deepcopy(source)
        model.update(imported)
    else:
        model = copy.deepcopy(source)
        model['children'][source['children'].index(original)] = imported
    report = {'bones': len(new_paths), 'added_bones': sorted(new_paths.keys()-old_paths.keys()),
              'source_quads': sum(len(n['quads']) for n in old_paths.values()),
              'imported_quads': sum(len(n['quads']) for n in new_paths.values()),
              'triangles': triangle_count, 'meshes': len(seen_meshes),
              'reblended_root_uv': reblend_root_uv and not juvenile and not pixel,
              'full': full, 'juvenile': juvenile, 'pixel': pixel}
    return model, report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('project', nargs='?', type=Path)
    parser.add_argument('--source', type=Path)
    scope = parser.add_mutually_exclusive_group()
    scope.add_argument('--full', dest='full', action='store_true', help='Import the complete model (default)')
    scope.add_argument('--head', dest='full', action='store_false', help='Import only an explicitly supplied head project')
    parser.set_defaults(full=True)
    parser.add_argument('--juvenile', action='store_true', help='Use juvenile paths and skip adult root blending')
    parser.add_argument('--pixel', action='store_true',
                        help='Keep hard face normals and editor UVs; allow unused empty hair bones')
    parser.add_argument('--output', type=Path, help='Defaults to --source; written atomically')
    parser.add_argument('--check', action='store_true', help='Validate without writing any file')
    parser.add_argument('--reblend-root-uv', action='store_true',
                        help='Repaint root UV blends as well as normals; normally retain editor UVs')
    args = parser.parse_args()
    if not args.full and args.project is None:
        parser.error('--head requires an explicit head-only project path; the maintained sources are full models')
    age = 'juvenile' if args.juvenile else 'adult'
    if args.project is None:
        args.project = ROOT / 'models' / ('bigfatfish_pixel_juvenile.bbmodel' if args.juvenile else 'bigfatfish_pixel.bbmodel')
        args.pixel = True
    args.source = args.source or SOURCE.with_name(f'{age}.mesh.json.gz')
    project = json.loads(args.project.read_text(encoding='utf-8'))
    with gzip.open(args.source, 'rt', encoding='utf-8') as stream:
        source = json.load(stream)
    model, report = import_model(project, source, args.reblend_root_uv, args.full, args.juvenile, args.pixel)
    print(json.dumps(report, ensure_ascii=False))
    if args.check:
        print('Validation passed; no files written')
        return
    output = args.output or args.source
    raw = json.dumps(model, separators=(',', ':'), ensure_ascii=False).encode('utf-8')
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=output.parent, suffix='.tmp', delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(gzip.compress(raw, mtime=0))
        os.replace(temporary, output)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()
    print(f'Imported {"model" if args.full else "head"} into {output}')


if __name__ == '__main__':
    main()
