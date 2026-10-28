import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Render the existing branding source into Android resources; keep its artwork intact. */
public final class BuildBranding {
    public static void main(String[] args)throws Exception {
        BufferedImage source=ImageIO.read(new File(args[0]));
        if(source==null || source.getWidth()!=source.getHeight())throw new IllegalArgumentException("Square branding source required");
        File directory=new File(args[1]);directory.mkdirs();
        int side=source.getWidth();
        // ic_smartschool_app_logo: viewport 48, corner radius 5.
        BufferedImage login=render(source,0,side,side*5.0/48.0);
        // splash_logo: viewport 240, logo at (64,64), size 112, radius 11.2.
        int inset=(int)Math.round(side*64.0/240.0),logoSide=side-2*inset;
        BufferedImage splash=render(source,inset,logoSide,logoSide*0.1);
        if((login.getRGB(0,0)>>>24)!=0 || (login.getRGB(side/2,side/2)>>>24)!=255)
            throw new AssertionError("Login logo mask invalid");
        if((splash.getRGB(inset,inset)>>>24)!=0 || (splash.getRGB(side/2,side/2)>>>24)!=255)
            throw new AssertionError("Splash logo mask invalid");
        if(login.getRGB(side/2,side/2)!=source.getRGB(side/2,side/2))throw new AssertionError("Source artwork changed");
        ImageIO.write(login,"png",new File(directory,"in-app-logo.png"));
        ImageIO.write(splash,"png",new File(directory,"splash-logo.png"));
        System.out.println("Kattenlogo’s gebouwd met originele hoekafronding en splashmarge");
    }
    static BufferedImage render(BufferedImage source,int inset,int size,double radius) {
        int side=source.getWidth();
        BufferedImage result=new BufferedImage(side,side,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setColor(Color.WHITE);
            graphics.fill(new RoundRectangle2D.Double(inset,inset,size,size,2*radius,2*radius));
            graphics.setComposite(AlphaComposite.SrcIn);
            graphics.drawImage(source,inset,inset,size,size,null);
        } finally {graphics.dispose();}
        return result;
    }
}
