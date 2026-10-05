package cn.deepmod.bigfatfish.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Original indexed PMX surfaces, four-weight skinning and selected facial morphs. */
final class MmdMesh {
    static final MmdMesh INSTANCE=new MmdMesh("mmd"), SUMMER=new MmdMesh("mmd_summer");
    static MmdMesh forSkin(int skin) { return skin==1?SUMMER:INSTANCE; }
    private final float[][] vertices;
    private final int[] indices,parents;
    private final String[] names;
    private final float[][] bind;
    private final List<Material> materials=new ArrayList<>();
    private final Map<String,float[][]> morphs=new HashMap<>();
    private record Material(Identifier texture,int start,int count,int color,boolean translucent,boolean doubleSided) {}
    record Pose(Matrix4f[] bones,float[] geometry,float scale,float shift) {}

    private static float[] floats(JsonArray array) {
        float[] result=new float[array.size()];
        for(int i=0;i<result.length;i++) result[i]=array.get(i).getAsFloat();
        return result;
    }
    private MmdMesh(String asset) {
        try(var raw=MmdMesh.class.getResourceAsStream("/assets/bigfatfish/models/entity/"+asset+".mesh.json.gz")) {
            if(raw==null) throw new IOException("Missing converted MMD geometry");
            JsonObject data;
            try(var reader=new InputStreamReader(new GZIPInputStream(raw),StandardCharsets.UTF_8)) {
                data=JsonParser.parseReader(reader).getAsJsonObject();
            }
            if(data.get("format").getAsInt()!=1) throw new IOException("Unsupported MMD mesh format");
            var points=data.getAsJsonArray("vertices");vertices=new float[points.size()][];
            for(int i=0;i<vertices.length;i++) vertices[i]=floats(points.get(i).getAsJsonArray());
            var triangles=data.getAsJsonArray("indices");indices=new int[triangles.size()];
            for(int i=0;i<indices.length;i++) indices[i]=triangles.get(i).getAsInt();
            var skeleton=data.getAsJsonArray("bones");names=new String[skeleton.size()];parents=new int[names.length];bind=new float[names.length][];
            for(int i=0;i<names.length;i++) {
                var b=skeleton.get(i).getAsJsonObject();names[i]=b.get("name").getAsString();
                parents[i]=b.get("parent").getAsInt();bind[i]=floats(b.getAsJsonArray("position"));
            }
            for(var entry:data.getAsJsonArray("materials")) {
                var m=entry.getAsJsonObject();var rgba=floats(m.getAsJsonArray("color"));
                if(rgba[3]<=0) continue;
                int color=((int)(rgba[3]*255)<<24)|((int)(rgba[0]*255)<<16)|((int)(rgba[1]*255)<<8)|(int)(rgba[2]*255);
                materials.add(new Material(Identifier.parse(m.get("texture").getAsString()),m.get("start").getAsInt(),m.get("count").getAsInt(),color,rgba[3]<.99F,m.get("double_sided").getAsBoolean()));
            }
            for(var entry:data.getAsJsonObject("morphs").entrySet()) {
                var offsets=entry.getValue().getAsJsonArray();float[][] values=new float[offsets.size()][];
                for(int i=0;i<values.length;i++) values[i]=floats(offsets.get(i).getAsJsonArray());
                morphs.put(entry.getKey(),values);
            }
        } catch(IOException e) { throw new IllegalStateException("Cannot load MMD character",e); }
    }
    private void compose(int i,Matrix4f[] result,Matrix4f[] local,boolean[] visiting) {
        if(result[i]!=null) return;
        if(visiting[i]) throw new IllegalStateException("Cyclic MMD bone hierarchy");
        visiting[i]=true;
        if(parents[i]>=0) {
            compose(parents[i],result,local,visiting);
            result[i]=new Matrix4f(result[parents[i]]).mul(local[i]);
        } else result[i]=new Matrix4f(local[i]);
        visiting[i]=false;
    }
    Pose pose(FishModel model,FishRenderer.State state) {
        Matrix4f[] local=new Matrix4f[names.length],matrices=new Matrix4f[names.length];
        float neutral=(float)Math.toRadians(55),time=state.ageInTicks;
        for(int i=0;i<names.length;i++) {
            float x=0,y=0,z=0;
            String name=names[i].endsWith("D")?names[i].substring(0,names[i].length()-1):names[i];
            switch(name) {
                case "頭" -> {x=model.head.xRot;y=model.head.yRot;z=model.head.zRot;}
                case "上半身" -> {x=model.body.xRot;y=model.body.yRot;z=model.body.zRot;}
                case "左腕" -> {x=model.leftArm.xRot;y=model.leftArm.yRot;z=model.leftArm.zRot;}
                case "右腕" -> {x=model.rightArm.xRot;y=model.rightArm.yRot;z=model.rightArm.zRot;}
                case "左ひじ" -> x=model.leftArm.getChild("forearm").xRot;
                case "右ひじ" -> x=model.rightArm.getChild("forearm").xRot;
                case "左足" -> {x=model.leftLeg.xRot;y=model.leftLeg.yRot;}
                case "右足" -> {x=model.rightLeg.xRot;y=model.rightLeg.yRot;}
                case "左ひざ" -> x=model.leftLeg.getChild("shin").xRot;
                case "右ひざ" -> x=model.rightLeg.getChild("shin").xRot;
                default -> {
                    if(name.startsWith("J_Sec_Hair") && !name.endsWith("錘")) {
                        x=state.hairPitch*.07F+(float)Math.sin(time*.045+i*.7)*.006F;
                        z=state.hairYaw*.055F;
                    } else if(name.startsWith("J_Opt_C_FoxTail") && !name.endsWith("錘")) {
                        y=(state.tailIdle+state.tailYaw)*.18F;
                    } else if(name.equals("summer_skirt")) {
                        x=state.clothPitch;z=state.clothSway;
                    } else if(name.startsWith("dress_")) {
                        x=state.clothPitch*.12F;z=state.clothSway*.12F;
                    }
                }
            }
            var p=bind[i];var rotation=new Matrix4f().translation(p[0],p[1],p[2]);
            float rest=name.startsWith("左")?neutral:-neutral;
            if(name.equals("左ひじ") || name.equals("右ひじ")) {
                // Bend in the lowered arm's frame, not the PMX A-pose axes.
                rotation.rotateZ(-rest).rotateX(x).rotateZ(rest);
            } else {
                rotation.rotateZYX(z,y,x);
                if(name.equals("左腕") || name.equals("右腕")) rotation.rotateZ(rest);
            }
            if(name.equals("summer_skirt") && state.sitting) rotation.translate(0,0,-2.4F);
            local[i]=rotation.translate(-p[0],-p[1],-p[2]);
        }
        boolean[] visiting=new boolean[names.length];
        for(int i=0;i<names.length;i++) compose(i,matrices,local,visiting);
        float[] offsets=new float[vertices.length*3];
        if(((int)time%93)<3 || state.emote==3 || state.emote==4) morph(offsets,"Fcl_EYE_Close",1);
        if(state.emote==1 || state.emote==4) { morph(offsets,"Fcl_MTH_Joy",.45F);morph(offsets,"Fcl_BRW_Joy",.3F); }
        float ageScale=state.isBaby?.90F/1.30F:1;
        float shift=model.body.y-model.body.getInitialPose().y();
        float[] deformed=new float[vertices.length*6];
        Vector3f p=new Vector3f(),n=new Vector3f();
        for(int i=0;i<vertices.length;i++) {
            var v=vertices[i];int out=i*6,off=i*3;
            for(int weight=0;weight<4;weight++) {
                int bone=(int)v[8+weight];float w=v[12+weight];if(w<=0 || bone<0) continue;
                p.set(v[0]+offsets[off],v[1]+offsets[off+1],v[2]+offsets[off+2]);
                matrices[bone].transformPosition(p);
                n.set(v[3],v[4],v[5]);matrices[bone].transformDirection(n);
                deformed[out]+=p.x*w;deformed[out+1]+=p.y*w;deformed[out+2]+=p.z*w;
                deformed[out+3]+=n.x*w;deformed[out+4]+=n.y*w;deformed[out+5]+=n.z*w;
            }
            deformed[out]*=ageScale;deformed[out+1]=24+(deformed[out+1]-24)*ageScale+shift;deformed[out+2]*=ageScale;
            n.set(deformed[out+3],deformed[out+4],deformed[out+5]).normalize();
            deformed[out+3]=n.x;deformed[out+4]=n.y;deformed[out+5]=n.z;
        }
        return new Pose(matrices,deformed,ageScale,shift);
    }
    private void morph(float[] target,String name,float weight) {
        var values=morphs.get(name);if(values==null) return;
        for(var v:values) {int p=(int)v[0]*3;for(int j=0;j<3;j++) target[p+j]+=v[j+1]*weight;}
    }
    void submit(PoseStack stack,SubmitNodeCollector collector,Pose pose,FishRenderer.State state,int light,int overlay) {
        float[] points=pose.geometry();
        for(var material:materials) {
            var type=state.isInvisible ? (state.isInvisibleToPlayer?RenderTypes.outline(material.texture()):RenderTypes.entityTranslucent(material.texture()))
                : material.translucent()?RenderTypes.entityTranslucent(material.texture())
                : material.doubleSided()?RenderTypes.entityCutout(material.texture()):RenderTypes.entityCutoutCull(material.texture());
            int color=state.isInvisible?0x26FFFFFF:material.color();
            collector.submitCustomGeometry(stack,type,(transform,buffer)->{
                for(int t=material.start();t<material.start()+material.count();t+=3) for(int corner=0;corner<4;corner++) {
                    int index=indices[t+Math.min(corner,2)],p=index*6;
                    buffer.addVertex(transform,points[p]/16,points[p+1]/16,points[p+2]/16)
                        .setColor(color).setUv(vertices[index][6],vertices[index][7]).setOverlay(overlay).setLight(light)
                        .setNormal(transform,points[p+3],points[p+4],points[p+5]);
                }
            });
        }
    }
    void hand(PoseStack stack,Pose pose,HumanoidArm arm) {
        int bone=Arrays.asList(names).indexOf(arm==HumanoidArm.RIGHT?"右手首":"左手首");
        var p=bind[bone];
        Matrix4f matrix=new Matrix4f().translate(0,(24*(1-pose.scale())+pose.shift())/16,0)
            .scale(pose.scale()/16).mul(pose.bones()[bone]).translate(p[0],p[1],p[2])
            .rotateZ((float)Math.toRadians(arm==HumanoidArm.RIGHT?55:-55)).scale(16);
        stack.mulPose(matrix);
        // Cancel vanilla's offset in its rotated (-90 X, 180 Y) frame,
        // then move half a model unit from the wrist into the palm.
        stack.translate(arm==HumanoidArm.RIGHT?1F/16:-1F/16,-9.5F/16,2F/16);
    }
}
