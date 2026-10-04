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
    record Surface(ModelPart bone, float[] vertices, List<Surface> children) {
        void submit(PoseStack pose, SubmitNodeCollector collector, RenderType type, int light, int overlay, int color) {
            if (!bone.visible) return;
            pose.pushPose();
            bone.translateAndRotate(pose);
            if (!bone.skipDraw && vertices.length > 0) collector.submitCustomGeometry(pose, type, (transform, buffer) -> {
                for (int i = 0; i < vertices.length; i += 8) {
                    buffer.addVertex(transform, vertices[i], vertices[i+1], vertices[i+2])
                        .setColor(color).setUv(vertices[i+3], vertices[i+4]).setOverlay(overlay).setLight(light)
                        .setNormal(transform, vertices[i+5], vertices[i+6], vertices[i+7]);
                }
            });
            for (var child : children) child.submit(pose, collector, type, light, overlay, color);
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
                var root = read(JsonParser.parseReader(input).getAsJsonObject(), face);
                return new Loaded(root.bone(), face[0].bone(), root, face[0]);
            }
        } catch (IOException e) { throw new IllegalStateException("Unable to load character", e); }
    }
    private static Surface read(JsonObject node, Surface[] face) {
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
                vertices[offset+5]=normal.x;vertices[offset+6]=normal.y;vertices[offset+7]=normal.z;
            }
        }
        var children = new LinkedHashMap<String,ModelPart>();
        var surfaces = new ArrayList<Surface>();
        for (var entry : node.getAsJsonArray("children")) {
            var child = entry.getAsJsonObject(); var part = read(child, face);
            if (child.get("name").getAsString().equals("face")) face[0] = part;
            else { children.put(child.get("name").getAsString(), part.bone()); surfaces.add(part); }
        }
        var result = new ModelPart(List.of(), children); var pose = node.getAsJsonArray("pose");
        result.setInitialPose(PartPose.offset(pose.get(0).getAsFloat(),pose.get(1).getAsFloat(),pose.get(2).getAsFloat()));
        result.resetPose();
        return new Surface(result, vertices, List.copyOf(surfaces));
    }
}
