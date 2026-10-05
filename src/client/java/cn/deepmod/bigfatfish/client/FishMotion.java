package cn.deepmod.bigfatfish.client;

/** Client-only secondary motion, advanced once per entity tick rather than per frame. */
final class FishMotion {
    final Spring hairPitch = new Spring(.14F, .80F, -.12F, .26F);
    final Spring hairYaw = new Spring(.14F, .80F, -.28F, .28F);
    final Spring tailYaw = new Spring(.10F, .82F, -.14F, .14F);
    private boolean initialized, juvenile;
    private int lastTick;
    private double lastX, lastY, lastZ, lastVx, lastVz;
    private float lastYaw;

    void update(int tick, double x, double y, double z, float yaw, boolean baby, boolean sitting) {
        double dx=x-lastX, dy=y-lastY, dz=z-lastZ;
        int elapsed=tick-lastTick;
        if (!initialized || baby!=juvenile || elapsed<0 || elapsed>4 || dx*dx+dy*dy+dz*dz>4) {
            initialized=true; juvenile=baby;
            hairPitch.reset(); hairYaw.reset(); tailYaw.reset();
            lastVx=lastVz=0;
        } else {
            if (elapsed==0) return;
            double vx=dx/elapsed, vz=dz/elapsed;
            float turn=wrapDegrees(yaw-lastYaw)/elapsed*(float)(Math.PI/180);
            double angle=yaw*Math.PI/180, sin=Math.sin(angle), cos=Math.cos(angle);
            double ax=(vx-lastVx)/elapsed, az=(vz-lastVz)/elapsed;
            float forward=(float)(-sin*vx+cos*vz);
            float acceleration=(float)(-sin*ax+cos*az);
            float sideways=(float)(cos*ax+sin*az);
            float amount=sitting?.3F:1F;
            for (int i=0;i<elapsed;i++) {
                hairPitch.step((forward*.65F+acceleration*1.4F)*amount);
                hairYaw.step((-turn*1.8F-sideways*.7F)*amount);
                tailYaw.step((-turn*.9F-sideways*.35F)*amount);
            }
            lastVx=vx; lastVz=vz;
        }
        lastTick=tick; lastX=x; lastY=y; lastZ=z; lastYaw=yaw;
    }

    private static float wrapDegrees(float degrees) {
        float result=degrees%360;
        return result>=180 ? result-360 : result<-180 ? result+360 : result;
    }

    static final class Spring {
        private final float stiffness, damping, minimum, maximum;
        private float previous, angle, velocity;
        Spring(float stiffness,float damping,float minimum,float maximum) {
            this.stiffness=stiffness; this.damping=damping; this.minimum=minimum; this.maximum=maximum;
        }
        void step(float target) {
            previous=angle;
            target=Math.clamp(target,minimum,maximum);
            velocity=(velocity+(target-angle)*stiffness)*damping;
            angle+=velocity;
            if (angle<minimum || angle>maximum) {
                angle=Math.clamp(angle,minimum,maximum); velocity=0;
            }
        }
        float sample(float partialTick) {
            return previous+(angle-previous)*Math.clamp(partialTick,0F,1F);
        }
        void reset() { previous=angle=velocity=0; }
    }
}
