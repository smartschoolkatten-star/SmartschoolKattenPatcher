package be.smartschoolkatten.patcher.engine;
import java.io.*;import java.nio.file.*;import java.util.*;import com.android.apksig.*;
public class SplitSetTest {
 static void reject(List<File> files,String label)throws Exception {try{ApkSet.validate(files);throw new AssertionError(label);}catch(IOException expected){System.out.println("PASS reject "+label+": "+expected.getMessage());}}
 public static void main(String[]args)throws Exception {
  File d=new File(args[0]),base=new File(d,"base.apk"),abi=new File(d,"abi.apk"),density=new File(d,"density.apk");
  List<File> originals=Arrays.asList(base,abi,density);
  if(!ApkSet.validate(originals).equals(base))throw new AssertionError("base");
  reject(Arrays.asList(base),"missing ABI and density");reject(Arrays.asList(base,abi),"missing density");
  reject(Arrays.asList(base,abi,density,abi),"duplicate split");reject(Arrays.asList(base,abi,density,new File(d,"wrong.apk")),"different version");
  if(args.length>1) reject(Arrays.asList(new File(args[1])),"actual downloaded incomplete APK");
  File archive=new File(d,"source.apks");ApkSet.pack(originals,archive);
  List<File> unpacked=ApkSet.unpack(archive,new File(d,"unpacked"));
  if(unpacked.size()!=3)throw new AssertionError("unpack");
  KattenPatcher engine=new KattenPatcher();List<File> signed=new ArrayList<>();byte[] cert=null;
  for(File part:originals) {
   File out=new File(d,"signed-"+part.getName());engine.patchSplit(part,out,new File(d,"keys"));
   if(!KattenPatcher.PACKAGE.equals(ApkSet.info(out).get("package")))throw new AssertionError("package");
   ApkVerifier.Result verified=new ApkVerifier.Builder(out).setMinCheckedPlatformVersion(32).build().verify();
   if(!verified.isVerified())throw new AssertionError("signature");
   byte[] next=verified.getSignerCertificates().get(0).getEncoded();if(cert!=null&&!Arrays.equals(cert,next))throw new AssertionError("certificate mismatch");cert=next;signed.add(out);
  }
  File output=new File(d,"signed.apks");ApkSet.pack(signed,output);ApkSet.unpack(output,new File(d,"signed-unpacked"));
  System.out.println("PASS complete set roundtrip, package rename, signatures and identical certificates");
 }
}
