package be.smartschoolkatten.runtime;
public final class CoverScale {
    private CoverScale() {}
    public static float factor(int targetWidth,int targetHeight,int sourceWidth,int sourceHeight) {
        return Math.max((float)targetWidth/sourceWidth,(float)targetHeight/sourceHeight);
    }
}
