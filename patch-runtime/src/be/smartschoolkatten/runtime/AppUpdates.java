package be.smartschoolkatten.runtime;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.widget.Toast;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;

/** Public APK releases; app use never depends on GitHub being reachable. */
final class AppUpdates {
    private static final String API="https://api.github.com/repos/"+UpdatePolicy.REPOSITORY;
    private static final Set<String> HOSTS=new HashSet<>(Arrays.asList("api.github.com","github.com","release-assets.githubusercontent.com","objects.githubusercontent.com"));
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final AtomicBoolean busy=new AtomicBoolean();
    private static final Handler main=new Handler(Looper.getMainLooper());
    private AppUpdates() {}
    static void check(Activity owner,boolean manual) {
        SharedPreferences state=owner.getSharedPreferences("katten-updates",Context.MODE_PRIVATE);
        long now=System.currentTimeMillis();
        if(!manual && now-state.getLong("last-check",0)<24*60*60*1000L)return;
        if(!busy.compareAndSet(false,true)) {if(manual)toast(owner,"Updatecontrole is al bezig");return;}
        state.edit().putLong("last-check",now).apply();
        WeakReference<Activity> reference=new WeakReference<>(owner);
        Context app=owner.getApplicationContext();
        if(manual)toast(owner,"Zoeken naar updates…");
        worker.execute(()->{
            try {
                JSONObject release=request(API+"/releases/latest","application/vnd.github+json",connection->{
                    if(connection.getResponseCode()==404)return null;
                    return new JSONObject(new String(read(connection.getInputStream(),1024*1024),java.nio.charset.StandardCharsets.UTF_8));
                });
                long installed=app.getPackageManager().getPackageInfo(app.getPackageName(),0).getLongVersionCode();
                if(release==null || release.optBoolean("draft") || release.optBoolean("prerelease") || !UpdatePolicy.newer(release.optString("tag_name"),installed)) {
                    if(manual)main.post(()->{Activity a=live(reference);if(a!=null)toast(a,"Je hebt de nieuwste beschikbare versie");});
                    return;
                }
                JSONArray assets=release.getJSONArray("assets");JSONObject apk=null;
                for(int i=0;i<assets.length();i++)if(UpdatePolicy.ASSET.equals(assets.getJSONObject(i).optString("name"))) {
                    if(apk!=null)throw new IOException("De release bevat meerdere installatiebestanden");
                    apk=assets.getJSONObject(i);
                }
                if(apk==null)throw new IOException("De nieuwe release bevat nog geen installatiebestand");
                final JSONObject asset=apk;
                main.post(()->{
                    Activity a=live(reference);if(a==null)return;
                    new AlertDialog.Builder(a).setTitle("Update beschikbaar")
                        .setMessage("Er is een nieuwe versie van Smartschool Katten ("+release.optString("tag_name")+").")
                        .setNegativeButton("Later",null).setPositiveButton("Downloaden",(dialog,which)->download(a,asset,UpdatePolicy.version(release.optString("tag_name")))).show();
                });
            } catch(Exception error) {if(manual)main.post(()->{Activity a=live(reference);if(a!=null)toast(a,"Updates controleren mislukt. Probeer later opnieuw.");});}
            finally {busy.set(false);}
        });
    }
    private static void download(Activity owner,JSONObject asset,long version) {
        if(!busy.compareAndSet(false,true)){toast(owner,"Er is al een download bezig");return;}
        WeakReference<Activity> reference=new WeakReference<>(owner);Context app=owner.getApplicationContext();
        toast(owner,"Update downloaden…");
        worker.execute(()->{
            File target=null;
            try {
                long size=asset.getLong("size");String expected=asset.optString("digest");
                if(size<1 || size>512L*1024*1024 || !expected.matches("sha256:[a-f0-9]{64}"))throw new IOException("Ongeldige releasegegevens");
                File directory=new File(app.getCacheDir(),"katten-updates");directory.mkdirs();
                File[] old=directory.listFiles();if(old!=null)for(File file:old)file.delete();
                target=File.createTempFile("update-",".apk",directory);final File destination=target;
                request(API+"/releases/assets/"+asset.getLong("id"),"application/octet-stream",connection->{
                    try(InputStream input=connection.getInputStream();OutputStream output=new FileOutputStream(destination)) {
                        byte[] buffer=new byte[65536];long total=0;int n;
                        while((n=input.read(buffer))!=-1){total+=n;if(total>size)throw new IOException("Download te groot");output.write(buffer,0,n);}
                        if(total!=size)throw new IOException("Onvolledige download");
                    }return null;
                });
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                try(InputStream input=new FileInputStream(target)){byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1)digest.update(buffer,0,n);}
                StringBuilder actual=new StringBuilder("sha256:");for(byte value:digest.digest())actual.append(String.format(Locale.ROOT,"%02x",value&255));
                if(!expected.equals(actual.toString()))throw new IOException("Downloadcontrole mislukt");
                verify(app,target,version);
                File ready=target;target=null;
                main.post(()->{
                    Activity a=live(reference);
                    if(a!=null)a.startActivity(new Intent(a,UpdateActivity.class).putExtra("apk",ready.getAbsolutePath()).putExtra("version",version));
                    else ready.delete();
                });
            } catch(Exception error) {main.post(()->{Activity a=live(reference);if(a!=null)toast(a,"Update downloaden of controleren mislukt. Probeer opnieuw.");});}
            finally {if(target!=null)target.delete();busy.set(false);}
        });
    }
    static void verify(Context context,File file,long expectedVersion)throws Exception {
        PackageManager pm=context.getPackageManager();
        PackageInfo installed=pm.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo candidate=pm.getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
        if(candidate==null || !installed.packageName.equals(candidate.packageName) || candidate.getLongVersionCode()!=expectedVersion
            || candidate.getLongVersionCode()<=installed.getLongVersionCode() || candidate.signingInfo==null || installed.signingInfo==null
            || !new HashSet<>(Arrays.asList(candidate.signingInfo.getApkContentsSigners())).equals(new HashSet<>(Arrays.asList(installed.signingInfo.getApkContentsSigners()))))
            throw new IOException("De update hoort niet bij deze installatie");
    }
    private interface Reader<T> {T read(HttpsURLConnection connection)throws Exception;}
    private static <T>T request(String address,String accept,Reader<T> reader)throws Exception {
        URL url=new URL(address);
        for(int redirects=0;redirects<6;redirects++) {
            if(!"https".equals(url.getProtocol()) || !HOSTS.contains(url.getHost()) || url.getUserInfo()!=null || (url.getPort()!=-1 && url.getPort()!=443))throw new IOException("Ongeldige update-URL");
            HttpsURLConnection connection=(HttpsURLConnection)url.openConnection();connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);connection.setReadTimeout(30000);connection.setRequestProperty("User-Agent","Smartschool-Katten");connection.setRequestProperty("Accept",accept);
            try {
                int code=connection.getResponseCode();
                if(Arrays.asList(301,302,303,307,308).contains(code)) {
                    String location=connection.getHeaderField("Location");if(location==null)throw new IOException("Updateverwijzing ontbreekt");url=new URL(url,location);
                } else if(code==404 && accept.equals("application/vnd.github+json"))return reader.read(connection);
                else {if(code<200 || code>=300)throw new IOException("GitHub HTTP "+code);return reader.read(connection);}
            } finally {connection.disconnect();}
        }
        throw new IOException("Te veel updateverwijzingen");
    }
    private static byte[] read(InputStream stream,int limit)throws IOException {
        try(InputStream input=stream;ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1){if(output.size()+n>limit)throw new IOException("Updategegevens te groot");output.write(buffer,0,n);}return output.toByteArray();
        }
    }
    private static Activity live(WeakReference<Activity> reference) {Activity a=reference.get();return a==null || a.isFinishing() || a.isDestroyed()?null:a;}
    private static void toast(Context context,String message) {Toast.makeText(context,message,Toast.LENGTH_LONG).show();}
}
