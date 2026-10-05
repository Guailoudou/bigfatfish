"""Capture both pixel models and outfits in Blockbench without saving preview state."""
import base64
import json
from pathlib import Path

from blockbench_mcp import Blockbench
from blockbench_pose import POSE, RESTORE


def main():
    bb = Blockbench()
    output = Path(__file__).resolve().parents[1] / 'build'
    original = bb.call('get_project_info')['structuredContent']['project']['uuid']
    for age in ('adult', 'juvenile'):
        name = 'bigfatfish_pixel' + ('_juvenile' if age == 'juvenile' else '')
        bb.call('risky_eval', code=f'ModelProject.all.find(p=>p.name==={json.dumps(name)}).select();true')
        bb.call('risky_eval', code='window.pixelVisibility=[...Mesh.all,...Group.all].map(e=>[e.uuid,e.visibility]);true')
        try:
            baby = age == 'juvenile'
            center = 9 if baby else 13
            for skin in ('maid', 'summer'):
                code = '''(()=>{for(const g of Group.all){if(/^(maid|summer)_/.test(g.name))g.visibility=g.name.startsWith(SKIN+'_');}
                for(const m of Mesh.all){let p=m.parent,h=false;while(p&&p!=='root'){if(p.name&&/^(maid|summer)_/.test(p.name)&&!p.name.startsWith(SKIN+'_'))h=true;p=p.parent;}m.visibility=!h;}
                Canvas.updateAll();return true})()'''.replace('SKIN', json.dumps(skin))
                bb.call('risky_eval', code=code)
                views = [('front', [0, center, -45]), ('side', [45, center, 0]), ('back', [20, center + 5, 45])]
                views += [('sitting', [24, center + 6, -42]), ('clasp', [24, center + 6, -42])]
                for view, position in views:
                    posed = view in ('sitting', 'clasp')
                    try:
                        if posed:
                            bb.call('risky_eval', code=POSE.replace('POSE_NAME', repr(view)))
                        result = bb.call('set_camera_angle', position=position, target=[0, center, 0],
                                         projection='orthographic', zoom=.65 if baby else .47, max_size=1000)
                        for item in result.get('content', []):
                            if item['type'] == 'image':
                                path = output / f'pixel-{age}-{skin}-{view}.png'
                                path.write_bytes(base64.b64decode(item['data']))
                    finally:
                        if posed:
                            bb.call('risky_eval', code=RESTORE)
        finally:
            bb.call('risky_eval', code='for(const [id,v] of window.pixelVisibility){const e=[...Mesh.all,...Group.all].find(e=>e.uuid===id);if(e)e.visibility=v;}delete window.pixelVisibility;Canvas.updateAll();true')
    bb.call('risky_eval', code=f'ModelProject.all.find(p=>p.uuid==={json.dumps(original)}).select();true')
    print('Saved 20 Blockbench previews; restored geometry, poses and outfit visibility.')


if __name__ == '__main__':
    main()
