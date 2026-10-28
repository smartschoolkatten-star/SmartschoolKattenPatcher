package be.smartschoolkatten.patcher.engine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.cert.*;
import java.math.BigInteger;

/** A private, persistent signing identity belonging to this patcher installation. */
public final class SigningIdentity {
    public final PrivateKey key;
    public final X509Certificate certificate;
    private SigningIdentity(PrivateKey key, X509Certificate certificate) {this.key=key;this.certificate=certificate;}
    public static synchronized SigningIdentity load(File directory) throws Exception {
        directory.mkdirs(); File k=new File(directory,"signing.pk8"), c=new File(directory,"signing.der");
        if (!k.exists() && !c.exists()) {
            KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);KeyPair pair=generator.generateKeyPair();
            byte[] algorithm=hex("300d06092a864886f70d01010b0500");
            byte[] name=der(0x30,der(0x31,der(0x30,concat(hex("0603550403"),der(0x0c,"Smartschool Katten".getBytes(StandardCharsets.UTF_8))))));
            byte[] validity=der(0x30,concat(der(0x17,"260101000000Z".getBytes(StandardCharsets.US_ASCII)),der(0x17,"460101000000Z".getBytes(StandardCharsets.US_ASCII))));
            byte[] serial=new BigInteger(120,new SecureRandom()).add(BigInteger.ONE).toByteArray();
            byte[] tbs=der(0x30,concat(der(0xa0,der(2,new byte[]{2})),der(2,serial),algorithm,name,validity,name,pair.getPublic().getEncoded()));
            Signature signature=Signature.getInstance("SHA256withRSA");signature.initSign(pair.getPrivate());signature.update(tbs);
            byte[] cert=der(0x30,concat(tbs,algorithm,der(3,concat(new byte[]{0},signature.sign()))));
            Files.write(k.toPath(),pair.getPrivate().getEncoded());Files.write(c.toPath(),cert);
        }
        PrivateKey key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(k.toPath())));
        X509Certificate certificate=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(Files.readAllBytes(c.toPath())));
        return new SigningIdentity(key,certificate);
    }
    private static byte[] der(int tag,byte[] data) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(tag);
        if(data.length<128)out.write(data.length);else{int bytes=0;for(int n=data.length;n>0;n>>>=8)bytes++;out.write(128|bytes);for(int i=bytes-1;i>=0;i--)out.write(data.length>>>(8*i));}
        out.write(data);return out.toByteArray();
    }
    private static byte[] concat(byte[]... items)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();for(byte[] x:items)b.write(x);return b.toByteArray();}
    private static byte[] hex(String s){byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
}
