"""Generate original pixel textures and Minecraft 26.3 JSON resources; Python stdlib only."""
from pathlib import Path
import json, struct, zlib

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
ASSETS = ROOT / 'assets/bigfatfish'
DATA = ROOT / 'data/bigfatfish'

def js(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def png(path, w, h, pixels):
    def chunk(kind, data):
        return struct.pack('!I', len(data)) + kind + data + struct.pack('!I', zlib.crc32(kind + data) & 0xffffffff)
    raw = b''.join(b'\0' + bytes(sum(pixels[y*w:(y+1)*w], ())) for y in range(h))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('!2I5B', w,h,8,6,0,0,0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b''))

def canvas(w, h):
    pixels = [(0,0,0,0)] * (w*h)
    def rect(x1,y1,x2,y2,color):
        for y in range(max(0,y1),min(h,y2)):
            for x in range(max(0,x1),min(w,x2)): pixels[y*w+x] = tuple(color) + (255,) if len(color)==3 else tuple(color)
    return pixels, rect

def main():
    items = ['paddy','rice','cooked_rice','stone_mill','big_fat_fish_spawn_egg']
    for name in items:
        js(ASSETS / f'items/{name}.json', {'model':{'type':'minecraft:model','model':f'bigfatfish:item/{name}'}})
        if name != 'stone_mill':
            js(ASSETS / f'models/item/{name}.json', {'parent':'minecraft:item/generated','textures':{'layer0':f'bigfatfish:item/{name}'}})
    for name in ['paddy','rice','cooked_rice','big_fat_fish_spawn_egg']:
        pix, r = canvas(16,16)
        if name in ['paddy','rice']:
            for x,y in [(4,4),(8,2),(11,6),(5,10),(9,11)]:
                r(x,y,x+2,y+4,(186,137,56) if name=='paddy' else (241,231,203))
                r(x,y,x+1,y+3,(234,192,92) if name=='paddy' else (255,250,232))
        elif name=='cooked_rice':
            r(2,7,14,10,(98,62,37)); r(3,10,13,13,(133,84,44)); r(5,13,11,14,(83,52,35))
            r(3,5,13,8,(235,228,205)); r(5,3,11,6,(255,250,237))
            for x,y in [(4,6),(7,4),(10,5),(8,7)]: r(x,y,x+1,y+1,(204,193,169))
        else:
            for y in range(2,15):
                for x in range(3,13):
                    if ((x-7.5)/4.8)**2 + ((y-8)/6.5)**2 < 1:
                        r(x,y,x+1,y+1,(53,91,171) if (x+y)%4 else (218,229,255))
            r(5,6,7,8,(116,190,227)); r(9,9,11,11,(116,190,227))
        png(ASSETS / f'textures/item/{name}.png',16,16,pix)

    for age in range(8):
        for part in range(3):
            pix,r=canvas(16,16)
            green=(92,140,49) if age<7 else (147,150,46)
            height=4+age if part==0 else 16
            for x in [3,7,11]:
                r(x,16-height,x+1,16,green)
                r(x-2,16-height+4,x,16-height+5,green)
                r(x+1,16-height+7,x+3,16-height+8,green)
                if age==7 and part==2:
                    r(x,1,x+1,7,(198,155,54)); r(x+1,2,x+3,6,(222,185,72))
            texture=f'rice_{age}_{part}'
            png(ASSETS / f'textures/block/{texture}.png',16,16,pix)
            js(ASSETS / f'models/block/{texture}.json',{'parent':'minecraft:block/cross','textures':{'cross':f'bigfatfish:block/{texture}'}})
    js(ASSETS / 'blockstates/rice_crop.json', {'variants':{f'age={a},part={p}':{'model':f'bigfatfish:block/rice_{a}_{p}'} for a in range(8) for p in range(3)}})
    faces={d:{'texture':'#stone'} for d in ['down','up','north','south','west','east']}
    wood={d:{'texture':'#wood'} for d in faces}
    js(ASSETS / 'models/block/stone_mill.json',{'textures':{'stone':'minecraft:block/stone','wood':'minecraft:block/oak_log','particle':'minecraft:block/stone'},'elements':[
        {'from':[1,0,1],'to':[15,5,15],'faces':faces},
        {'from':[2,5,2],'to':[14,11,14],'faces':faces},
        {'from':[7,11,7],'to':[9,13,9],'faces':wood},
        {'from':[8,12,7],'to':[15,14,9],'faces':wood}]})
    js(ASSETS / 'blockstates/stone_mill.json',{'variants':{'':{'model':'bigfatfish:block/stone_mill'}}})
    js(ASSETS / 'models/item/stone_mill.json',{'parent':'bigfatfish:block/stone_mill'})

    pix,r=canvas(256,128)
    palette=[(0,0,(250,218,207)),(64,0,(52,87,160)),(128,0,(241,237,248)),(192,0,(32,39,79)),(0,64,(251,224,213)),(64,64,(203,168,90)),(128,64,(97,178,214)),(192,64,(241,237,248))]
    for x,y,c in palette:
        r(x,y,x+64,y+64,c)
        for yy in range(y,y+64):
            for xx in range(x,x+64):
                delta=((xx*7+yy*11)%9)-4
                color=c
                if x==64 and y==0:
                    delta+=(xx%7)*2
                    blend=max(0,min(1,(yy-10)/14))
                    color=tuple(int(a+(b-a)*blend) for a,b in zip(c,(70,162,201)))
                r(xx,yy,xx+1,yy+1,tuple(max(0,min(255,v+delta)) for v in color))
    # Head front UV: x=10..19, y=74..83. Blue eyes and a small smiling mouth.
    r(10,74,20,77,(60,90,164)); r(10,77,11,80,(67,98,175)); r(19,77,20,80,(67,98,175))
    for x in [12,16]:
        r(x,78,x+2,81,(32,60,110)); r(x,79,x+2,80,(76,155,218)); r(x,78,x+1,79,(255,255,255))
    r(11,81,13,82,(243,163,167)); r(17,81,19,82,(243,163,167)); r(14,82,16,83,(180,98,106))
    r(0,96,32,128,(241,237,248))
    for yy in [98,100,102]: r(3,yy,4,yy+1,(47,64,120))
    r(195,68,199,70,(66,95,163)); r(198,67,200,68,(66,95,163)); r(199,66,200,68,(66,95,163))
    r(195,68,196,69,(255,255,255)); r(196,66,197,67,(66,95,163))
    png(ASSETS / 'textures/entity/big_fat_fish.png',256,128,pix)

    js(DATA / 'recipe/cooked_rice.json',{'type':'minecraft:crafting_shapeless','ingredients':['bigfatfish:rice']*3+['minecraft:bowl'],'result':{'id':'bigfatfish:cooked_rice','count':1}})
    js(DATA / 'recipe/stone_mill.json',{'type':'minecraft:crafting_shaped','pattern':['SWS','CCC','CCC'],'key':{'S':'minecraft:stone_slab','W':'minecraft:stick','C':'minecraft:cobblestone'},'result':{'id':'bigfatfish:stone_mill','count':1}})
    # 26.3 uses singular condition/modifier keys and typed predicates.
    root_condition={'type':'minecraft:match_block','blocks':'bigfatfish:rice_crop','state':{'part':'0'}}
    mature_condition={'type':'minecraft:match_block','blocks':'bigfatfish:rice_crop','state':{'part':'0','age':'7'}}
    js(DATA / 'loot_table/blocks/rice_crop.json',{'type':'minecraft:block','modifier':{'type':'minecraft:explosion_decay'},'pools':[
        {'condition':root_condition,'rolls':1,'entries':[{'type':'minecraft:item','name':'bigfatfish:paddy'}]},
        {'condition':mature_condition,'rolls':2,'entries':[{'type':'minecraft:item','name':'bigfatfish:paddy'}]}]})
    js(DATA / 'loot_table/blocks/stone_mill.json',{'type':'minecraft:block','pools':[{'condition':{'type':'minecraft:survives_explosion'},'rolls':1,'entries':[{'type':'minecraft:item','name':'bigfatfish:stone_mill'}]}]})
    js(DATA / 'loot_table/entities/big_fat_fish.json',{'type':'minecraft:entity','pools':[]})
    js(ROOT / 'data/minecraft/tags/block/mineable/pickaxe.json',{'replace':False,'values':['bigfatfish:stone_mill']})
    js(ROOT / 'data/minecraft/tags/block/crops.json',{'replace':False,'values':['bigfatfish:rice_crop']})
    for name, ingredient in [('cooked_rice','rice'),('stone_mill','paddy')]:
        js(DATA / f'advancement/recipes/{name}.json',{'parent':'minecraft:recipes/root','criteria':{'has_ingredient':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':f'bigfatfish:{ingredient}'}]}}},'requirements':[['has_ingredient']],'rewards':{'recipes':[f'bigfatfish:{name}']}})

    zh={
        'entity.bigfatfish.big_fat_fish':'大肥鱼','block.bigfatfish.rice_crop':'水稻','block.bigfatfish.stone_mill':'石磨',
        'item.bigfatfish.paddy':'稻谷','item.bigfatfish.rice':'稻米','item.bigfatfish.cooked_rice':'米饭','item.bigfatfish.stone_mill':'石磨','item.bigfatfish.big_fat_fish_spawn_egg':'大肥鱼刷怪蛋',
        'message.bigfatfish.not_owner':'她只听主人的话。','message.bigfatfish.controls':'空手右键坐下/跟随，潜行右键打开背包，米饭右键喂食。',
        'screen.bigfatfish.backpack':'背包 · 27 格','screen.bigfatfish.mainhand':'右手（主手）','screen.bigfatfish.offhand':'左手','screen.bigfatfish.hunger':'饥饿值：%s/20',
    }
    states=['闲着','跟随','坐下','收割','战斗','偷懒','讨饭','坐床']
    zh.update({f'activity.bigfatfish.{i}':s for i,s in enumerate(states)})
    dialogue={
        'tame':['哼，只是你煮的饭还不错，我才跟着你！','以后米饭要管够，听见没？主人。','既然你这么想让我陪你……那就勉强答应啦。'],
        'fed':['才、才没有很开心呢！再来一碗也不是不行。','嗯……米饭还不错，算你过关。','这碗饭就当你请我的，下次也要记得哦！'],
        'full':['吃不下啦！你是想把我喂成大肥鱼吗？','哼，刚吃饱，留着等会儿再给我。','今天的米饭已经够啦，别光顾着喂我。'],
        'sit':['好啦，我就在这里待着，你别走太远。','坐就坐……拿了工具我还是会干活的哦。','哼，这里就是我的位置了，记得回来接我。'],
        'follow':['真拿你没办法，我跟着就是啦。','走慢点！我可不是在担心你迷路。','知道啦知道啦，你去哪我就去哪。'],
        'hungry':['喂，主人！米饭呢？我才没有在撒娇！','肚子只是有一点点饿……快给我一碗饭啦。','干了这么多活，连碗米饭都没有吗？哼！'],
        'loaf':['我只是检查一下附近，才没有偷懒呢。','休息一会儿怎么了？工具也要喘口气的！','别盯着我看啦，等会儿就继续干活。'],
        'harvest':['收好了，都在背包里，记得自己拿。','哼，收获还不错吧？也不看看是谁干的。','这些庄稼我帮你收了，米饭可别忘记哦。'],
        'backpack_full':['装不下啦！快来帮我清清背包。','哼，背包都塞满了，先把东西拿走再说。','再收就要掉地上了，我才不给你浪费呢。'],
        'fight':['躲我后面！我可不是特意保护你。','那些家伙真碍眼，我去教训一下。','剑给我拿着，我就不会让它们欺负你。'],
        'bed':['这张床看起来不错，借我坐一会儿。','只是坐一下，才不是想偷睡！','床上舒服多了……你不许笑。'],
        'death':['主人……背包里的东西，记得拿走。','还没吃到下一碗米饭呢……','哼……这回可要你来保护我了。'],
    }
    for event,lines in dialogue.items():
        for i,line in enumerate(lines): zh[f'dialogue.bigfatfish.{event}.{i}']='〈大肥鱼〉'+line
    js(ASSETS / 'lang/zh_cn.json',zh)
    en=dict(zh)
    en.update({'entity.bigfatfish.big_fat_fish':'Big Fat Fish','block.bigfatfish.rice_crop':'Rice Plant','block.bigfatfish.stone_mill':'Stone Mill','item.bigfatfish.paddy':'Paddy','item.bigfatfish.rice':'Rice Grain','item.bigfatfish.cooked_rice':'Bowl of Rice','item.bigfatfish.stone_mill':'Stone Mill','item.bigfatfish.big_fat_fish_spawn_egg':'Big Fat Fish Spawn Egg','screen.bigfatfish.backpack':'Backpack (27 slots)','screen.bigfatfish.mainhand':'Right (main)','screen.bigfatfish.offhand':'Left','screen.bigfatfish.hunger':'Food: %s/20'})
    js(ASSETS / 'lang/en_us.json',en)

if __name__ == '__main__': main()
