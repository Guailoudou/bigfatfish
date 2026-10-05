package cn.deepmod.bigfatfish.client;

/** Runs the production spring without Minecraft or a rendering context. */
public final class FishMotionCheck {
    private static void require(boolean condition,String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static float[] values(FishMotion motion,float partialTick) {
        return new float[]{motion.hairPitch.sample(partialTick),motion.hairYaw.sample(partialTick),motion.tailYaw.sample(partialTick)};
    }
    private static void zero(FishMotion motion,String message) {
        for (float alpha:new float[]{0,.5F,1})
            for(float value:values(motion,alpha)) require(value==0,message);
    }
    public static void main(String[] args) {
        FishMotion once=new FishMotion(), many=new FishMotion(), other=new FishMotion();
        once.update(0,0,0,0,0,false,false);
        many.update(0,0,0,0,0,false,false);
        other.update(0,0,0,0,0,false,false);
        double x=0,z=0;
        for(int tick=1;tick<=4000;tick++) {
            x+=Math.sin(tick*.08)*.11; z+=Math.cos(tick*.06)*.12;
            float yaw=(float)(Math.sin(tick*.05)*179);
            once.update(tick,x,0,z,yaw,false,false);
            for(int frame=0;frame<12;frame++) {
                many.update(tick,x,0,z,yaw,false,false);
                values(many,frame/12F);
            }
            float[] a=values(once,1),b=values(many,1);
            for(int i=0;i<3;i++) require(a[i]==b[i],"Repeated frame extraction advanced the spring");
            require(Float.isFinite(a[0]) && a[0]>=-.020001F && a[0]<=.050001F,"Pitch unstable");
            require(Float.isFinite(a[1]) && Math.abs(a[1])<=.035001F,"Hair yaw unstable");
            require(Float.isFinite(a[2]) && Math.abs(a[2])<=.140001F,"Tail yaw unstable");
        }
        zero(other,"Motion leaked between entities");
        for(int tick=4001;tick<=4400;tick++) once.update(tick,x,0,z,0,false,false);
        for(float value:values(once,1)) require(Math.abs(value)<1e-7,"Spring did not settle");

        FishMotion.Spring spring=new FishMotion.Spring(.10F,.82F,-.14F,.14F);
        for(int i=0;i<30;i++) spring.step(.08F);
        boolean rebounded=false;
        for(int i=0;i<150;i++) { spring.step(0); rebounded|=spring.sample(1)<-.001F; }
        require(rebounded,"Tail has no elastic return after release");
        require(Math.abs(spring.sample(1))<1e-7,"Tail rebound did not settle");

        many.update(4001,x+80,0,z,140,false,false);
        zero(many,"Teleport retained stale motion");
        many.update(4002,x+80.2,0,z,150,false,false);
        require(Math.abs(many.hairYaw.sample(1))>0,"Turn did not drive motion");
        many.update(4002,x+80.2,0,z,150,true,false);
        zero(many,"Age/model transition retained stale motion");
        many.update(4012,x+80.2,0,z,150,true,false);
        zero(many,"Long extraction gap retained stale motion");

        FishMotion wrap=new FishMotion();
        wrap.update(0,0,0,0,179,false,false);
        wrap.update(1,0,0,0,-179,false,false);
        float expected=-(float)Math.toRadians(2)*.65F*.18F*.78F;
        require(Math.abs(wrap.hairYaw.sample(1)-expected)<1e-7,"Yaw crossing 180 degrees produced a large impulse");
        System.out.println("FishMotion checks passed: bounded/settling springs, elastic return, frame-independent extraction, entity isolation, teleport/age/gap reset, wrapped yaw.");
    }
}
