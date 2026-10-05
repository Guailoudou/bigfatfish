package cn.deepmod.bigfatfish.client;

/** Checks the production bend's attachment, Jacobian normals and local section continuity. */
public final class FishHairBendCheck {
    private static void require(boolean test,String message) {
        if(!test) throw new AssertionError(message);
    }
    private static float[] point(float y,float nx,float ny,float nz) {
        return new float[]{.2F,y,.15F,nx,ny,nz};
    }
    public static void main(String[] args) {
        for(float[] range:new float[][]{{5.25F/16,7.05F/16},{4.4F/16,4.31F/16}}) {
            for(float pitch:new float[]{-.15F,0,.29F}) for(float sway:new float[]{-.31F,0,.31F}) {
                FishHairBend bend=new FishHairBend(range[0],range[1],pitch,sway);
                for(float y:new float[]{-1,0,range[0]}) {
                    float[] v=point(y,0,0,1), before=v.clone();bend.deform(v);
                    for(int i=0;i<6;i++) require(v[i]==before[i],"Head/root moved into face");
                }
                float previous=0;
                for(int i=1;i<=100;i++) {
                    float t=i/100F,y=range[0]+range[1]*t;
                    float[] v=point(y,0,0,1);bend.deform(v);
                    float displacement=(float)Math.hypot(v[0]-.2F,v[2]-.15F);
                    require(displacement+1e-7>=previous,"Bend reverses along the strand");previous=displacement;
                    float norm=(float)Math.sqrt(v[3]*v[3]+v[4]*v[4]+v[5]*v[5]);
                    require(Math.abs(norm-1)<1e-6,"Normal is not unit length");
                    // Compare the analytic normal to an independently differenced surface tangent.
                    float eps=1e-4F;
                    float[] a=point(y-eps,0,0,1), b=point(y+eps,0,0,1);
                    bend.deform(a);bend.deform(b);
                    float dot=((b[0]-a[0])*v[3]+(b[1]-a[1])*v[4]+(b[2]-a[2])*v[5])/(2*eps);
                    require(Math.abs(dot)<.0004F,"Normal does not follow the bent surface");
                    float[] side=v.clone();side[0]=.7F;side[1]=y;side[2]=.15F;
                    side[3]=0;side[4]=0;side[5]=1;bend.deform(side);
                    require(Math.abs(side[0]-v[0]-.5F)<1e-6 && Math.abs(side[2]-v[2])<1e-6,"A horizontal section twists or tears");
                    require(v[1]==y,"Bend shortened the hair into the head");
                }
                require(previous<=range[1]*.22F,"Hair excursion exceeds its bounded envelope");
            }
        }
        System.out.println("FishHairBend checks passed: both ages, fixed head/root, continuous growing bend, intact sections, Jacobian normals and bounded excursion.");
    }
}
