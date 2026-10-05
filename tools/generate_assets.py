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
    states=['闲着','跟随','坐下','耕作','战斗','偷懒','讨饭','坐床']
    zh.update({f'activity.bigfatfish.{i}':s for i,s in enumerate(states)})
    dialogue={
        'tame':['哼，只是你煮的饭还不错，我才跟着你！','以后米饭要管够，听见没？主人。','既然你这么想让我陪你……那就勉强答应啦。'],
        'fed':['才、才没有很开心呢！下次记得再给本大小姐煮饭。','嗯……米饭还不错，算你过关。','这碗饭就当你请我的，下次也要记得哦！'],
        'full':['吃不下啦！你是想把我喂成大肥鱼吗？','哼，刚吃饱，留着等会儿再给我。','本大小姐还没消化完呢，别急着再喂。'],
        'sit':['好啦，我就在这里待着，你别走太远。','坐就坐……你可要记得回来接我哦。','哼，这里就是我的位置了，记得回来接我。'],
        'follow':['真拿你没办法，我跟着就是啦。','走慢点！我可不是在担心你迷路。','知道啦知道啦，你去哪我就去哪。'],
        'hungry':['喂，主人！米饭呢？我才没有在撒娇！','肚子只是有一点点饿……快给我一碗饭啦。','等了这么久，连碗米饭都没有吗？哼！'],
        'loaf':['我只是检查一下附近，才没有偷懒呢。','休息一会儿怎么了？工具也要喘口气的！','别盯着我看啦，等会儿就继续干活。'],
        'harvest':['收好了，补种用掉的种子之外都在背包里，记得自己拿。','哼，收获还不错吧？也不看看是谁干的。','这些庄稼我帮你收了，能种的地方还会补上，米饭可别忘记哦。'],
        'backpack_full':['这次收获装不下啦！快来帮我清清背包。','哼，背包剩下的位置不够了，先把东西拿走再说。','先腾出地方再收，我才不给你浪费呢。'],
        'fight':['躲我后面！我可不是特意保护你。','那些家伙真碍眼，我去教训一下。','剑给我拿着，我就不会让它们欺负你。'],
        'bed':['这张床看起来不错，借我坐一会儿。','本大小姐想去床上坐一下，才不是想偷睡！','这张床看着挺舒服……本大小姐去试试，你不许笑。'],
        'death':['主人……背包里的东西，记得拿走。','还没吃到下一碗米饭呢……','哼……这回可要你来保护我了。'],
    }
    dialogue.update({
        'taming':['哼，饭先收下啦，跟不跟你还要再想想！','我、我才没有被一碗米饭收买呢……现在吃饱了。','再陪我一会儿嘛，人家还不认识你呢。','嗯，好香！下次也要这个味道哦。','别急着摸头啦，我还没答应呢！','你带的饭不错……下次还想吃这个味道。'],
        'baby_tame':['那、那就牵着我走吧！米饭也要带上哦。','以后你就是我的饭饭主人啦，嘿嘿！','我会乖乖跟着你的，才不是因为喜欢你呢！'],
        'baby_fed':['啊呜！吃饱饱，长高高！','饭饭好香呀，再摸摸我的头嘛。','嘿嘿……尾巴摇起来了，不许笑我！'],
        'baby_hungry':['主人，饭饭呢？本大小姐想吃一碗啦！','我还小嘛……想要一碗香香的米饭。','给我饭饭，我就给你看尾巴摇摇！'],
        'baby_cute':['看我看我！今天也有乖乖等你哦。','我才没有想撒娇……只是想挨着你一点点。','嘿嘿，长大以后就能帮你啦，现在先抱抱嘛。'],
    })
    for event,lines in dialogue.items():
        for i,line in enumerate(lines): zh[f'dialogue.bigfatfish.{event}.{i}']=line.replace('我们','本大小姐和小肥鱼').replace('人家','本大小姐').replace('我','本大小姐')
    zh.update({'dialogue.bigfatfish.format':'〈%s〉%s',
        'screen.bigfatfish.affection':'好感度：%s/100','screen.bigfatfish.full':'吃饱了',
        'screen.bigfatfish.special':'繁育小肥鱼','screen.bigfatfish.too_young':'幼年 · 尚未成年','screen.bigfatfish.cooldown':'冷却：%s 秒',
        'screen.bigfatfish.special_hint':'成年且好感度达到 100 时，可以与主人繁育一只小肥鱼。冷却 5 分钟。',
        'dialogue.bigfatfish.family.0':'本大小姐把小家伙交给你一起照顾啦，米饭可要准备两份！',
        'dialogue.bigfatfish.family.1':'哼，要对小肥鱼温柔一点，本大小姐可会盯着你哦！',
        'dialogue.bigfatfish.family.2':'以后本大小姐和小肥鱼一起陪着你……才不是舍不得你呢。'})
    zh.update({
        'dialogue.bigfatfish.breed_reject_young.0':'救命！本大小姐还没长大呢！不许提这种越界要求，你这个变态！',
        'dialogue.bigfatfish.breed_reject_young.1':'本大小姐还在长大，繁育不行！你脑袋里都在想些什么啊，变态！',
        'dialogue.bigfatfish.breed_reject_young.2':'先学会好好照顾本大小姐！对未成年提这种要求，真差劲！',
        'dialogue.bigfatfish.breed_reject_affection.0':'本大小姐才不要答应你呢，滚远点！你连照顾人都没学会。',
        'dialogue.bigfatfish.breed_reject_affection.1':'你也太会得寸进尺了吧？先给本大小姐把米饭管好！',
        'dialogue.bigfatfish.breed_reject_affection.2':'哼，好感度都不够，还想让本大小姐答应？做梦，笨蛋！',
        'dialogue.bigfatfish.breed_reject_cooldown.0':'本大小姐刚带来一个小家伙，让本大小姐休息一会儿啦。',
        'dialogue.bigfatfish.breed_reject_cooldown.1':'哼，冷却还没结束呢，本大小姐也需要歇一歇。',
        'dialogue.bigfatfish.breed_reject_cooldown.2':'先照顾好刚来的小肥鱼，别一直催本大小姐！',
        'dialogue.bigfatfish.runaway.0':'本大小姐对你彻底失望了。这次真的走啦，你好自为之。',
        'dialogue.bigfatfish.runaway.1':'本大小姐才不要再受你的气！不在这里待了，再见。',
        'dialogue.bigfatfish.runaway.2':'哼，本大小姐受够了。你的东西留下了，以后别再找本大小姐。',
    })
    # Materialize complete, prewritten responses for each relationship tier and event.
    tones=[
        ['', '', ''],
        ['你最近照顾得还算用心。','本大小姐已经记住你的好啦。','哼，算你有点诚意。'],
        ['有你陪着，本大小姐还挺放心的。','本大小姐愿意把这些事告诉你啦。','你是本大小姐认可的主人，知道吗？'],
        ['今天也要陪着本大小姐哦。','你来了，本大小姐就安心了……不许笑！','本大小姐刚好在等你，才没有想你呢。'],
        ['本大小姐最信任的就是你啦。','在你面前，本大小姐也可以撒撒娇吧……','本大小姐想一直陪着你，哼，你要记住哦。'],
    ]
    base=[(key,value) for key,value in zh.items() if key.startswith('dialogue.bigfatfish.') and key!='dialogue.bigfatfish.format']
    for stage,prefixes in enumerate(tones):
        for key,value in base:
            event,index=key.removeprefix('dialogue.bigfatfish.').rsplit('.',1)
            if event=='taming': continue
            zh[f'dialogue.bigfatfish.stage.{stage}.{event}.{index}']=value if event in {'breed_reject_young','breed_reject_affection','breed_reject_cooldown','death','full'} else prefixes[int(index)%3]+value
    negative_tones={
        -1:['本大小姐现在心情不好。','本大小姐有点不高兴，哼。','哼，本大小姐还在生气呢。'],
        -2:['本大小姐心里很不是滋味。','别以为本大小姐已经原谅你了。','哼，本大小姐都快不相信你了。'],
        -3:['本大小姐已经很失望了。','本大小姐对你没什么好心情。','哼，本大小姐现在不想和你说笑。'],
        -4:['本大小姐真的受够了。','本大小姐已经快不想留在这里了。','哼，本大小姐现在不想亲近你。'],
    }
    for stage,prefixes in negative_tones.items():
        for key,value in base:
            event,index=key.removeprefix('dialogue.bigfatfish.').rsplit('.',1)
            if event=='taming': continue
            zh[f'dialogue.bigfatfish.stage.{stage}.{event}.{index}']=value if event in {'runaway','breed_reject_young','death'} else prefixes[int(index)%3]+value
    staged={
        'fed':[
            ['饭还行，本大小姐先收下了。你可别得意。','只是饭合口味才吃的，本大小姐还在观察你呢。','哼，吃完啦，下次别煮得太软。'],
            ['你记得本大小姐喜欢米饭呀……还算用心。','嗯，这次也不错。本大小姐允许你再夸一句。','本大小姐吃好了，这次的饭还算满意嘛。'],
            ['你煮的米饭越来越合本大小姐的口味了。','本大小姐信得过你的手艺，放心吃啦。','今天也能吃到你的饭，本大小姐心情不错。'],
            ['你亲手送来，本大小姐就觉得格外香……才不是偏心！','本大小姐刚才还在想你会不会来喂饭呢。','吃饱啦，陪本大小姐坐一会儿吧。'],
            ['本大小姐最喜欢你煮的米饭了……还有你。最后那句没听见！','有你照顾，本大小姐每天都很开心。','吃饱啦，本大小姐想在你身边多待一会儿。'],
        ],
        'hungry':[
            ['喂，主人！本大小姐饿了，米饭呢？','本大小姐的米饭可不能少，快拿饭来！','肚子在响？才不是本大小姐，是你听错了！'],
            ['你还记得本大小姐喜欢什么吧？肚子有点饿了。','本大小姐在这里等饭，别让本大小姐等太久。','哼，等了这么久，给本大小姐煮一碗饭吧。'],
            ['本大小姐饿了，今天也想吃你做的米饭。','只告诉你哦，本大小姐的小肚子在咕咕叫。','本大小姐相信你不会忘记喂饭，对吧？'],
            ['本大小姐想吃米饭，也想你陪着一起吃……','主人，本大小姐饿了，给本大小姐拿碗饭嘛。','快拿饭喂本大小姐嘛……才没有撒娇！'],
            ['主人，本大小姐肚子饿啦。喂完再陪本大小姐待一会儿，好不好？','本大小姐今天也在等你送饭，见到你就很开心。','想吃你煮的米饭，也想挨着你。本大小姐只说这一次哦。'],
        ],
    }
    for event,stages in staged.items():
        for stage,lines in enumerate(stages):
            for index,line in enumerate(lines):
                zh[f'dialogue.bigfatfish.stage.{stage}.{event}.{index}']=line
    for stage,prefixes in enumerate(tones):
        for event in ['baby_fed','baby_hungry','baby_cute']:
            for index,line in enumerate(dialogue[event]):
                text=line.replace('我','本大小姐')
                zh[f'dialogue.bigfatfish.stage.{stage}.{event}.{index}']=prefixes[index]+text
    for stage,lines in {
        2:['什、什么？本大小姐还没准备好呢！不许趁机欺负本大小姐，笨蛋！','本大小姐才不要现在就答应你！等好感度满了再说……你别笑啦！','你这个笨蛋，急什么呀？本大小姐也会害羞的！'],
        3:['本大小姐又没说永远不答应……但现在不行，别得意！','快把好感度攒满啦，本大小姐才、才没有在期待呢。','你靠这么近做什么……本大小姐还差一点点才会答应，笨蛋！'],
    }.items():
        for index,line in enumerate(lines): zh[f'dialogue.bigfatfish.stage.{stage}.breed_reject_affection.{index}']=line
    # Negative affection can result from neglect or offensive requests. Do not invent a cause.
    distant_baby={
        'fed':['饭吃完了，本大小姐可还没原谅你。','嗯，这碗饭还行。别以为本大小姐这么容易就消气。','本大小姐收下饭了，以后怎么相处还要看你的表现。'],
        'baby_fed':['饭收下了，本大小姐现在吃饱了。','嗯，肚子不饿了。别以为一碗饭就能哄好本大小姐。','本大小姐会慢慢长大的，你先把饭照顾好。'],
        'baby_hungry':['本大小姐想吃饭了，别趁机逗本大小姐！','本大小姐还小，想要米饭。别拿这个要求本大小姐讨好你。','把饭拿来就行，本大小姐还在生你的气呢。'],
        'baby_cute':['本大小姐还没消气，别以为摇摇尾巴就是原谅你了。','别挨这么近，本大小姐还在生气呢。','本大小姐还没长大，也知道自己现在不高兴。'],
    }
    for stage in negative_tones:
        for event,lines in distant_baby.items():
            for index,line in enumerate(lines): zh[f'dialogue.bigfatfish.stage.{stage}.{event}.{index}']=line
    # Keep only states that the entity's actual branches can emit, rather than their cross product.
    allowed={'tame':{0},'baby_tame':{0},'family':{4},'runaway':{-4},
             'breed_reject_young':set(range(-4,4)), # refusal deducts 10, so the result cannot be 100
             'breed_reject_affection':set(range(-4,4)),'breed_reject_cooldown':{4}}
    for key in list(zh):
        if key.startswith('dialogue.bigfatfish.stage.'):
            stage,event,index=key.removeprefix('dialogue.bigfatfish.stage.').split('.')
            if event in allowed and int(stage) not in allowed[event]: del zh[key]
        elif key.startswith('dialogue.bigfatfish.') and key!='dialogue.bigfatfish.format':
            event=key.removeprefix('dialogue.bigfatfish.').rsplit('.',1)[0]
            if event not in {'taming','full'}: del zh[key]
    zh.pop('screen.bigfatfish.too_young',None)
    zh.update({'skin.bigfatfish.maid':'鲸尾女仆', 'skin.bigfatfish.summer':'白蓝夏日', 'screen.bigfatfish.skin':'外观 · 点击切换'})
    zh.update({'screen.bigfatfish.juvenile':'幼年 · 米饭帮助长大','screen.bigfatfish.adult':'成年 · 可以帮忙干活'})
    js(ASSETS / 'lang/zh_cn.json',zh)
    en=dict(zh)
    en.update({'entity.bigfatfish.big_fat_fish':'Big Fat Fish','block.bigfatfish.rice_crop':'Rice Plant','block.bigfatfish.stone_mill':'Stone Mill','item.bigfatfish.paddy':'Paddy','item.bigfatfish.rice':'Rice Grain','item.bigfatfish.cooked_rice':'Bowl of Rice','item.bigfatfish.stone_mill':'Stone Mill','item.bigfatfish.big_fat_fish_spawn_egg':'Big Fat Fish Spawn Egg','screen.bigfatfish.backpack':'Backpack (27 slots)','screen.bigfatfish.mainhand':'Right (main)','screen.bigfatfish.offhand':'Left','screen.bigfatfish.hunger':'Food: %s/20'})
    en.update({'skin.bigfatfish.maid':'Whale Maid', 'skin.bigfatfish.summer':'Summer Blue', 'screen.bigfatfish.skin':'Skin: select below'})
    en.update({'screen.bigfatfish.juvenile':'Young: feed rice','screen.bigfatfish.adult':'Adult: can help'})
    en.update({'dialogue.bigfatfish.format':'<%s> %s','screen.bigfatfish.affection':'Affection: %s/100',
        'screen.bigfatfish.full':'Full','screen.bigfatfish.special':'Raise a little fish',
        'screen.bigfatfish.cooldown':'Cooldown: %s s','screen.bigfatfish.special_hint':'An adult with 100 affection can raise a little fish with its owner. Five-minute cooldown.'})
    js(ASSETS / 'lang/en_us.json',en)

if __name__ == '__main__': main()
