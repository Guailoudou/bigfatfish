"""Validate pixel import regressions and the authoritative BB/runtime assets."""
import copy
import gzip
import json
import math
import struct
from pathlib import Path
import import_blockbench as importer


def check():
    source = {'name': 'root', 'pose': [0, 0, 0], 'quads': [], 'children': [
        {'name': 'head', 'pose': [0, 0, 0], 'quads': [], 'children': [
            {'name': name, 'pose': [0, 0, 0], 'quads': [], 'children': []}
            for name in ('face', 'hair_0')]}]}
    vertices = {'a': [0, 0, 0], 'b': [1, 0, 0], 'c': [1, 1, 0],
                'd': [0, 1, 0], 'e': [0, 0, 1], 'f': [1, 0, 1]}
    def face(corners, texture):
        return {'vertices': corners, 'texture': texture,
                'uv': {corner: uv for corner, uv in zip(corners, [[0, 0], [128, 0], [128, 128], [0, 128]])}}
    project = {'resolution': {'width': 16, 'height': 16},
               'textures': [{'uv_width': 128, 'uv_height': 128} for _ in range(2)],
               'elements': [
                   {'uuid': 'corner', 'name': 'corner', 'type': 'mesh', 'origin': [0, 24, 0],
                    'vertices': vertices, 'faces': {'xy': face(['a', 'b', 'c', 'd'], 0),
                                                  'xz': face(['a', 'e', 'f', 'b'], 0)}},
                   {'uuid': 'face_plane', 'name': 'face_plane', 'type': 'mesh', 'origin': [0, 24, 0],
                    'vertices': vertices, 'faces': {'xy': face(['a', 'b', 'c', 'd'], 1)}}],
               'outliner': [{'uuid': 'r', 'name': 'root', 'origin': [0, 24, 0], 'children': [
                   {'uuid': 'h', 'name': 'head', 'origin': [0, 24, 0], 'children': ['corner',
                       {'uuid': 'f', 'name': 'face', 'origin': [0, 24, 0], 'children': ['face_plane']},
                       {'uuid': 'hair', 'name': 'hair_0', 'origin': [0, 24, 0], 'children': []}]}]}]}
    original_project, original_source = copy.deepcopy(project), copy.deepcopy(source)
    model, report = importer.import_model(project, source, full=True, pixel=True, reblend_root_uv=True)
    head = model['children'][0]
    xy, xz = head['quads']
    assert xy[0][:3] == xz[0][:3], 'Fixture must contain a shared geometric corner'
    assert all(v[5:] == [0, 0, 1] for v in xy), 'XY face lost its flat normal'
    assert all(v[5:] == [0, -1, 0] for v in xz), 'Hard edge was smoothed across faces'
    assert xy[2][3:5] == [1, 1], 'UVs must use 128px texture dimensions, not project resolution'
    assert all(v[5:] == [0, 0, 1] for v in head['children'][0]['quads'][0]), 'Face acquired legacy lighting bias'
    assert head['children'][1]['quads'] == [], 'Empty animation placeholder was not retained'
    assert report['pixel'] and not report['reblended_root_uv'], 'Legacy root blending applied'
    assert project == original_project and source == original_source, 'Import mutated its inputs'
    misplaced = copy.deepcopy(project)
    misplaced['outliner'][0]['origin'] = [0, 0, 0]
    try:
        importer.import_model(misplaced, source, full=True, pixel=True)
    except ValueError as error:
        assert 'root pose must be zero' in str(error)
    else:
        raise AssertionError('Nonzero root would misalign body and face render passes')
    for node in importer.paths(model).values():
        for quad in node['quads']:
            assert all(len(v) == 8 and math.isfinite(sum(v)) for v in quad)
    try:
        importer.import_model(project, source, full=True)
    except ValueError as error:
        assert 'animated hair cannot be empty' in str(error)
    else:
        raise AssertionError('Non-pixel validation changed')
    print('Pixel import checks passed: hard edges, face normals, 128px UVs, empty bones, zero root, immutable inputs and legacy validation.')


def check_assets():
    root = Path(__file__).resolve().parents[1]
    assets = root / 'src/main/resources/assets/bigfatfish'
    required = {'/root', '/root/head', '/root/head/hat', '/root/head/face',
                '/root/head/ahoge', '/root/head/maid_head', '/root/head/summer_head',
                '/root/body', '/root/body/tail', '/root/body/maid_body', '/root/body/summer_body'}
    required.update(f'/root/head/hair_{i}' for i in range(29))
    nonempty = {'/root/head/face', '/root/head/ahoge', '/root/head/maid_head',
                '/root/body/tail', '/root/body/maid_body', '/root/body/summer_body'}
    for side in ('left', 'right'):
        arm, leg = f'/root/{side}_arm', f'/root/{side}_leg'
        required.update((arm, arm+'/forearm', arm+'/forearm/hand', arm+'/maid_arm',
                         arm+'/forearm/maid_forearm', arm+'/summer_arm', leg, leg+'/shin',
                         leg+'/maid_leg', leg+'/shin/maid_shin', leg+'/shin/summer_shin',
                         leg+'/shin/maid_shin/shoe', leg+'/shin/summer_shin/shoe'))
        # Summer has bare arms and no head accessory; their required placeholders may be empty.
        nonempty.update((arm+'/forearm/hand', arm+'/maid_arm', arm+'/forearm/maid_forearm',
                         leg+'/maid_leg', leg+'/shin/maid_shin', leg+'/shin/summer_shin'))

    def count_faces(node):
        return len(node['quads']) + sum(count_faces(child) for child in node['children'])

    for age, filename in (('adult', 'bigfatfish_pixel.bbmodel'),
                          ('juvenile', 'bigfatfish_pixel_juvenile.bbmodel')):
        project = json.loads((root / 'models' / filename).read_text(encoding='utf-8'))
        assert len(project['textures']) == 2
        assert all((t.get('uv_width'), t.get('uv_height')) == (128, 128)
                   for t in project['textures']), f'{age}: BB texture dimensions'
        with gzip.open(assets / 'models/entity' / f'{age}.mesh.json.gz', 'rt', encoding='utf-8') as stream:
            runtime = json.load(stream)
        imported, report = importer.import_model(project, runtime, full=True,
                                                 juvenile=age=='juvenile', pixel=True)
        actual, expected = importer.paths(runtime), importer.paths(imported)
        assert actual.keys() == expected.keys(), f'{age}: BB/runtime bone hierarchy differs'
        assert required <= actual.keys(), f'{age}: missing render/animation bones {required-actual.keys()}'
        assert all(abs(v) < 1e-8 for v in runtime['pose']), f'{age}: root offset misaligns face'
        for path in nonempty:
            assert count_faces(actual[path]) > 0, f'{age}: missing outfit/character geometry {path}'
        for path, node in actual.items():
            reference = expected[path]
            assert len(node['pose']) == 3 and max(abs(a-b) for a,b in zip(node['pose'],reference['pose'])) < 1e-6, f'{age}: pose drift {path}'
            assert len(node['quads']) == len(reference['quads']), f'{age}: face count drift {path}'
            for index, (quad, reference_quad) in enumerate(zip(node['quads'], reference['quads'])):
                assert len(quad) == 4, f'{age}: invalid quad {path}/{index}'
                for vertex, reference_vertex in zip(quad, reference_quad):
                    assert len(vertex) == 8 and all(math.isfinite(v) for v in vertex), f'{age}: invalid vertex'
                    assert max(abs(a-b) for a,b in zip(vertex, reference_vertex)) < 1e-6, f'{age}: BB/runtime geometry, UV or normal drift {path}/{index}'
                    assert all(-1e-7 <= v <= 1+1e-7 for v in vertex[3:5]), f'{age}: UV outside atlas'
                    assert abs(sum(v*v for v in vertex[5:])-1) < 1e-6, f'{age}: nonunit normal'
                    assert max(abs(a-b) for a,b in zip(vertex[5:],quad[0][5:])) < 1e-6, f'{age}: face contains smoothed normals'
        print(f'{age}: BB/runtime match; {report["bones"]} bones, {report["imported_quads"]} hard faces; both outfits valid.')

    for name in ('character_atlas', 'adult_face', 'adult_face_closed', 'face', 'face_closed'):
        image = assets / 'textures/entity' / f'{name}.png'
        header = image.read_bytes()[:24]
        assert header[:8] == b'\x89PNG\r\n\x1a\n' and header[12:16] == b'IHDR', f'{name}: invalid PNG'
        assert struct.unpack('>II', header[16:24]) == (128,128), f'{name}: expected 128px PNG'
        metadata = json.loads(image.with_suffix('.png.mcmeta').read_text(encoding='utf-8'))
        assert metadata.get('texture', {}).get('blur') is False, f'{name}: pixel filtering must disable blur'
    print('Five runtime PNGs: 128x128 with blur disabled.')


if __name__ == '__main__':
    check()
    check_assets()
