package be.smartschoolkatten.runtime;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import java.util.*;

/** Platform fragment owns the photo-picker result without changing host activities. */
public final class CatImportFragment extends Fragment {
    private static final String TAG="smartschool-katten-import";
    private static final int REQUEST=0xca71;
    public CatImportFragment() {}
    public static void open(Activity activity) {
        if(activity.getFragmentManager().findFragmentByTag(TAG)!=null)return;
        CatImportFragment fragment=new CatImportFragment();
        activity.getFragmentManager().beginTransaction().add(fragment,TAG).commitAllowingStateLoss();
        activity.getFragmentManager().executePendingTransactions();
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
            .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        try {fragment.startActivityForResult(intent,REQUEST);}
        catch(ActivityNotFoundException error) {
            activity.getFragmentManager().beginTransaction().remove(fragment).commitAllowingStateLoss();
            android.widget.Toast.makeText(activity,"Geen fotokiezer beschikbaar",android.widget.Toast.LENGTH_SHORT).show();
            KattenRuntime.importFinished(activity);
        }
    }
    @Override public void onCreate(Bundle state) {super.onCreate(state);setRetainInstance(true);}
    @Override public void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=REQUEST)return;
        Activity activity=getActivity();if(activity==null)return;
        activity.getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
        List<Uri> photos=new ArrayList<>();
        if(result==Activity.RESULT_OK && data!=null) {
            ClipData multiple=data.getClipData();
            if(multiple!=null)for(int i=0;i<Math.min(multiple.getItemCount(),CustomCatStore.LIMIT);i++) {
                Uri photo=multiple.getItemAt(i).getUri();if(photo!=null)photos.add(photo);
            }
            else if(data.getData()!=null)photos.add(data.getData());
        }
        if(photos.isEmpty())KattenRuntime.importFinished(activity);
        else KattenRuntime.importCats(activity,photos);
    }
}
