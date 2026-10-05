"""Refine the reference character's curls directly in the active Blockbench projects.

Uses native meshes/Undo; writes the resulting editable projects, not render previews.
"""
from pathlib import Path
import base64
import time
from blockbench_mcp import Blockbench

ROOT = Path(__file__).resolve().parents[1]
EDIT = r'''(()=>{
const baby=Project.name.endsWith('_juvenile'), sx=baby?.8:1;
const strands=Mesh.all.filter(m=>/^(flowing_side_|back_wave_)/.test(m.name));
Undo.initEdit({elements:strands});
function sample(points,t){
 const f=t*(points.length-1),i=Math.min(points.length-2,Math.floor(f)),u=f-i;
 const a=points[Math.max(0,i-1)],b=points[i],c=points[i+1],d=points[Math.min(points.length-1,i+2)];
 return b.map((v,k)=>.5*((2*v)+(-a[k]+c[k])*u+(2*a[k]-5*v+4*c[k]-d[k])*u*u+(-a[k]+3*v-3*c[k]+d[k])*u*u*u));
}
for(const m of strands){
 const L=baby?8.2:13;
 const ring=Object.entries(m.vertices).filter(([k])=>/^v0_/.test(k)).map(([,v])=>v);
 const start=[0,0,0].map((_,k)=>(Math.min(...ring.map(v=>v[k]))+Math.max(...ring.map(v=>v[k])))/2);
 const side=m.name.startsWith('flowing'), n=Number(m.name.split('_').at(-1));
 const sign=start[0]<0?-1:1, depth=start[2];
 const off=side ? n*.12 : 0;
 let points;
 if(side) points=[start,[sign*(3.5+off)*sx,-L*.16,depth+.1*sx],
  [sign*(4.1+off)*sx,-L*.33,depth],[sign*(3.9+off)*sx,-L*.50,depth+.18*sx],
  [sign*(4.85+off)*sx,-L*.68,depth+.28*sx],[sign*(5.35+off)*sx,-L*.84,depth],
  [sign*(4.85+off)*sx,-L,depth-.12*sx],[sign*(3.9+off)*sx,-L*.94,depth-.2*sx],
  [sign*(4.05+off)*sx,-L*.82,depth-.18*sx],[sign*(4.6+off)*sx,-L*.85,depth-.12*sx]];
 else {
  const x=start[0], dir=n%2===0?1:-1, wave=.36*sx;
  points=[start,[x+wave*dir,-L*.2,depth+.30*sx],[x-wave*dir,-L*.4,depth+.65*sx],
   [x+wave*dir,-L*.6,depth+.8*sx],[x+.8*sx*dir,-L*.81,depth+.9*sx],
   [x+.3*sx*dir,-L,depth+.6*sx],[x-.55*sx*dir,-L*.92,depth+.4*sx],
   [x-.15*sx*dir,-L*.82,depth+.45*sx]];
 }
 const vertices={},faces={},steps=30,sides=6;
 for(let j=0;j<=steps;j++){
  const t=j/steps,p=sample(points,t),a=sample(points,Math.max(0,t-.003)),b=sample(points,Math.min(1,t+.003));
  const tx=b[0]-a[0],ty=b[1]-a[1],length=Math.hypot(tx,ty)||1;
  const radius=sx*(side?.48:.72)*Math.min(1,.48+t*4)*Math.max(.045,Math.min(1,(1-t)*7));
  for(let k=0;k<sides;k++){
   const angle=k/sides*Math.PI*2,c=Math.cos(angle),s=Math.sin(angle);
   vertices['v'+j+'_'+k]=[p[0]-ty/length*radius*c,p[1]+tx/length*radius*c,p[2]+radius*.65*s];
  }
 }
 m.vertices=vertices;m.faces={};
 for(let j=0;j<steps;j++) for(let k=0;k<sides;k++){
  const next=(k+1)%sides,keys=['v'+j+'_'+k,'v'+j+'_'+next,'v'+(j+1)+'_'+next,'v'+(j+1)+'_'+k];
  const uv={};uv[keys[0]]=[34+k/sides*26,1+j/steps*29];uv[keys[1]]=[34+(k+1)/sides*26,1+j/steps*29];
  uv[keys[2]]=[34+(k+1)/sides*26,1+(j+1)/steps*29];uv[keys[3]]=[34+k/sides*26,1+(j+1)/steps*29];
  faces['curl_'+j+'_'+k]=new MeshFace(m,{vertices:keys,uv,texture:Texture.all[0].uuid});
 }
 m.faces=faces;m.shading='flat';
}
const group=Group.all.find(g=>g.name==='hair_28');
let lining=Mesh.all.find(m=>m.name==='layered_hair_backing');
if(!lining)lining=new Mesh({name:'layered_hair_backing',origin:group.origin.slice()}).addTo(group).init();
lining.vertices={};lining.faces={};
const head=Group.all.find(g=>g.name==='head').origin,sy=baby?.624:1;
for(let j=0;j<=12;j++){
 const t=j/12,width=sx*(3.35+1.05*Math.sin(t*Math.PI));
 const y=head[1]+(baby?5.4:6.3)-t*(baby?9.6:13.8);
 for(let k=0;k<=10;k++){
  const a=k/10*Math.PI;
  lining.vertices['b'+j+'_'+k]=[width*Math.cos(a)-group.origin[0],y-group.origin[1],sx*(1.15+(1.18+.35*Math.sin(t*Math.PI))*Math.sin(a))-group.origin[2]];
 }
}
for(let j=0;j<12;j++)for(let k=0;k<10;k++){
 const v=['b'+j+'_'+k,'b'+j+'_'+(k+1),'b'+(j+1)+'_'+(k+1),'b'+(j+1)+'_'+k],uv={};
 v.forEach((key,i)=>uv[key]=[38+((i===1||i===2)?k+1:k)*2,1+(j+(i>=2?1:0))*2.4]);
 lining.faces['b'+j+'_'+k]=new MeshFace(lining,{vertices:v,uv,texture:Texture.all[0].uuid});
}
lining.shading='flat';
Canvas.updateAll();Undo.finishEdit('Shape reference-style flowing curls');
return {project:Project.name,strands:strands.length,meshes:Mesh.all.length};
})()'''

def main():
    bb = Blockbench()
    for name in ('bigfatfish_pixel', 'bigfatfish_pixel_juvenile'):
        bb.open_project(ROOT / 'models' / f'{name}.bbmodel')
        print(bb.call('risky_eval', code=EDIT)['content'][0]['text'])
        project = bb.call('get_project_info')['structuredContent']['project']
        for attempt in range(5):
            try:
                contents = bb.rpc('resources/read', {'uri': f'blockbench://project/{project["uuid"]}.bbmodel'})
                break
            except RuntimeError as error:
                if 'already compiling' not in str(error) or attempt==4:
                    raise
                time.sleep(1)
        (ROOT / 'models' / f'{name}.bbmodel').write_text(contents['contents'][0]['text'], encoding='utf-8')
        for side, position in [('front',[0,13,-35]),('back',[0,13,35]),('angle',[20,20,-30])]:
            r=bb.call('set_camera_angle',position=position,target=[0,9 if name.endswith('juvenile') else 12,0],projection='orthographic',zoom=.65,max_size=1000)
            for item in r.get('content',[]):
                if item['type']=='image':
                    (ROOT/'build'/f'curl-{name}-{side}.png').write_bytes(base64.b64decode(item['data']))

if __name__ == '__main__':
    main()

