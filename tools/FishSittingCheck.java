/** Numeric check of the thigh rotation and arc-skinned knee, without Minecraft. */
public final class FishSittingCheck {
    public static void main(String[] args) {
        float bend=1.3F;
        // Adult, original normalized juvenile and revised Blockbench juvenile thigh lengths.
        float[][] cases={{5.6F,1.4F},{1.734F,1.2F},{2.78F,1.2F}};
        if(args.length==2) {
            cases[0][0]=Float.parseFloat(args[0]);
            cases[2][0]=Float.parseFloat(args[1]);
        }
        double maximumError=0;
        for(float[] parameters:cases) {
            float thigh=parameters[0], span=parameters[1];
            float shift=thigh*(1-(float)Math.cos(bend))+span*(1-(float)Math.sin(bend)/bend);
            // Independently integrate the actual knee centreline, then rotate
            // that arc and a rigid distal shoe back through the thigh rotation.
            double arcY=0,arcZ=0;
            int samples=16384;
            for(int i=0;i<samples;i++) {
                double theta=bend*(i+.5)/samples;
                arcY+=Math.cos(theta)*span/samples;
                arcZ+=Math.sin(theta)*span/samples;
            }
            for(double distal:new double[]{span+.05,3,5.4}) {
                for(double soleZ:new double[]{-1.8,-.43,.8}) {
                    double kneeY=arcY+(distal-span)*Math.cos(bend)-soleZ*Math.sin(bend);
                    double kneeZ=arcZ+(distal-span)*Math.sin(bend)+soleZ*Math.cos(bend);
                    double sittingY=shift+thigh*Math.cos(bend)+kneeY*Math.cos(bend)+kneeZ*Math.sin(bend);
                    double standingY=thigh+distal;
                    double error=Math.abs(sittingY-standingY);
                    maximumError=Math.max(maximumError,error);
                    if(error>1e-6) throw new AssertionError("Sole moved vertically: "+error);
                }
            }
            System.out.printf("thigh=%.3f span=%.1f sittingShift=%.6f%n",thigh,span,shift);
        }
        System.out.println("Sole height checks passed (27 distal-length/depth combinations), maximum error="+maximumError);
    }
}
