import java.lang.reflect.Method;

/** Run on Android with the real host APK on the classpath; no account data read. */
public final class ComposeScaleDeviceTest {
    static long size(float width,float height) {
        return ((long)Float.floatToRawIntBits(width)<<32) | (Float.floatToRawIntBits(height)&0xffffffffL);
    }
    public static void main(String[] args)throws Exception {
        Class<?> scales=Class.forName("qz1");
        Object fit=scales.getField("b").get(null),crop=scales.getField("a").get(null);
        Method compute=Class.forName("sz1").getMethod("c",long.class,long.class);
        int cases=0;
        for(int diameter:new int[]{24,32,40,48,64,80,96,120,160})
            for(int[] source:new int[][]{{73,419},{419,73},{512,512}}) {
                long result=(Long)compute.invoke(crop,size(source[0],source[1]),size(diameter,diameter));
                float sx=Float.intBitsToFloat((int)(result>>32)),sy=Float.intBitsToFloat((int)result);
                if(source[0]*sx<diameter-0.001f || source[1]*sy<diameter-0.001f)
                    throw new AssertionError("Actual host Crop leaves space at diameter "+diameter);
                cases++;
            }
        long fitResult=(Long)compute.invoke(fit,size(400,200),size(80,80));
        long cropResult=(Long)compute.invoke(crop,size(400,200),size(80,80));
        float fitY=Float.intBitsToFloat((int)fitResult),cropY=Float.intBitsToFloat((int)cropResult);
        if(Math.abs(200*fitY-40)>0.001f || Math.abs(200*cropY-80)>0.001f)
            throw new AssertionError("Expected letterbox reproduction failed");
        System.out.println("PASS actual host Fit draws a 400x200 photo at 80x40 inside 80x80; Crop fills 80x80");
        System.out.println("PASS actual host Crop fills "+cases+" avatar sizes/aspect ratios");
    }
}
