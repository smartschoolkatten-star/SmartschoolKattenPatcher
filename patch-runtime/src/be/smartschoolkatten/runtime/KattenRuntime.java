package be.smartschoolkatten.runtime;

import android.app.*;
import android.content.*;
import android.graphics.ImageDecoder;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

/** Injected into Smartschool; independent of the patcher's classes and storage. */
public final class KattenRuntime {
    private static volatile Application app;
    private static volatile android.content.SharedPreferences preferences;
    private static volatile List<String> cats=Collections.emptyList();
    private static volatile List<String> bundledCats=Collections.emptyList();
    private static volatile CustomCatStore customCats;
    private static final class ImportWorkers {
        static final ExecutorService images=Executors.newSingleThreadExecutor();
    }
    private static volatile List<String> activeCats=Collections.emptyList();
    private static volatile Set<String> disabledCats=Collections.emptySet();
    private static final ConcurrentHashMap<String,String> assigned=new ConcurrentHashMap<>();
    private static final Set<String> ownPhotoUrls=ConcurrentHashMap.newKeySet();
    private static final Set<String> avatarPhotoUrls=ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Boolean> cardBinding=new ThreadLocal<>();
    private static final Map<ImageView,String> imageRequests=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class ImageWorkers {
        static final ExecutorService images = Executors.newFixedThreadPool(4);
    }
    private static final String HOME_TAG="smartschool-katten-home-streak";
    private static final String BUTTON_TAG="smartschool-katten-menu";
    private static volatile boolean initialized;
    private KattenRuntime() {}
    public static void editorUnavailable() {
        Application application=app;
        if(application!=null)new android.os.Handler(android.os.Looper.getMainLooper()).post(()->
            Toast.makeText(application,"De ingebouwde foto-editor is niet beschikbaar in Smartschool Katten. Gebruik hiervoor de officiële Smartschool-app.",Toast.LENGTH_LONG).show());
    }
    public static synchronized void init(Application application) {
        if(initialized || application==null)return;
        try {
            initialize(application);
        } catch(RuntimeException | LinkageError error) {
            cats=Collections.emptyList();
            preferences=null;
            CrashDiagnostics.record(application,"Katteninitialisatie mislukt; originele profielfoto’s blijven actief",error);
        }
    }
    private static void initialize(Application application) {
        app=application;
        CrashDiagnostics.install(application);
        preferences=app.getSharedPreferences("smartschool-katten",Context.MODE_PRIVATE);
        List<String> loaded=new ArrayList<>();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(app.getAssets().open("katten-urls.txt"),"UTF-8"))) {
            String line;while((line=reader.readLine())!=null)if(line.startsWith("https://") && !loaded.contains(line))loaded.add(line);
        } catch(IOException error){android.util.Log.e("SmartschoolKatten","Kattenlijst lezen mislukt",error);}
        bundledCats=Collections.unmodifiableList(loaded);
        customCats=new CustomCatStore(new File(app.getFilesDir(),"katten-custom"));
        refreshCatSelection();
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity activity,Bundle state) {}
            public void onActivityStarted(Activity activity) {}
            public void onActivityResumed(Activity activity) {
                // The home/navigation activity exists only after the login flow.
                if(activity.getClass().getName().equals("be.smartschool.mobile.NavigationDrawerActivity"))
                    activity.getWindow().getDecorView().post(()->{
                        try {
                            View streak=activity.getWindow().getDecorView().findViewWithTag(HOME_TAG);
                            if(streak instanceof HomeStreakView)((HomeStreakView)streak).update();
                            AppUpdates.check(activity,false);
                        }
                        catch(RuntimeException | LinkageError error) { CrashDiagnostics.record(app,"Kattenknop plaatsen mislukt",error); }
                    });
            }
            public void onActivityPaused(Activity activity) {}
            public void onActivityStopped(Activity activity) {}
            public void onActivitySaveInstanceState(Activity activity,Bundle state) {}
            public void onActivityDestroyed(Activity activity) {}
        });
        initialized=true;
    }
    public static String avatar(String original) {
        try { return selectAvatar(original); }
        catch(RuntimeException | LinkageError error) { CrashDiagnostics.record(app,"Kattenfoto kiezen mislukt",error); return original; }
    }
    private static String selectAvatar(String original) {
        if(original==null || original.isEmpty())return original;
        // Shared Avatar models can also render the current user. Keep that
        // photograph under the own-photo switch instead of the others switch.
        if(ownPhotoUrls.contains(original))return selectOwnAvatar(original);
        if(cats.contains(original))return original;
        if(cats.isEmpty() || !otherAvatarsEnabled())return original;
        List<String> available=activeCats;
        return CatSelection.choose(original,available,assigned);
    }
    /** Planner can omit a user's photo; provide a cat before its placeholder check. */
    public static void plannerHeader(Object header,Object graphic) {
        try {
            Class<?> graphicType=Class.forName("be.smartschool.mobile.components.quicksearch.data.QuickSearchGraphic");
            Class<?> headerType=Class.forName("be.smartschool.mobile.ui.components.header.SmscHeader");
            if(graphic!=null && "image".equals(graphicType.getMethod("getType").invoke(graphic))) {
                String url=plannerAvatar((String)graphicType.getMethod("getValue").invoke(graphic));
                ImageView image=(ImageView)headerType.getMethod("getImage").invoke(header);
                if(cats.contains(url) && activity(image.getContext())!=null) {
                    headerType.getMethod("setIconRoundedBackground",boolean.class).invoke(header,false);
                    catImage(image,url);return;
                }
            }
            Class.forName("o26").getMethod("P",headerType,graphicType).invoke(null,header,graphic);
        } catch(ReflectiveOperationException | RuntimeException | LinkageError error) {
            CrashDiagnostics.record(app,"Plannerkopfoto laden mislukt",error);
        }
    }
    public static String plannerAvatar(String original) {
        try {
            if(original!=null && ownPhotoUrls.contains(original))return selectOwnAvatar(original);
            if(!otherAvatarsEnabled() || activeCats.isEmpty())return original;
            return CatSelection.choose(original==null || original.trim().isEmpty()?"planner-no-photo":original,activeCats,assigned);
        } catch(RuntimeException | LinkageError error) {CrashDiagnostics.record(app,"Plannerkat kiezen mislukt",error);return original;}
    }
    /** Keep the source URL in host state, including flows retained on recreation. */
    public static String rememberOwnAvatar(String original) {
        if(original!=null && !original.isEmpty() && !cats.contains(original))ownPhotoUrls.add(original);
        return original;
    }
    /** Record photo identity without putting a selected cat in retained host state. */
    public static String rememberAvatar(String original) {
        if(original!=null && !original.isEmpty() && !cats.contains(original))avatarPhotoUrls.add(original);
        return original;
    }
    public static String displayAvatar(String original) {
        if(Boolean.TRUE.equals(cardBinding.get()))return studentCardAvatar(original);
        if(ownPhotoUrls.contains(original==null?"":original))return ownAvatar(original);
        return avatarPhotoUrls.contains(original==null?"":original)?avatar(original):original;
    }
    public static void bindStudentCardAvatar(Object view,String original) {
        Boolean previous=cardBinding.get();cardBinding.set(true);
        try {view.getClass().getMethod("setAvatar",String.class).invoke(view,studentCardAvatar(original));}
        catch(ReflectiveOperationException error) {CrashDiagnostics.record(app,"Leerlingenkaartfoto binden mislukt",error);}
        finally {if(previous==null)cardBinding.remove();else cardBinding.set(previous);}
    }
    public static String ownAvatar(String original) {
        try { return selectOwnAvatar(original); }
        catch(RuntimeException | LinkageError error) { CrashDiagnostics.record(app,"Eigen kattenfoto kiezen mislukt",error); return original; }
    }
    /** Own-avatar UI binding; keep User and retained header state unchanged. */
    public static String ownUserAvatar(Object user) {
        String original=null;
        try {
            original=(String)user.getClass().getMethod("getAvatarUrl").invoke(user);
            return ownAvatar(original);
        } catch(ReflectiveOperationException | RuntimeException | LinkageError error) {
            CrashDiagnostics.record(app,"Eigen profielfoto binden mislukt",error);
            return original;
        }
    }
    private static String selectOwnAvatar(String original) {
        if(original!=null && !original.isEmpty() && !cats.contains(original))ownPhotoUrls.add(original);
        if(!ownAvatarEnabled())return original;
        return selectStudentCardAvatar(original);
    }
    public static String studentCardAvatar(String original) {
        try {
            if(preferences==null || !AvatarPolicy.card(preferences.getBoolean("enabled",true),preferences.getBoolean("own-enabled",true),preferences.getBoolean("student-card-enabled",false)))return original;
            return selectStudentCardAvatar(original);
        }
        catch(RuntimeException | LinkageError error) { CrashDiagnostics.record(app,"Leerlingenkaartkat kiezen mislukt",error); return original; }
    }
    private static String selectStudentCardAvatar(String original) {
        if(preferences==null || activeCats.isEmpty())return original;
        String favorite=preferences.getString("favorite",null);
        if(favorite!=null && activeCats.contains(favorite))return favorite;
        favorite=CatSelection.choose(original==null || original.isEmpty()?"student-card":original,activeCats,assigned);
        preferences.edit().putString("favorite",favorite).apply();
        return favorite;
    }
    private static void refreshCatSelection() {
        List<String> all=new ArrayList<>(bundledCats);
        if(customCats!=null)for(File file:customCats.files())all.add(android.net.Uri.fromFile(file).toString());
        cats=Collections.unmodifiableList(all);
        Set<String> disabled=new HashSet<>(preferences.getStringSet("disabled-cats",Collections.emptySet()));
        List<String> available=CatSelection.available(cats,disabled);
        disabledCats=Collections.unmodifiableSet(disabled);
        activeCats=Collections.unmodifiableList(available);
        assigned.entrySet().removeIf(entry->!available.contains(entry.getValue()));
        String favorite=preferences.getString("favorite",null);
        if(favorite!=null && !available.contains(favorite))preferences.edit().remove("favorite").apply();
    }
    private static void toggleCat(String url) {
        Set<String> disabled=new HashSet<>(disabledCats);
        if(!disabled.remove(url))disabled.add(url);
        preferences.edit().putStringSet("disabled-cats",disabled).apply();
        refreshCatSelection();
    }
    static void importFinished(Activity activity) {
        if(!activity.isFinishing() && !activity.isDestroyed())openMenu(activity);
    }
    static void importCats(Activity activity,List<android.net.Uri> photos) {
        java.lang.ref.WeakReference<Activity> owner=new java.lang.ref.WeakReference<>(activity);
        Toast.makeText(activity,"Katten toevoegen…",Toast.LENGTH_SHORT).show();
        ImportWorkers.images.execute(()->{
            int imported=0;String failure=null;
            for(android.net.Uri photo:photos) {
                if(customCats.count()>=CustomCatStore.LIMIT){failure="Maximum van 100 eigen katten bereikt";break;}
                try(InputStream input=app.getContentResolver().openInputStream(photo)) {
                    if(input==null)throw new IOException("Foto kon niet worden geopend");
                    customCats.add(input,file->ImageDecoder.decodeDrawable(ImageDecoder.createSource(file),(decoder,info,source)->{
                        int width=info.getSize().getWidth(),height=info.getSize().getHeight();
                        double scale=Math.min(1.0,128.0/Math.max(width,height));
                        decoder.setTargetSize(Math.max(1,(int)(width*scale)),Math.max(1,(int)(height*scale)));
                    }));
                    imported++;
                } catch(IOException | RuntimeException | OutOfMemoryError error) {failure="Een foto kon niet worden toegevoegd";}
            }
            refreshCatSelection();
            final String message=imported>0?imported+" "+(imported==1?"kat toegevoegd":"katten toegevoegd")+(failure==null?"":". "+failure):failure;
            Activity current=owner.get();
            if(current!=null)current.runOnUiThread(()->{
                if(!current.isDestroyed() && !current.isFinishing()) {
                    Toast.makeText(current,message,Toast.LENGTH_LONG).show();importFinished(current);
                }
            });
        });
    }
    private static int dp(Activity a,int value){return Math.round(value*a.getResources().getDisplayMetrics().density);}
    static void openMenu(Context context) {
        Activity activity=activity(context);
        if(activity==null || activity.isFinishing() || activity.isDestroyed())return;
        try { showMenu(activity); }
        catch(RuntimeException | LinkageError error) {
            CrashDiagnostics.record(app,"Kattenmenu openen mislukt",error);
            Toast.makeText(activity,"Het kattenmenu kan niet worden geopend. Probeer de app opnieuw te starten.",Toast.LENGTH_LONG).show();
        }
    }
    private static Activity activity(Context context) {
        for(int i=0;i<12 && context!=null;i++) {
            if(context instanceof Activity)return (Activity)context;
            if(!(context instanceof android.content.ContextWrapper))break;
            Context next=((android.content.ContextWrapper)context).getBaseContext();
            if(next==context)break;context=next;
        }
        return null;
    }
    /** Scope custom decoding to substituted cat URLs; preserve other image loading. */
    public static void avatarImage(ImageView view,String url,boolean option) {
        url=displayAvatar(url);
        Activity activity=activity(view.getContext());
        if(activity!=null && cats.contains(url)) {
            roundAvatar(view);
            loadImage(activity,view,url,true);
        } else {
            imageRequests.remove(view);
            try { Class.forName("ul0").getMethod("U",ImageView.class,String.class,boolean.class).invoke(null,view,url,option); }
            catch(ReflectiveOperationException error) { CrashDiagnostics.record(app,"Originele profielfoto laden mislukt",error); }
        }
    }
    private static void roundAvatar(ImageView view) {
        // The outline covers the whole avatar. Host padding must not leave a
        // smaller image inside it, especially in larger message/menu avatars.
        view.setPadding(0,0,0,0);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        // Render animations through ImageView, while the outline supplies the circle.
        for(Class<?> type=view.getClass();type!=null;type=type.getSuperclass()) {
            if(type.getName().equals("de.hdodenhof.circleimageview.CircleImageView")) {
                try {type.getMethod("setDisableCircularTransformation",boolean.class).invoke(view,true);}
                catch(ReflectiveOperationException error) {CrashDiagnostics.record(app,"Ronde kattenweergave aanpassen mislukt",error);}
                break;
            }
        }
        view.setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View image,android.graphics.Outline outline) {
                int side=Math.min(image.getWidth(),image.getHeight());
                int left=(image.getWidth()-side)/2,top=(image.getHeight()-side)/2;
                outline.setOval(left,top,left+side,top+side);
            }
        });
        view.setClipToOutline(true);view.invalidateOutline();
    }
    private static boolean catImage(ImageView view,String url) {
        Activity owner=activity(view.getContext());
        if(owner==null || !cats.contains(url))return false;
        roundAvatar(view);loadImage(owner,view,url,true);return true;
    }
    private static void imageFallback(String method,ImageView view,String url,Class<?>[] extraTypes,Object... extra) {
        imageRequests.remove(view);
        try {
            Class<?>[] types=new Class<?>[extraTypes.length+2];types[0]=ImageView.class;types[1]=String.class;
            System.arraycopy(extraTypes,0,types,2,extraTypes.length);
            Object[] values=new Object[extra.length+2];values[0]=view;values[1]=url;System.arraycopy(extra,0,values,2,extra.length);
            Class.forName("eo0").getMethod(method,types).invoke(null,values);
        } catch(ReflectiveOperationException error) {CrashDiagnostics.record(app,"Originele afbeelding laden mislukt",error);}
    }
    public static void imageA(ImageView view,String url,boolean flag) {url=displayAvatar(url);if(!catImage(view,url))imageFallback("a",view,url,new Class<?>[]{boolean.class},flag);}
    public static void imageB(ImageView view,String url,Integer placeholder,boolean flag) {url=displayAvatar(url);if(!catImage(view,url))imageFallback("b",view,url,new Class<?>[]{Integer.class,boolean.class},placeholder,flag);}
    public static void imageC(ImageView view,String url,Integer placeholder,int flags) {url=displayAvatar(url);if(!catImage(view,url))imageFallback("c",view,url,new Class<?>[]{Integer.class,int.class},placeholder,flags);}
    public static void imageD(ImageView view,String url,Integer placeholder) {url=displayAvatar(url);if(!catImage(view,url))imageFallback("d",view,url,new Class<?>[]{Integer.class},placeholder);}
    /** Also intercept direct loads and d94.b's default-argument delegation. */
    public static void directImage(Object loader,String url,ImageView view,Integer placeholder,boolean flag,int defaults) {
        url=displayAvatar(url);
        if(catImage(view,url))return;
        imageRequests.remove(view);
        try {
            Class<?> type=Class.forName("d94");
            type.getMethod("a",type,String.class,ImageView.class,Integer.class,boolean.class,int.class)
                .invoke(null,loader,url,view,placeholder,flag,defaults);
        } catch(ReflectiveOperationException error) {CrashDiagnostics.record(app,"Rechtstreekse afbeelding laden mislukt",error);}
    }
    /** Direct Glide requests bypass ul0/eo0/d94, including avatar adapters. */
    public static void glideImage(Object request,ImageView view) {
        try {
            Class<?> type=Class.forName("kw9");
            java.lang.reflect.Field model=type.getDeclaredField("M");model.setAccessible(true);
            Object url=model.get(request);
            if(url instanceof String && catImage(view,displayAvatar((String)url)))return;
        } catch(ReflectiveOperationException error) {
            CrashDiagnostics.record(app,"Rechtstreekse kattenaanvraag herkennen mislukt",error);
        }
        imageRequests.remove(view);
        try {Class.forName("kw9").getMethod("z",ImageView.class).invoke(request,view);}
        catch(ReflectiveOperationException error) {CrashDiagnostics.record(app,"Glide-afbeelding laden mislukt",error);}
    }
    /** Glide's explicit ImageView targets also bypass its z(ImageView) entry. */
    public static void glideTarget(Object request,Object target,Object options) {
        try {
            Class<?> viewTarget=Class.forName("vw0");
            if(viewTarget.isInstance(target)) {
                java.lang.reflect.Field image=viewTarget.getDeclaredField("a");image.setAccessible(true);
                ImageView view=(ImageView)image.get(target);
                java.lang.reflect.Field model=Class.forName("kw9").getDeclaredField("M");model.setAccessible(true);
                Object url=model.get(request);
                if(url instanceof String && catImage(view,displayAvatar((String)url)))return;
                imageRequests.remove(view);
            }
        } catch(ReflectiveOperationException error) {CrashDiagnostics.record(app,"Glide-kattenweergave herkennen mislukt",error);}
        try {Class.forName("kw9").getMethod("y",Class.forName("wgb"),Class.forName("ls0")).invoke(request,target,options);}
        catch(ReflectiveOperationException error) {CrashDiagnostics.record(app,"Glide-doel laden mislukt",error);}
    }
    public static void configureCard(Object modifier,String label,Object icon,Object action,boolean compact,Object composer,int changed,int defaults) {
        HomeSettingsBridge.render(modifier,label,icon,action,compact,composer,changed,defaults);
    }
    public static void composeImage(Object model,String description,Object modifier,Object callback,
            Object alignment,Object scale,float alpha,Object filter,Object composer,int changed,int defaults) {
        if(model instanceof String) {
            Object displayed=displayAvatar((String)model);
            if(!Objects.equals(model,displayed)) {changed=(changed & ~14)|4;defaults &= ~1;}
            model=displayed;
        }
        ComposeImageBridge.render(model,description,modifier,callback,alignment,scale,alpha,
            filter,composer,changed,defaults,model instanceof String && cats.contains(model));
    }
    private static TextView text(Activity a,String label,int size){TextView t=new TextView(a);t.setText(label);t.setTextSize(size);t.setPadding(0,dp(a,8),0,dp(a,8));return t;}
    private static Streak readStreak() {
        String date=preferences.getString("last","");LocalDate last=null;
        try{if(!date.isEmpty())last=LocalDate.parse(date);}catch(java.time.format.DateTimeParseException ignored){}
        return new Streak(preferences.getInt("streak",0),preferences.getInt("best",0),last).refresh(LocalDate.now());
    }
    private static void saveStreak(Streak s) {
        preferences.edit().putInt("streak",s.count).putInt("best",s.best).putString("last",s.last==null?"":s.last.toString()).apply();
    }
    private static boolean otherAvatarsEnabled() {
        return preferences!=null && AvatarPolicy.others(preferences.getBoolean("enabled",true),preferences.getBoolean("others-enabled",true));
    }
    private static boolean ownAvatarEnabled() {
        return preferences!=null && AvatarPolicy.own(preferences.getBoolean("enabled",true),preferences.getBoolean("own-enabled",true));
    }
    static boolean streakVisible() {return preferences!=null && preferences.getBoolean("streak-visible",true);}
    public static void homeFavourites(int changed,Object composer,Object action,Object modifier,List<?> favourites) {
        HomeSettingsBridge.favourites(changed,composer,action,modifier,favourites);
    }
    /** b15.c uses v0b.h: Open Sans 600, 13sp, 17.7sp line height. */
    private static void styleHomePillText(TextView view) {
        view.setTypeface(view.getResources().getFont(0x7f09000a));
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,13);
        view.setLetterSpacing(0f);view.setIncludeFontPadding(false);
        float lineHeight=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,17.7f,view.getResources().getDisplayMetrics());
        view.setLineSpacing(lineHeight-view.getPaint().getFontSpacing(),1f);
    }
    static final class HomeStreakView extends LinearLayout {
        final TextView stats;
        final TextView checkIn;
        HomeStreakView(Context context) {
            super(context);setTag(HOME_TAG);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER);
            int padding=Math.round(12*getResources().getDisplayMetrics().density);setPadding(padding,0,padding,0);
            GradientDrawable tile=new GradientDrawable();tile.setColor(context.getColor(0x7f060040));
            tile.setCornerRadius(24*getResources().getDisplayMetrics().density);
            setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x22000000),tile,null));
            setFocusable(true);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            stats=new TextView(context);styleHomePillText(stats);stats.setTextColor(context.getColor(0x7f0609cf));
            stats.setGravity(Gravity.CENTER);addView(stats,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
            checkIn=new TextView(context);styleHomePillText(checkIn);checkIn.setGravity(Gravity.CENTER);
            float density=getResources().getDisplayMetrics().density;
            checkIn.setMinHeight(Math.round(48*density));
            checkIn.setPadding(Math.round(20*density),0,Math.round(20*density),0);
            checkIn.setTextColor(context.getColor(0x7f0609cf));
            stats.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            checkIn.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            setOnClickListener(v->{
                Streak streak=readStreak();LocalDate today=LocalDate.now();
                if(!today.equals(streak.last)) {
                    saveStreak(streak.click(today));
                    Toast.makeText(context,"Kat geaaid! +1 dag 🐾",Toast.LENGTH_SHORT).show();
                }
                update();
            });
            addView(checkIn,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,Math.round(48*density)));
            update();
        }
        void update() {
            Streak streak=readStreak();saveStreak(streak);
            stats.setText("🔥 "+streak.count+" dagen  ·  🏆 "+streak.best);
            boolean checkedIn=LocalDate.now().equals(streak.last);
            checkIn.setText(checkedIn?"Kom morgen terug":"🐾 Aai de kat");setEnabled(!checkedIn);
            setContentDescription("Streak: "+streak.count+" dagen. Record: "+streak.best+" dagen. "+checkIn.getText());
        }
        @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);info.setClassName(Button.class.getName());
        }
    }
    private static void showMenu(Activity activity) {
        boolean dark=(activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int surface=activity.getColor(dark?android.R.color.system_neutral1_900:android.R.color.system_neutral1_50);
        int textColor=activity.getColor(dark?android.R.color.system_neutral1_50:android.R.color.system_neutral1_900);
        int accent=activity.getColor(dark?android.R.color.system_accent1_200:android.R.color.system_accent1_600);
        Dialog dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        boolean[] internalClose={false};
        dialog.setOnDismissListener(d->{
            if(internalClose[0] || activity.isFinishing() || activity.isDestroyed())return;
            refreshCatSelection();
            // Rebind both native and Compose avatars through the host's saved-state lifecycle.
            imageRequests.clear();
            activity.recreate();
        });
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(activity,24),dp(activity,20),dp(activity,24),dp(activity,16));
        GradientDrawable panel=new GradientDrawable();panel.setColor(surface);panel.setCornerRadius(dp(activity,28));body.setBackground(panel);body.setClipToOutline(true);
        TextView title=text(activity,"Smartschool Katten",24);title.setTextColor(textColor);title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));body.addView(title);
        LinearLayout tabs=new LinearLayout(activity);
        Button generalTab=menuButton(activity,"Algemeen",accent,dark?Color.BLACK:Color.WHITE);
        Button experimentalTab=menuButton(activity,"Experimenteel",surface,textColor);
        tabs.addView(generalTab,new LinearLayout.LayoutParams(0,dp(activity,48),1));
        tabs.addView(experimentalTab,new LinearLayout.LayoutParams(0,dp(activity,48),1));
        body.addView(tabs);
        LinearLayout general=new LinearLayout(activity);general.setOrientation(LinearLayout.VERTICAL);body.addView(general);
        LinearLayout experimental=new LinearLayout(activity);experimental.setOrientation(LinearLayout.VERTICAL);experimental.setVisibility(View.GONE);body.addView(experimental);
        TextView experimentalHint=text(activity,"Deze opties veranderen alleen de weergave op dit toestel, niet de toestemming van je school.",14);
        experimentalHint.setTextColor(textColor);experimentalHint.setPadding(0,dp(activity,16),0,dp(activity,8));experimental.addView(experimentalHint);
        Switch leaveSchool=settingSwitch(activity,experimental,"‘Mag school verlaten’ als JA tonen",textColor,accent);
        leaveSchool.setChecked(preferences.getBoolean("experimental-leave-school",false));
        leaveSchool.setOnCheckedChangeListener((button,on)->preferences.edit().putBoolean("experimental-leave-school",on).apply());
        generalTab.setOnClickListener(v->{general.setVisibility(View.VISIBLE);experimental.setVisibility(View.GONE);generalTab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));experimentalTab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(surface));generalTab.setTextColor(dark?Color.BLACK:Color.WHITE);experimentalTab.setTextColor(textColor);});
        experimentalTab.setOnClickListener(v->{general.setVisibility(View.GONE);experimental.setVisibility(View.VISIBLE);experimentalTab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));generalTab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(surface));experimentalTab.setTextColor(dark?Color.BLACK:Color.WHITE);generalTab.setTextColor(textColor);});
        Switch enabled=settingSwitch(activity,general,"Kattenprofielfoto’s",textColor,accent);
        enabled.setChecked(preferences.getBoolean("enabled",true));
        Switch others=settingSwitch(activity,general,"Kattenfoto’s van anderen",textColor,accent);
        others.setChecked(preferences.getBoolean("others-enabled",true));
        others.setOnCheckedChangeListener((button,on)->preferences.edit().putBoolean("others-enabled",on).apply());
        Switch own=settingSwitch(activity,general,"Je eigen kattenfoto",textColor,accent);
        own.setChecked(preferences.getBoolean("own-enabled",true));
        own.setOnCheckedChangeListener((button,on)->preferences.edit().putBoolean("own-enabled",on).apply());
        Switch studentCard=settingSwitch(activity,general,"Kat op leerlingenkaart",textColor,accent);
        studentCard.setChecked(preferences.getBoolean("student-card-enabled",false));
        studentCard.setOnCheckedChangeListener((button,on)->preferences.edit().putBoolean("student-card-enabled",on).apply());
        Switch streakVisible=settingSwitch(activity,general,"Streakbalk",textColor,accent);
        streakVisible.setChecked(preferences.getBoolean("streak-visible",true));
        streakVisible.setOnCheckedChangeListener((button,on)->preferences.edit().putBoolean("streak-visible",on).apply());
        if(AppUpdates.supported(activity)) {
            Button updates=menuButton(activity,"Controleren op updates",surface,textColor);
            updates.setOnClickListener(v->AppUpdates.check(activity,true));
            general.addView(updates,new LinearLayout.LayoutParams(-1,dp(activity,48)));
        }
        TextView heading=text(activity,"Je favoriete kat",18);heading.setTextColor(textColor);heading.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));general.addView(heading);
        TextView hint=text(activity,"Tik op een kat voor je eigen profielfoto.",14);hint.setTextColor(textColor);general.addView(hint);
        TextView disableHint=text(activity,"Druk lang om een kat uit te schakelen.",12);disableHint.setTextColor(textColor);disableHint.setAlpha(0.65f);general.addView(disableHint);
        int menuWidth=Math.min(activity.getResources().getDisplayMetrics().widthPixels-dp(activity,32),dp(activity,420));
        int photoSize=Math.max(1,(menuWidth-dp(activity,48))/4-dp(activity,8));
        int cellHeight=photoSize+dp(activity,8);
        LinearLayout gallery=new LinearLayout(activity);gallery.setOrientation(LinearLayout.VERTICAL);
        java.util.List<ImageView> previews=new ArrayList<>();
        boolean[] deleting={false};
        Set<String> selectedUploads=new HashSet<>();
        Set<String> uploadUrls=new HashSet<>();
        for(File file:customCats.files())uploadUrls.add(android.net.Uri.fromFile(file).toString());
        int red=0xffd32f2f;
        int gray=dark?0xff555555:0xffdddddd;
        Button upload=menuButton(activity,"Katten uploaden",accent,dark?Color.BLACK:Color.WHITE);
        Button delete=menuButton(activity,"Delete uploads",accent,dark?Color.BLACK:Color.WHITE);
        Button close=menuButton(activity,"Sluiten",accent,dark?Color.BLACK:Color.WHITE);
        Runnable selection=()->{
            String favorite=preferences.getString("favorite",null);
            for(ImageView preview:previews) {
                boolean isUpload=uploadUrls.contains(preview.getTag());
                boolean marked=deleting[0] && selectedUploads.contains(preview.getTag());
                boolean disabled=deleting[0]?!isUpload:disabledCats.contains(preview.getTag());
                boolean chosen=!marked && !disabled && Objects.equals(favorite,preview.getTag());
                android.graphics.ColorMatrix grayscale=new android.graphics.ColorMatrix();grayscale.setSaturation(0);
                preview.setColorFilter(disabled?new android.graphics.ColorMatrixColorFilter(grayscale):null);
                preview.setAlpha(disabled?0.4f:1f);
                preview.setContentDescription("Kat "+(previews.indexOf(preview)+1)+(disabled?", uitgeschakeld. Houd ingedrukt om in te schakelen.":" als favoriet kiezen. Houd ingedrukt om uit te schakelen."));
                GradientDrawable border=new GradientDrawable();border.setColor(Color.TRANSPARENT);border.setCornerRadius(dp(activity,16));border.setStroke(dp(activity,3),marked?red:chosen?accent:Color.TRANSPARENT);
                preview.setForeground(border);preview.setSelected(chosen || marked);
                if(deleting[0])preview.setContentDescription(isUpload?(marked?"Upload geselecteerd voor verwijderen":"Upload selecteren voor verwijderen"):"Standaardkat, kan niet worden verwijderd");
            }
            upload.setText(deleting[0]?"Annuleren":"Katten uploaden");
            upload.setEnabled(deleting[0] || customCats.count()<CustomCatStore.LIMIT);
            delete.setEnabled(customCats.count()>0 && (!deleting[0] || !selectedUploads.isEmpty()));
            delete.setBackgroundTintList(android.content.res.ColorStateList.valueOf(customCats.count()==0?gray:deleting[0]?red:accent));
            upload.setBackgroundTintList(android.content.res.ColorStateList.valueOf(deleting[0]?0xff1976d2:upload.isEnabled()?accent:gray));
            upload.setTextColor(deleting[0]?Color.WHITE:dark?Color.BLACK:Color.WHITE);
            delete.setTextColor(customCats.count()==0?textColor:dark?Color.BLACK:Color.WHITE);
            enabled.setEnabled(!deleting[0]);
            setSettingEnabled(others,!deleting[0] && enabled.isChecked());
            setSettingEnabled(own,!deleting[0] && enabled.isChecked());
            setSettingEnabled(studentCard,!deleting[0] && enabled.isChecked() && own.isChecked());
            streakVisible.setEnabled(!deleting[0]);heading.setAlpha(deleting[0]?0.4f:1f);
            hint.setText(deleting[0]?"Selecteer de uploads die je wilt verwijderen.":"Tik op een kat voor je eigen profielfoto.");
            disableHint.setAlpha(deleting[0]?0.25f:0.65f);
            close.setEnabled(!deleting[0]);
            close.setAlpha(deleting[0]?0.4f:1f);
            close.setBackgroundTintList(android.content.res.ColorStateList.valueOf(deleting[0]?gray:accent));
            dialog.setCancelable(!deleting[0]);dialog.setCanceledOnTouchOutside(!deleting[0]);
        };
        enabled.setOnCheckedChangeListener((button,on)->{preferences.edit().putBoolean("enabled",on).apply();selection.run();});
        own.setOnCheckedChangeListener((button,on)->{preferences.edit().putBoolean("own-enabled",on).apply();selection.run();});
        LinearLayout row=null;
        for(int index=0;index<cats.size();index++) {
            if(index%4==0){row=new LinearLayout(activity);gallery.addView(row,new LinearLayout.LayoutParams(-1,-2));}
            String url=cats.get(index);ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setTag(url);previews.add(image);
            image.setContentDescription("Kat "+(index+1)+" als favoriet kiezen");
            GradientDrawable background=new GradientDrawable();background.setColor(surface);background.setCornerRadius(dp(activity,16));image.setBackground(background);image.setClipToOutline(true);
            FrameLayout cell=new FrameLayout(activity);row.addView(cell,new LinearLayout.LayoutParams(0,cellHeight,1));
            cell.addView(image,new FrameLayout.LayoutParams(photoSize,photoSize,Gravity.CENTER));
            image.setOnLongClickListener(v->{if(deleting[0])return true;toggleCat(url);selection.run();Toast.makeText(activity,disabledCats.contains(url)?"Kat uitgeschakeld":"Kat ingeschakeld",Toast.LENGTH_SHORT).show();return true;});
            image.setOnClickListener(v->{
                if(deleting[0]) {
                    if(uploadUrls.contains(url)) {if(!selectedUploads.remove(url))selectedUploads.add(url);selection.run();}
                    return;
                }
                if(disabledCats.contains(url)) {Toast.makeText(activity,"Houd ingedrukt om deze kat weer in te schakelen",Toast.LENGTH_SHORT).show();return;}
                preferences.edit().putString("favorite",url).apply();selection.run();Toast.makeText(activity,"Favoriete kat opgeslagen",Toast.LENGTH_SHORT).show();
            });loadImage(activity,image,url);
        }
        if(row!=null && cats.size()%4!=0) {
            for(int missing=cats.size()%4;missing<4;missing++)
                row.addView(new View(activity),new LinearLayout.LayoutParams(0,cellHeight,1));
        }
        general.addView(gallery);
        int customCount=customCats.count();
        upload.setOnClickListener(v->{
            if(deleting[0]) {deleting[0]=false;selectedUploads.clear();selection.run();return;}
            internalClose[0]=true;dialog.dismiss();CatImportFragment.open(activity);
        });
        delete.setOnClickListener(v->{
            if(!deleting[0]) {deleting[0]=true;selection.run();return;}
            if(selectedUploads.isEmpty())return;
            AlertDialog confirmation=new AlertDialog.Builder(activity)
                .setMessage("Ben je zeker dat je deze katten wilt verwijderen?")
                .setNegativeButton("Nee",(d,which)->{})
                .setPositiveButton("Ja",(d,which)->{
                    String failure=null;
                    for(String url:new HashSet<>(selectedUploads)) {
                        try {customCats.delete(new File(android.net.Uri.parse(url).getPath()));}
                        catch(java.io.IOException error) {failure="Sommige katten konden niet worden verwijderd. Probeer opnieuw.";}
                    }
                    Set<String> disabled=new HashSet<>(disabledCats);disabled.removeAll(selectedUploads);
                    preferences.edit().putStringSet("disabled-cats",disabled).apply();
                    refreshCatSelection();
                    internalClose[0]=true;dialog.dismiss();openMenu(activity);
                    if(failure!=null)Toast.makeText(activity,failure,Toast.LENGTH_LONG).show();
                }).create();
            confirmation.setCanceledOnTouchOutside(false);confirmation.show();
            Window confirmationWindow=confirmation.getWindow();
            if(confirmationWindow!=null) {
                GradientDrawable confirmationPanel=new GradientDrawable();confirmationPanel.setColor(surface);confirmationPanel.setCornerRadius(dp(activity,28));
                confirmationWindow.setBackgroundDrawable(confirmationPanel);
                confirmationWindow.getDecorView().setClipToOutline(true);
            }
        });
        LinearLayout actions=new LinearLayout(activity);
        LinearLayout.LayoutParams uploadLayout=new LinearLayout.LayoutParams(0,dp(activity,48),1);uploadLayout.setMarginEnd(dp(activity,8));
        actions.addView(upload,uploadLayout);actions.addView(delete,new LinearLayout.LayoutParams(0,dp(activity,48),1));
        LinearLayout.LayoutParams actionsLayout=new LinearLayout.LayoutParams(-1,-2);actionsLayout.topMargin=dp(activity,8);general.addView(actions,actionsLayout);
        TextView uploadCount=text(activity,"("+customCount+"/"+CustomCatStore.LIMIT+")",12);
        uploadCount.setTextColor(textColor);uploadCount.setAlpha(0.65f);uploadCount.setGravity(Gravity.START);
        general.addView(uploadCount,new LinearLayout.LayoutParams(-1,-2));
        close.setOnClickListener(v->dialog.dismiss());
        selection.run();
        LinearLayout.LayoutParams closeLayout=new LinearLayout.LayoutParams(-1,dp(activity,48));closeLayout.topMargin=dp(activity,8);body.addView(close,closeLayout);
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.setBackground(panel.getConstantState().newDrawable());scroll.setClipToOutline(true);scroll.addView(body);dialog.setContentView(scroll);
        Window window=dialog.getWindow();if(window!=null)window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
        if(window!=null) {
            window.setLayout(menuWidth,WindowManager.LayoutParams.WRAP_CONTENT);
            scroll.getLayoutParams().height=Math.min(dp(activity,640),(int)(activity.getResources().getDisplayMetrics().heightPixels*0.8));scroll.requestLayout();
        }
    }
    private static void setSettingEnabled(Switch control,boolean enabled) {
        control.setEnabled(enabled);
        View row=(View)control.getParent();row.setAlpha(enabled?1f:0.4f);
        if(row instanceof ViewGroup)((ViewGroup)row).getChildAt(0).setEnabled(enabled);
    }
    private static Switch settingSwitch(Activity activity,LinearLayout parent,String label,int textColor,int accent) {
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(activity,56));row.setPadding(0,dp(activity,8),0,dp(activity,8));row.setClipChildren(false);row.setClipToPadding(false);
        TextView caption=text(activity,label,16);caption.setTextColor(textColor);caption.setPadding(0,0,dp(activity,12),0);
        row.addView(caption,new LinearLayout.LayoutParams(0,-2,1));
        Switch control=new Switch(activity);control.setText("");control.setContentDescription(label);
        control.setShowText(false);control.setSwitchMinWidth(dp(activity,52));control.setPadding(dp(activity,4),dp(activity,4),dp(activity,4),dp(activity,4));
        control.setThumbTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{accent,textColor}));
        row.addView(control,new LinearLayout.LayoutParams(-2,-2));caption.setOnClickListener(v->{if(control.isEnabled())control.toggle();});
        parent.addView(row,new LinearLayout.LayoutParams(-1,-2));return control;
    }
    /** Only the own-card UI calls this; leave the server's model untouched. */
    public static String studentCardInformation(Object information) {
        String original=null;
        try {
            Class<?> type=Class.forName("be.smartschool.mobile.network.services.ProfileInformation");
            original=(String)type.getMethod("getContent").invoke(information);
            String title=(String)type.getMethod("getTitle").invoke(information);
            return StudentCardDisplay.content(title,original,preferences!=null && preferences.getBoolean("experimental-leave-school",false));
        } catch(ReflectiveOperationException | RuntimeException | LinkageError error) {
            CrashDiagnostics.record(app,"Leerlingenkaartinformatie aanpassen mislukt",error);return original;
        }
    }
    private static Button menuButton(Activity activity,String label,int background,int textColor) {
        Button button=new Button(activity);button.setText(label);button.setAllCaps(false);button.setTextSize(14);button.setTextColor(textColor);
        button.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));button.setBackgroundTintList(null);button.setStateListAnimator(null);
        GradientDrawable shape=new GradientDrawable();shape.setColor(background);shape.setCornerRadius(dp(activity,24));
        button.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x22000000),shape,null));return button;
    }
    private static void loadImage(Activity activity,ImageView target,String url) {
        loadImage(activity,target,url,false);
    }
    private static void loadImage(Activity activity,ImageView target,String url,boolean avatar) {
        imageRequests.put(target,url);
        loadImageAttempt(activity,target,url,avatar,0);
    }
    private static void loadImageAttempt(Activity activity,ImageView target,String url,boolean avatar,int attempt) {
        java.lang.ref.WeakReference<Activity> owner=new java.lang.ref.WeakReference<>(activity);
        java.lang.ref.WeakReference<ImageView> view=new java.lang.ref.WeakReference<>(target);
        ImageWorkers.images.execute(()->{
            Activity current=owner.get();ImageView currentTarget=view.get();
            if(current==null || currentTarget==null || current.isDestroyed() || current.isFinishing() || !url.equals(imageRequests.get(currentTarget)))return;
            File cached=null;
            try {
                File directory=new File(app.getCacheDir(),"katten-images");directory.mkdirs();
                String key=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(url.getBytes("UTF-8")));
                File file=new File(directory,key);cached=file;
                byte[] data;
                if(url.startsWith("file:")) {
                    File local=new File(new java.net.URI(url));
                    if(!local.getCanonicalFile().getParentFile().equals(new File(app.getFilesDir(),"katten-custom").getCanonicalFile()))throw new IOException("Onbekende kattenfoto");
                    if(local.length()>CustomCatStore.MAX_BYTES)throw new IOException("Afbeelding te groot");
                    data=java.nio.file.Files.readAllBytes(local.toPath());
                } else if(file.exists() && file.length()<=8*1024*1024) data=java.nio.file.Files.readAllBytes(file.toPath());
                else {
                    HttpsURLConnection connection=(HttpsURLConnection)new URL(url).openConnection();connection.setConnectTimeout(15000);connection.setReadTimeout(20000);
                    try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                        byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>8*1024*1024)throw new IOException("Afbeelding te groot");}
                        data=out.toByteArray();
                    }finally{connection.disconnect();}
                    File temporary=File.createTempFile("katten-image-",".tmp",directory);
                    try {
                        java.nio.file.Files.write(temporary.toPath(),data);
                        java.nio.file.Files.move(temporary.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } finally { temporary.delete(); }
                }
                // Preserve the full source ratio; CoverDrawable crops when drawing.
                // GIFs remain animated rather than being flattened to a bitmap.
                Drawable decoded=ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(data)),(decoder,info,source)->{
                    int originalWidth=info.getSize().getWidth(), originalHeight=info.getSize().getHeight();
                    double scale=Math.min(1.0,512.0/Math.max(originalWidth,originalHeight));
                    int width=Math.max(1,(int)Math.round(originalWidth*scale));
                    int height=Math.max(1,(int)Math.round(originalHeight*scale));
                    decoder.setTargetSize(width,height);
                });
                final Drawable image=new CoverDrawable(decoded);
                current.runOnUiThread(()->{if(!current.isDestroyed() && !current.isFinishing() && url.equals(imageRequests.get(currentTarget))) {
                    // A recycled view or host binding can change padding/scale
                    // while decoding; configure against its current layout.
                    if(avatar)roundAvatar(currentTarget);
                    currentTarget.setImageDrawable(image);
                    if(image instanceof android.graphics.drawable.Animatable)((android.graphics.drawable.Animatable)image).start();
                }});
            } catch(Exception | OutOfMemoryError error) {
                if(cached!=null)cached.delete();
                current.runOnUiThread(()->{
                    if(current.isDestroyed() || current.isFinishing() || !url.equals(imageRequests.get(currentTarget)))return;
                    if(attempt<2 && !(error instanceof OutOfMemoryError)) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
                            if(!current.isDestroyed() && !current.isFinishing() && url.equals(imageRequests.get(currentTarget)))
                                loadImageAttempt(current,currentTarget,url,avatar,attempt+1);
                        },1000L*(attempt+1));
                    } else {
                        CrashDiagnostics.record(app,"Kattenfoto laden mislukt",error);
                        currentTarget.setContentDescription("Kat kon niet laden; heropen de instellingen om opnieuw te proberen");
                    }
                });
            }
        });
    }
}
