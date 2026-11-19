package be.smartschoolkatten.runtime;

/** Release tags and Android version codes share one increasing revision. */
public final class UpdatePolicy {
    public static final String REPOSITORY="smartschoolkatten-star/SmartschoolKattenPatcher";
    public static final String ASSET="Smartschool-Katten.apk";
    public static final long VERSION_BASE=120300000L;
    private UpdatePolicy() {}
    public static long version(String tag) {
        if(tag==null || !tag.matches("katten-r[1-9][0-9]{0,5}"))return -1;
        return VERSION_BASE+Long.parseLong(tag.substring(8));
    }
    public static boolean newer(String tag,long installed) {return version(tag)>installed;}
}
