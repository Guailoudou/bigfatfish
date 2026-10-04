package cn.deepmod.bigfatfish.client;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.model.geom.*;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

/** Adapter for generated quad surfaces. Uses the public native polygon API without reflection. */
final class CharacterMesh {
    record Loaded(ModelPart root, ModelPart face) {}
    static Loaded load(boolean juvenile) {
        String path = "/assets/bigfatfish/models/entity/" + (juvenile ? "juvenile" : "adult") + ".mesh.json.gz";
        try (var raw = CharacterMesh.class.getResourceAsStream(path)) {
            if (raw == null) throw new IOException("Missing character mesh: " + path);
            try (var input = new InputStreamReader(new GZIPInputStream(raw), java.nio.charset.StandardCharsets.UTF_8)) {
                var face = new ModelPart[1];
                return new Loaded(read(JsonParser.parseReader(input).getAsJsonObject(), face), face[0]);
            }
        } catch (IOException e) { throw new IllegalStateException("Unable to load character", e); }
    }
    private static ModelPart read(JsonObject node, ModelPart[] face) {
        var cubes = new ArrayList<ModelPart.Cube>();
        var quads = node.getAsJsonArray("quads");
        for (int i = 0; i < quads.size(); i += 6) {
            // The six native polygon slots accept arbitrary quads, not just a cuboid's faces.
            var cube = new ModelPart.Cube(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 1, 1, EnumSet.allOf(Direction.class));
            for (int j = 0; j < 6; j++) {
                var vertices = new ModelPart.Vertex[4];
                for (int k = 0; k < 4; k++) {
                    var v = i+j < quads.size() ? quads.get(i+j).getAsJsonArray().get(k).getAsJsonArray() : null;
                    vertices[k] = v == null ? new ModelPart.Vertex(0,0,0,0,0) : new ModelPart.Vertex(v.get(0).getAsFloat(),v.get(1).getAsFloat(),v.get(2).getAsFloat(),v.get(3).getAsFloat(),v.get(4).getAsFloat());
                }
                var a = vertices[0]; var b = vertices[1]; var c = vertices[2];
                var normal = new Vector3f(b.x()-a.x(),b.y()-a.y(),b.z()-a.z()).cross(c.x()-a.x(),c.y()-a.y(),c.z()-a.z());
                if (normal.lengthSquared() > 1e-12F) normal.normalize(); else normal.set(0,1,0);
                cube.polygons[j] = new ModelPart.Polygon(vertices, normal);
            }
            cubes.add(cube);
        }
        var children = new LinkedHashMap<String,ModelPart>();
        for (var entry : node.getAsJsonArray("children")) {
            var child = entry.getAsJsonObject(); var part = read(child, face);
            if (child.get("name").getAsString().equals("face")) face[0] = part;
            else children.put(child.get("name").getAsString(), part);
        }
        var result = new ModelPart(cubes, children); var pose = node.getAsJsonArray("pose");
        result.setInitialPose(PartPose.offset(pose.get(0).getAsFloat(),pose.get(1).getAsFloat(),pose.get(2).getAsFloat()));
        result.resetPose();
        return result;
    }
}
