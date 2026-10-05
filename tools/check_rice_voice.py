"""Validate native rice geometry and bundled voice resources for CI."""
import json
import math
import struct
from pathlib import Path

assets=Path(__file__).resolve().parents[1]/'src/main/resources/assets/bigfatfish'
for age in range(8):
    for part in range(3):
        model=json.loads((assets/f'models/block/rice_{age}_{part}.json').read_text())
        assert 5<len(model['elements'])<100
        for element in model['elements']:
            for lo,hi in zip(element['from'],element['to']):
                assert math.isfinite(lo) and math.isfinite(hi) and -16<=lo<hi<=32
            assert set(element['faces'])=={'up','down','north','south','east','west'}
            if 'rotation' in element:
                assert element['rotation']['angle'] in (-45,-22.5,0,22.5,45)
        for texture in model['textures'].values():
            assert (assets/('textures/'+texture.split(':')[1]+'.png')).is_file()
sounds=json.loads((assets/'sounds.json').read_text())
languages=[json.loads((assets/f'lang/{lang}.json').read_text(encoding='utf-8')) for lang in ('en_us','zh_cn')]
for event in ('idle','beg','eat','hurt','death'):
    sound=sounds['entity.big_fat_fish.'+event]
    assert len(sound['sounds'])==(1 if event=='death' else 2)
    assert all(sound['subtitle'] in lang and '?' not in lang[sound['subtitle']] for lang in languages)
    for sample in sound['sounds']:
        data=(assets/('sounds/'+sample['name'].split(':')[1]+'.ogg')).read_bytes()
        assert data.startswith(b'OggS')
        header=data.index(b'\x01vorbis')
        assert data[header+11]==1, 'Positional voice must be mono'
        assert struct.unpack_from('<I',data,header+12)[0]==24000
print('Rice geometry, nine mono Vorbis samples, five events and both subtitle languages are valid.')
