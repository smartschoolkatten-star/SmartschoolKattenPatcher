package be.smartschoolkatten.runtime;

/** The master switch always takes precedence; preserve individual choices. */
public final class AvatarPolicy {
    private AvatarPolicy() {}
    public static boolean own(boolean master,boolean own) {return master && own;}
    public static boolean others(boolean master,boolean others) {return master && others;}
    public static boolean card(boolean master,boolean own,boolean card) {return master && own && card;}
}
