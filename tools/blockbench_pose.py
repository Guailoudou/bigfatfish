"""Preview runtime joint bending in Blockbench, restoring editable geometry afterward."""
import argparse
import base64
from pathlib import Path

from blockbench_mcp import Blockbench


POSE = r'''(() => {
    if (window.bigfatfishPosePreview) throw Error("A pose preview is already active");
    const meshes=[...Mesh.all], groups=[...Group.all];
    window.bigfatfishPosePreview={project:Project.uuid,
        meshes:meshes.map(m=>({uuid:m.uuid,origin:[...m.origin],vertices:JSON.parse(JSON.stringify(m.vertices))})),
        groups:groups.map(g=>({uuid:g.uuid,origin:[...g.origin],rotation:[...g.rotation]}))};
    const baby=Project.name.includes("juvenile"), span=baby?1.2:1.4;
    const sitting=POSE_NAME==="sitting", angle=sitting?1.3:-.8;
    const jointName=sitting?"shin":"forearm";
    const belongs=(mesh,name)=>{let p=mesh;while(p&&p!=="root"){if(p.name===name)return p;p=p.parent;}return null;};
    for(const m of meshes) {
        const joint=belongs(m,jointName);if(!joint)continue;
        const rigid=Boolean(belongs(m,"shoe"));
        for(const v of Object.values(m.vertices)) {
            const py=joint.origin[1]-(v[1]+m.origin[1]);
            const pz=v[2]+m.origin[2]-joint.origin[2];
            if(!rigid&&py<=0)continue;
            const length=rigid?span:Math.min(py,span),theta=angle*length/span;
            const c=Math.cos(theta),s=Math.sin(theta),rest=py-length;
            const sinc=Math.abs(theta)<1e-6?1:Math.sin(theta)/theta;
            const cosc=Math.abs(theta)<1e-6?.5:(1-Math.cos(theta))/(theta*theta);
            const y=length*sinc+rest*c-pz*s;
            const z=length*theta*cosc+rest*s+pz*c;
            v[1]=joint.origin[1]-y-m.origin[1];
            v[2]=joint.origin[2]+z-m.origin[2];
        }
    }
    const degrees=180/Math.PI;
    for(const g of groups) {
        if(g.name==="left_arm")g.rotation[2]=(sitting?-.23:.38)*degrees;
        if(g.name==="right_arm")g.rotation[2]=(sitting?.23:-.38)*degrees;
        if(!sitting&&(g.name==="left_arm"||g.name==="right_arm"))g.rotation[0]=.7*degrees;
    }
    let shift=0;
    if(sitting) {
        const leg=groups.find(g=>g.name==="left_leg");
        const shin=groups.find(g=>g.name==="shin"&&g.parent===leg);
        const thigh=leg.origin[1]-shin.origin[1];
        shift=thigh*(1-Math.cos(angle))+span*(1-Math.sin(angle)/angle);
        for(const g of groups) {
            if(g.name==="left_leg"||g.name==="right_leg") {
                g.rotation[0]=angle*degrees;
                g.rotation[1]=(g.name==="left_leg"?.16:-.16)*degrees;
            }
            if(g.name!=="root")g.origin[1]-=shift;
        }
        for(const m of meshes)m.origin[1]-=shift;
    }
    Canvas.updateAll();
    return {baby,shift,meshes:meshes.length};
})()'''

RESTORE = r'''(() => {
    const saved=window.bigfatfishPosePreview;
    if(!saved)return false;
    if(saved.project!==Project.uuid)throw Error("Select the previewed project before restoring");
    for(const item of saved.meshes) {
        const mesh=Mesh.all.find(m=>m.uuid===item.uuid);
        mesh.origin.splice(0,3,...item.origin);mesh.vertices=item.vertices;
    }
    for(const item of saved.groups) {
        const group=Group.all.find(g=>g.uuid===item.uuid);
        group.origin.splice(0,3,...item.origin);group.rotation.splice(0,3,...item.rotation);
    }
    delete window.bigfatfishPosePreview;Canvas.updateAll();return true;
})()'''


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('pose',choices=['sitting','clasp'])
    args=parser.parse_args()
    bb=Blockbench()
    info=bb.call('get_project_info')['structuredContent']['project']
    baby='juvenile' in info['name']
    if info['name'] not in ('adult','juvenile','bigfatfish_pixel','bigfatfish_pixel_juvenile'):
        raise ValueError('Open a full adult or juvenile character project first')
    bb.call('risky_eval',code='if(window.bigfatfishPosePreview)throw Error("Restore the active preview first");true')
    target=Path(__file__).resolve().parents[1]/'build'/f'blockbench-{info["name"]}-{args.pose}.png'
    try:
        result=bb.call('risky_eval',code=POSE.replace('POSE_NAME',repr(args.pose)))
        print(result['content'][0]['text'])
        center=[0,(6 if baby else 12) if args.pose=='sitting' else (9 if baby else 16),0]
        shot=bb.call('set_camera_angle',position=[19 if baby else 27,12 if baby else 20,-19 if baby else -27],
                     target=center,projection='perspective',fov=42,max_size=1100)
        for content in shot.get('content',[]):
            if content['type']=='image':
                target.write_bytes(base64.b64decode(content['data']))
        print(target)
    finally:
        bb.call('risky_eval',code=RESTORE)


if __name__=='__main__':
    main()
