import be.smartschoolkatten.patcher.engine.BinaryResources;
import com.android.apksig.ApkVerifier;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Verify the exact pre-signed APK before publishing; no private key is needed. */
public final class ReleaseArtifactCheck {
    public static void main(String[] args)throws Exception {
        File apk=new File(args[0]);
        ApkVerifier.Result result=new ApkVerifier.Builder(apk).build().verify();
        if(!result.isVerified() || result.getSignerCertificates().size()!=1)throw new AssertionError("Invalid APK signature");
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(result.getSignerCertificates().get(0).getEncoded());
        StringBuilder cert=new StringBuilder();for(byte b:digest)cert.append(String.format(Locale.ROOT,"%02x",b&255));
        if(!cert.toString().equals(args[1]))throw new AssertionError("Wrong app signing identity");
        try(ZipFile zip=new ZipFile(apk);InputStream input=zip.getInputStream(zip.getEntry("AndroidManifest.xml"))) {
            Map<String,String> info=BinaryResources.manifestAttributes(input.readAllBytes());
            if(!args[2].equals(info.get("package")) || !args[3].equals(info.get("versionCode")))throw new AssertionError("Wrong release package or version");
        }
        System.out.println("PASS release APK signature, existing app identity, package and version");
    }
}
