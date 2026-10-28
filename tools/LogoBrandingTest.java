import be.smartschoolkatten.patcher.engine.BinaryResources;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.*;
import java.util.zip.ZipFile;

/** Verify actual patched package resources for login, About and both splash routes. */
public final class LogoBrandingTest {
    public static void main(String[] args)throws Exception {
        try(ZipFile source=new ZipFile(args[0]);ZipFile patched=new ZipFile(args[1])) {
            Set<String> strings=new HashSet<>();
            BinaryResources.rewrite(read(patched,"resources.arsc"),s->{strings.add(s);return s;},null);
            String[] paths={"res/drawable/katten_app_logo.png","res/drawable/katten_splash_logo.png","res/drawable/katten_splash_logo_animated.png"};
            for(String path:paths) {
                if(!strings.contains(path))throw new AssertionError("Resource table missing "+path);
                byte[] bytes=read(patched,path);
                String asset=path.equals(paths[0])?"in-app-logo.png":"splash-logo.png";
                if(!Arrays.equals(bytes,java.nio.file.Files.readAllBytes(new File(args[2],asset).toPath())))
                    throw new AssertionError("Branding resource differs from generated asset");
                BufferedImage image=ImageIO.read(new ByteArrayInputStream(bytes));
                if(image==null || image.getWidth()!=1024 || image.getHeight()!=1024 || !image.getColorModel().hasAlpha())
                    throw new AssertionError("Invalid logo image");
                if((image.getRGB(0,0)>>>24)!=0 || (image.getRGB(512,512)>>>24)!=255)
                    throw new AssertionError("Logo corners/centre invalid");
            }
            for(String path:new String[]{"res/drawable/ic_smartschool_app_logo.xml","res/drawable/splash_logo.xml","res/drawable/splash_logo_animated.xml"})
                if(strings.contains(path) || patched.getEntry(path)!=null)throw new AssertionError("Original logo still referenced: "+path);
            for(String path:new String[]{"res/layout/activity_add_account.xml","res/layout/activity_splash.xml"})
                if(!Arrays.equals(read(source,path),read(patched,path)))throw new AssertionError("Logo layout geometry changed");
            System.out.println("PASS login/About shared logo and both splash resources: high-resolution source, alpha corners, original layout/resource references");
        }
    }
    static byte[] read(ZipFile zip,String path)throws IOException {
        if(zip.getEntry(path)==null)throw new AssertionError("Missing "+path);
        try(InputStream input=zip.getInputStream(zip.getEntry(path))){return input.readAllBytes();}
    }
}
