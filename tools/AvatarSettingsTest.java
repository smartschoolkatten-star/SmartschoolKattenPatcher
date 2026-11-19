import android.content.SharedPreferences;
import be.smartschoolkatten.runtime.KattenRuntime;
import java.lang.reflect.*;
import java.util.*;

/** Exercise actual avatar routes with preferences, including the shared own-avatar route. */
public final class AvatarSettingsTest {
    public static final class User {
        private final String photo;
        User(String photo) {this.photo=photo;}
        public String getAvatarUrl() {return photo;}
    }
    public static final class Card {
        String displayed;
        public void setAvatar(String original) {displayed=KattenRuntime.displayAvatar(original);}
    }
    public static void main(String[] args)throws Exception {
        Map<String,Object> values=new HashMap<>();
        String ownPhoto="https://school.example/own.jpg",other="https://school.example/other.jpg",favorite="https://cats.example/favorite.jpg";
        SharedPreferences preferences=(SharedPreferences)Proxy.newProxyInstance(AvatarSettingsTest.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(p,m,a)->{
            if(m.getName().equals("getBoolean") || m.getName().equals("getString"))return values.getOrDefault(a[0],a[1]);
            throw new AssertionError("Unexpected preference operation "+m.getName());
        });
        set("preferences",preferences);set("cats",Arrays.asList(favorite));set("activeCats",Arrays.asList(favorite));
        values.put("favorite",favorite);
        // Host state must keep the server URL while cats are ON; the exact same
        // stored URL must render normally after turning own photos OFF.
        values.put("enabled",true);values.put("own-enabled",true);
        String retained=KattenRuntime.rememberOwnAvatar(ownPhoto);
        String retainedOther=KattenRuntime.rememberAvatar(other);
        check(other,retainedOther);
        check(ownPhoto,retained);check(favorite,KattenRuntime.displayAvatar(retained));
        values.put("own-enabled",false);check(ownPhoto,KattenRuntime.displayAvatar(retained));
        for(int bits=0;bits<16;bits++) {
            boolean master=(bits&1)!=0,own=(bits&2)!=0,others=(bits&4)!=0,card=(bits&8)!=0;
            values.put("enabled",master);values.put("own-enabled",own);values.put("others-enabled",others);values.put("student-card-enabled",card);
            check(ownPhoto,KattenRuntime.rememberOwnAvatar(ownPhoto));
            check(master&&own?favorite:ownPhoto,KattenRuntime.displayAvatar(retained));
            check(master&&own?favorite:ownPhoto,KattenRuntime.ownAvatar(ownPhoto));
            check(master&&own?favorite:ownPhoto,KattenRuntime.ownUserAvatar(new User(ownPhoto)));
            check(master&&own?favorite:null,KattenRuntime.ownUserAvatar(new User(null)));
            check(master&&own?favorite:"",KattenRuntime.ownUserAvatar(new User("")));
            check(master&&own?favorite:ownPhoto,KattenRuntime.avatar(ownPhoto));
            check(master&&others?favorite:other,KattenRuntime.avatar(other));
            check(master&&others?favorite:other,KattenRuntime.displayAvatar(retainedOther));
            check(master&&others?favorite:other,KattenRuntime.plannerAvatar(other));
            check(master&&own&&card?favorite:ownPhoto,KattenRuntime.studentCardAvatar(ownPhoto));
            Card view=new Card();KattenRuntime.bindStudentCardAvatar(view,ownPhoto);
            check(master&&own&&card?favorite:ownPhoto,view.displayed);
            check(master&&own?favorite:ownPhoto,KattenRuntime.displayAvatar(retained));
        }
        values.put("enabled",true);values.put("others-enabled",true);
        check(favorite,KattenRuntime.avatar(favorite));
        String nextFavorite="https://cats.example/next.jpg";
        set("cats",Arrays.asList(favorite,nextFavorite));set("activeCats",Arrays.asList(favorite,nextFavorite));
        values.put("own-enabled",true);values.put("favorite",nextFavorite);
        User retainedUser=new User(ownPhoto);
        check(nextFavorite,KattenRuntime.ownUserAvatar(retainedUser));
        check(ownPhoto,retainedUser.getAvatarUrl());
        values.put("enabled",false);
        check(ownPhoto,KattenRuntime.ownUserAvatar(retainedUser));
        System.out.println("PASS actual avatar, own/shared avatar, planner and favorite-card routes across all 16 switch combinations");
    }
    private static void set(String name,Object value)throws Exception {Field f=KattenRuntime.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
    private static void check(String expected,String actual) {if(!Objects.equals(expected,actual))throw new AssertionError("Expected "+expected+", got "+actual);}
}
