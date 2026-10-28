import be.smartschoolkatten.runtime.CoverScale;
public final class CoverScaleTest {
    public static void main(String[] args) {
        for(int[] dimensions:new int[][]{{80,80,100,400},{80,80,400,100},{120,120,60,60},{120,120,240,480},{80,100,200,50}}) {
            float scale=CoverScale.factor(dimensions[0],dimensions[1],dimensions[2],dimensions[3]);
            if(dimensions[2]*scale<dimensions[0] || dimensions[3]*scale<dimensions[1])throw new AssertionError("Photo does not cover target");
            if(dimensions[0]==dimensions[1] && Math.abs(Math.min(dimensions[2],dimensions[3])*scale-dimensions[0])>0.01)throw new AssertionError("Shortest side does not match diameter");
        }
        // A shared cat is reused in small message rows and larger menu avatars.
        // Cover must keep filling the target as the view changes size.
        for(int diameter:new int[]{16,24,32,40,48,64,80,96,120,160,256})
            for(int[] source:new int[][]{{73,419},{419,73},{17,1024},{1024,17},{512,512}}) {
                float scale=CoverScale.factor(diameter,diameter,source[0],source[1]);
                if(Math.ceil(source[0]*scale)<diameter || Math.ceil(source[1]*scale)<diameter)
                    throw new AssertionError("Photo leaves space in diameter "+diameter);
            }
        System.out.println("PASS cover scaling for 11 avatar diameters, portrait, landscape and square sources");
    }
}
