"""Native Minecraft geometry for rice: tapered leaves and drooping panicles.

Reuses the existing pixel palette; no renderer, animated ticker or new texture.
"""
import json
from pathlib import Path

ASSETS = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/bigfatfish'


def generate():
    for age in range(8):
        for part in range(3):
            elements = []
            green = [3.2, 15.2, 3.8, 15.8]
            grain = [4.2, 2.2, 4.8, 2.8]
            gold = [3.2, 2.2, 3.8, 2.8]

            def box(lo, hi, uv=green, texture='plant', rotation=None):
                element = dict(from_=lo, to=hi,
                    faces={side:dict(texture='#'+texture, uv=uv)
                           for side in ('down','up','north','south','east','west')})
                element['from'] = element.pop('from_')
                if rotation:
                    element['rotation'] = rotation
                elements.append(element)

            height = (5 + age*3) if part == 0 and age < 4 else 16
            if part == 1 and age < 7:
                height = 5 + max(0,age-4)*5
            if part == 2:
                height = 10
            for number, (x,z) in enumerate(((5,5),(10,7),(7,11))):
                top = height if part < 2 else height-number
                box([x-.25,0,z-.25],[x+.25,top+(2 if part==2 else 0),z+.25])
                # Each leaf tapers toward its tip. Opposing axes give volume
                # when viewed along a row, unlike the old flat cross sprite.
                for n in range(3 if age >= 3 and part<2 else 1):
                    axis = 'z' if (number+n)%2 else 'x'
                    base = max(1,top-3-n*5)
                    sign = 1 if (number+n)%2 else -1
                    rotation = dict(origin=[x,base,z],axis=axis,angle=45*sign)
                    length = 3 if age < 3 else 7
                    for section,width in enumerate((1.3,.85,.3)):
                        y0 = base+section*length/3
                        if axis == 'z':
                            lo,hi=[x-width/2,y0,z-.09],[x+width/2,y0+length/3,z+.09]
                        else:
                            lo,hi=[x-.09,y0,z-width/2],[x+.09,y0+length/3,z+width/2]
                        box(lo,hi,rotation=rotation)
                if part == 2 and age == 7:
                    # Stepped arc and paired grains form a bent, heavy seed head.
                    direction = -1 if number == 1 else 1
                    for segment in range(4):
                        px=x+direction*segment*.9
                        py=top+2-segment*segment*.28
                        box([min(px,px+direction*.9),py-.2,z-.15],
                            [max(px,px+direction*.9),py+.2,z+.15],gold,'grain')
                        if segment:
                            previous=top+2-(segment-1)**2*.28
                            box([px-.15,py-.2,z-.15],[px+.15,previous+.2,z+.15],gold,'grain')
                        for side in (-1,1):
                            gz=z+side*.6
                            box([px-.28,py-1.6,gz-.3],[px+.28,py-.35,gz+.3],grain,'grain',
                                dict(origin=[px,py,gz],axis='z',angle=22.5*direction))
            model={'ambientocclusion':False,
                   'textures':{'plant':f'bigfatfish:block/rice_{age}_{part}',
                               'grain':'bigfatfish:block/rice_7_2',
                               'particle':f'bigfatfish:block/rice_{age}_{part}'},
                   'elements':elements}
            path=ASSETS/f'models/block/rice_{age}_{part}.json'
            path.write_text(json.dumps(model,separators=(',',':'))+'\n',encoding='utf-8')
    print('Generated 24 staged rice models with tapered leaves and drooping grain.')


if __name__ == '__main__':
    generate()
