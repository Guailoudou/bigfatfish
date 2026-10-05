package cn.deepmod.bigfatfish.client;

/** Captured, root-fixed secondary motion; independent of Minecraft and render state. */
record FishHairBend(float root, float length, float pitch, float sway) {
    void deform(float[] vertex) {
        if (length<=0 || vertex[1]<=root) return;
        float t=Math.clamp((vertex[1]-root)/length,0F,1F);
        // The displacement and its slope both vanish at the attachment.
        // Small bounded shears keep every horizontal section intact, without
        // swinging the crown or shortening a strand into the face.
        float distance=length*.5F*t*t+Math.max(0,vertex[1]-root-length);
        vertex[0]+=sway*distance;
        vertex[2]+=pitch*distance;
        // Inverse-transpose of the deformation Jacobian, not a blended rotation.
        vertex[4]-=t*(sway*vertex[3]+pitch*vertex[5]);
        float norm=(float)Math.sqrt(vertex[3]*vertex[3]+vertex[4]*vertex[4]+vertex[5]*vertex[5]);
        if(norm>1e-8F) {
            vertex[3]/=norm;vertex[4]/=norm;vertex[5]/=norm;
        }
    }
}
