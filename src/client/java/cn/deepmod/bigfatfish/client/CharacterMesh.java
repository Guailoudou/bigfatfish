package cn.deepmod.bigfatfish.client;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.model.geom.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Vector3f;

/** Animated native bones with directly submitted surfaces, compatible with optimized cuboid renderers. */
final class CharacterMesh {
    /** Joint-space skinning; descendant offsets also cover sleeves and stockings. */
    record JointBend(float angle, float span, float y, float z, boolean rigid) {
        JointBend(float angle,float span,float y,float z) { this(angle,span,y,z,false); }
        JointBend asRigid() { return new JointBend(angle,span,y,z,true); }
        JointBend offset(ModelPart bone) {
            return new JointBend(angle, span, y+bone.y/16F, z+bone.z/16F,rigid);
        }
        float determinant(float localY,float localZ) {
            float py=localY+y;
            return !rigid && py>0 && py<span ? 1-angle/span*(localZ+z) : 1;
        }
        void deform(float[] vertex) {
            float py=vertex[1]+y, pz=vertex[2]+z;
            if((!rigid && py<=0) || angle==0) return;
            // Integrating the tangent creates an arc instead of collapsing
            // the inner knee through a blend of two rigid endpoint positions.
            float length=rigid ? span : Math.min(py,span), theta=angle*length/span;
            float c=(float)Math.cos(theta), s=(float)Math.sin(theta);
            float remainder=py-length;
            float ny=vertex[4]/determinant(vertex[1],vertex[2]), nz=vertex[5];
            vertex[1]=length*sinc(theta)+remainder*c-pz*s-y;
            vertex[2]=length*theta*cosc(theta)+remainder*s+pz*c-z;
            vertex[4]=ny*c-nz*s;vertex[5]=ny*s+nz*c;
            float normalLength=(float)Math.sqrt(vertex[3]*vertex[3]+vertex[4]*vertex[4]+vertex[5]*vertex[5]);
            if(normalLength>1e-8F) {
                vertex[3]/=normalLength;vertex[4]/=normalLength;vertex[5]/=normalLength;
            }
        }
    }
    private static float sinc(float angle) {
        float square=angle*angle;
        return Math.abs(angle)<.01F ? 1-square/6+square*square/120 : (float)Math.sin(angle)/angle;
    }
    private static float cosc(float angle) {
        float square=angle*angle;
        return Math.abs(angle)<.01F ? .5F-square/24+square*square/720 : (float)((1-Math.cos(angle))/square);
    }
    static float jointSpan(boolean juvenile) { return (juvenile ? 1.2F : 1.4F)/16F; }
    static void translateBentEnd(PoseStack pose,float angle,boolean juvenile) {
        float span=jointSpan(juvenile);
        // Applied after the bone rotation, this is the arc's distal offset
        // expressed in the final rigid frame, also used by held items.
        pose.translate(0,span*(sinc(angle)-1),-span*angle*cosc(angle));
    }
    record Surface(ModelPart bone, float[] vertices, FishHairBend hair, float jointSpan, boolean rigid, List<Surface> children) {
        void submit(PoseStack pose, SubmitNodeCollector collector, RenderType type, int light, int overlay, int color) {
            submit(pose,collector,type,light,overlay,color,null);
        }
        private void submit(PoseStack pose, SubmitNodeCollector collector, RenderType type, int light, int overlay, int color,
                            JointBend inheritedBend) {
            if (!bone.visible) return;
            pose.pushPose();
            // These lower-limb descendants have static, translation-only poses.
            // Leave translations on the stack; deform each vertex in the same
            // joint space, then return it to its own local coordinates.
            JointBend localBend=jointSpan>0 ? new JointBend(bone.xRot,jointSpan,0,0)
                : inheritedBend == null ? null : inheritedBend.offset(bone);
            JointBend bend=rigid && localBend!=null ? localBend.asRigid() : localBend;
            if (hair == null && bend == null) bone.translateAndRotate(pose);
            else pose.translate(bone.x / 16F, bone.y / 16F, bone.z / 16F);
            // Capture an immutable bend now: submission is deferred between entities.
            FishHairBend hairBend=hair==null ? null
                : new FishHairBend(hair.root(),hair.length(),bone.xRot,bone.yRot+bone.zRot);
            if (!bone.skipDraw && vertices.length > 0) collector.submitCustomGeometry(pose, type, (transform, buffer) -> {
                float[] bent=bend == null && hairBend == null ? null : new float[6];
                for (int i = 0; i < vertices.length; i += 8) {
                    float x=vertices[i], y=vertices[i+1], z=vertices[i+2];
                    float nx=vertices[i+5], ny=vertices[i+6], nz=vertices[i+7];
                    if (hairBend != null) {
                        bent[0]=x;bent[1]=y;bent[2]=z;bent[3]=nx;bent[4]=ny;bent[5]=nz;
                        hairBend.deform(bent);
                        x=bent[0];y=bent[1];z=bent[2];nx=bent[3];ny=bent[4];nz=bent[5];
                    }
                    if (bend != null) {
                        bent[0]=x;bent[1]=y;bent[2]=z;bent[3]=nx;bent[4]=ny;bent[5]=nz;
                        bend.deform(bent);
                        x=bent[0];y=bent[1];z=bent[2];nx=bent[3];ny=bent[4];nz=bent[5];
                    }
                    buffer.addVertex(transform, x, y, z)
                        .setColor(color).setUv(vertices[i+3], vertices[i+4]).setOverlay(overlay).setLight(light)
                        .setNormal(transform, nx, ny, nz);
                }
            });
            for (var child : children) child.submit(pose, collector, type, light, overlay, color, bend);
            pose.popPose();
        }
    }
    record Loaded(ModelPart root, ModelPart face, Surface bodySurface, Surface faceSurface) {}
    static Loaded load(boolean juvenile) {
        String path = "/assets/bigfatfish/models/entity/" + (juvenile ? "juvenile" : "adult") + ".mesh.json.gz";
        try (var raw = CharacterMesh.class.getResourceAsStream(path)) {
            if (raw == null) throw new IOException("Missing character mesh: " + path);
            try (var input = new InputStreamReader(new GZIPInputStream(raw), java.nio.charset.StandardCharsets.UTF_8)) {
                var face = new Surface[1];
                var root = read(JsonParser.parseReader(input).getAsJsonObject(), face, juvenile);
                return new Loaded(root.bone(), face[0].bone(), root, face[0]);
            }
        } catch (IOException e) { throw new IllegalStateException("Unable to load character", e); }
    }
    private static Surface read(JsonObject node, Surface[] face, boolean juvenile) {
        var quads = node.getAsJsonArray("quads");
        var vertices = new float[quads.size() * 4 * 8];
        for (int i = 0; i < quads.size(); i++) {
            var points = new Vector3f[4];
            for (int k = 0; k < 4; k++) {
                var v = quads.get(i).getAsJsonArray().get(k).getAsJsonArray();
                int offset = (i*4+k)*8;
                for (int j=0;j<5;j++) vertices[offset+j] = v.get(j).getAsFloat() / (j<3 ? 16F : 1F);
                points[k] = new Vector3f(vertices[offset],vertices[offset+1],vertices[offset+2]);
            }
            var normal = new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[2]).sub(points[0]));
            if (normal.lengthSquared() > 1e-12F) normal.normalize(); else normal.set(0,1,0);
            for(int k=0;k<4;k++) {
                int offset=(i*4+k)*8;
                var source=quads.get(i).getAsJsonArray().get(k).getAsJsonArray();
                if(source.size()>=8) {
                    for(int axis=0;axis<3;axis++) vertices[offset+5+axis]=source.get(5+axis).getAsFloat();
                } else {
                    vertices[offset+5]=normal.x;vertices[offset+6]=normal.y;vertices[offset+7]=normal.z;
                }
            }
        }
        var children = new LinkedHashMap<String,ModelPart>();
        var surfaces = new ArrayList<Surface>();
        for (var entry : node.getAsJsonArray("children")) {
            var child = entry.getAsJsonObject(); var part = read(child, face, juvenile);
            if (child.get("name").getAsString().equals("face")) face[0] = part;
            else { children.put(child.get("name").getAsString(), part.bone()); surfaces.add(part); }
        }
        var result = new ModelPart(List.of(), children); var pose = node.getAsJsonArray("pose");
        result.setInitialPose(PartPose.offset(pose.get(0).getAsFloat(),pose.get(1).getAsFloat(),pose.get(2).getAsFloat()));
        result.resetPose();
        FishHairBend hair=null;
        if (node.get("name").getAsString().startsWith("hair_")) {
            // Freeze the complete head zone. Hair bones have a nonzero local
            // origin, so use their actual pose instead of a fixed local weight.
            float root=(.25F-result.y)/16F;
            float end=root;
            for(int i=1;i<vertices.length;i+=8) end=Math.max(end,vertices[i]);
            hair=new FishHairBend(root,end-root,0,0);
        }
        String name=node.get("name").getAsString();
        if(name.equals("maid_body") || name.equals("summer_body")) {
            // Reuse the root-fixed bend for both skirts. Everything above the
            // waist, including the bodice and brooch, stays attached to the torso.
            float waist=(juvenile?1.85F:2.95F)/16F,hem=waist;
            for(int i=1;i<vertices.length;i+=8) hem=Math.max(hem,vertices[i]);
            hair=new FishHairBend(waist,hem-waist,0,0);
        }
        return new Surface(result, vertices, hair,
            name.equals("forearm") || name.equals("shin") ? jointSpan(juvenile) : 0, name.equals("shoe"), List.copyOf(surfaces));
    }
}
