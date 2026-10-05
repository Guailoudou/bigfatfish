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
            require(Float.isFinite(a[0]) && a[0]>=-.120001F && a[0]<=.260001F,"Pitch unstable");
            require(Float.isFinite(a[1]) && Math.abs(a[1])<=.280001F,"Hair yaw unstable");
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
        float expected=-(float)Math.toRadians(2)*1.8F*.14F*.80F;
        require(Math.abs(wrap.hairYaw.sample(1)-expected)<1e-7,"Yaw crossing 180 degrees produced a large impulse");
        FishMotion walking=new FishMotion();
        walking.update(0,0,0,0,0,false,false);
        for(int i=1;i<=30;i++) walking.update(i,0,0,i*.22,0,false,false);
        require(walking.hairPitch.sample(1)>.10F,"Walking hair motion is imperceptible");
        FishMotion turning=new FishMotion();
        turning.update(0,0,0,0,0,false,false);
        for(int i=1;i<=8;i++) turning.update(i,0,0,0,i*15,false,false);
        require(Math.abs(turning.hairYaw.sample(1))>.15F,"Turning hair motion is imperceptible");
        boolean hairRebound=false;
        for(int i=9;i<=250;i++) {
            turning.update(i,0,0,0,120,false,false);
            hairRebound|=turning.hairYaw.sample(1)>.005F;
        }
        require(hairRebound,"Hair did not rebound after turning stopped");
        require(Math.abs(turning.hairYaw.sample(1))<1e-7,"Hair did not settle after rebound");
        FishMotion idle=new FishMotion(123),idleOther=new FishMotion(456),repeated=new FishMotion(123);
        boolean left=false,right=false,different=false;
        for(int tick=0;tick<2000;tick++) {
            idle.update(tick,0,0,0,0,false,false);
            idleOther.update(tick,0,0,0,0,false,false);
            for(int frame=0;frame<8;frame++) repeated.update(tick,0,0,0,0,false,false);
            float value=idle.tailIdle.sample(1);
            left|=value<-.1F;right|=value>.1F;
            different|=Math.abs(value-idleOther.tailIdle.sample(1))>.1F;
            require(value==repeated.tailIdle.sample(1),"Rendering frames advance random tail motion");
            require(Math.abs(value)<=.320001F,"Random tail leaves its safe range");
        }
        require(left && right && different,"Idle tail must sway in both directions independently per entity");
        require(walking.clothPitch.sample(1)>.1F,"Walking must move the skirt hem");
        for(int tick=31;tick<=250;tick++) walking.update(tick,0,0,30*.22,0,false,false);
        require(Math.abs(walking.clothPitch.sample(1))<1e-7,"Cloth must settle after walking stops");
        idle.update(2000,80,0,0,0,false,false);
        require(idle.tailIdle.sample(1)==0 && idle.clothPitch.sample(1)==0,"Teleport must reset tail and cloth");
        System.out.println("FishMotion checks passed: bounded/settling springs, elastic return, frame-independent extraction, entity isolation, teleport/age/gap reset, wrapped yaw.");
    }
}
