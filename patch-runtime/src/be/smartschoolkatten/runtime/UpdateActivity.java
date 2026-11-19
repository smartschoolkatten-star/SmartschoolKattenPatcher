package be.smartschoolkatten.runtime;

import android.app.*;
import android.content.*;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import java.io.*;

/** Explicit, non-exported installation callback; Android confirms each update. */
public final class UpdateActivity extends Activity {
    private boolean waitingPermission,installing;
    private File apk;
    private long version;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(getIntent().hasExtra(PackageInstaller.EXTRA_STATUS)){result(getIntent());return;}
        try {
            File directory=new File(getCacheDir(),"katten-updates").getCanonicalFile();
            apk=new File(getIntent().getStringExtra("apk")).getCanonicalFile();version=getIntent().getLongExtra("version",-1);
            if(!directory.equals(apk.getParentFile()) || !apk.isFile())throw new IOException("Update ontbreekt");
            if(state!=null) {waitingPermission=state.getBoolean("waiting");installing=state.getBoolean("installing");}
            show("Smartschool Katten bijwerken",()->proceed());
            if(!waitingPermission && !installing)proceed();
        } catch(Exception failure){show("Het updatebestand is niet beschikbaar. Download de update opnieuw.",null);}
    }
    @Override public void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.hasExtra(PackageInstaller.EXTRA_STATUS))result(intent);}
    @Override public void onSaveInstanceState(Bundle state){state.putBoolean("waiting",waitingPermission);state.putBoolean("installing",installing);super.onSaveInstanceState(state);}
    private void proceed() {
        if(installing)return;
        if(!getPackageManager().canRequestPackageInstalls()) {
            waitingPermission=true;
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())),71);return;
        }
        installing=true;show("Update voorbereiden…",null);
        new Thread(()->{
            int sessionId=-1;
            try {
                AppUpdates.verify(this,apk,version);
                PackageInstaller installer=getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(getPackageName());params.setSize(apk.length());params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
                sessionId=installer.createSession(params);
                try(PackageInstaller.Session session=installer.openSession(sessionId);InputStream input=new FileInputStream(apk)) {
                    try(OutputStream output=session.openWrite("base.apk",0,apk.length())) {byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);session.fsync(output);}
                    Intent callback=new Intent(this,UpdateActivity.class).setAction(getPackageName()+".UPDATE_RESULT").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    PendingIntent result=PendingIntent.getActivity(this,sessionId,callback,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
                    session.commit(result.getIntentSender());
                }
                runOnUiThread(()->finish());
            } catch(Exception failure) {
                if(sessionId>=0)try{getPackageManager().getPackageInstaller().abandonSession(sessionId);}catch(RuntimeException ignored){}
                runOnUiThread(()->{installing=false;show("Update voorbereiden mislukt. Probeer opnieuw.",()->proceed());});
            }
        },"katten-update-install").start();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==71){waitingPermission=false;if(getPackageManager().canRequestPackageInstalls())proceed();else show("Sta updates van Smartschool Katten toe om verder te gaan.",()->proceed());}}
    private void result(Intent intent) {
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation=intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if(confirmation!=null){startActivity(confirmation);finish();return;}
        }
        show(status==PackageInstaller.STATUS_SUCCESS?"Smartschool Katten is bijgewerkt":status==PackageInstaller.STATUS_FAILURE_ABORTED?"Update geannuleerd":"Android kon de update niet installeren",null);
    }
    private void show(String message,Runnable action) {
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);int padding=Math.round(24*getResources().getDisplayMetrics().density);body.setPadding(padding,padding,padding,padding);
        TextView text=new TextView(this);text.setText(message);text.setTextSize(18);body.addView(text);
        if(action!=null){Button button=new Button(this);button.setText("Doorgaan");button.setOnClickListener(v->action.run());body.addView(button);}
        Button close=new Button(this);close.setText("Sluiten");close.setOnClickListener(v->finish());body.addView(close);setContentView(body);
    }
}
