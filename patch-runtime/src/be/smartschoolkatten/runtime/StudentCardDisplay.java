package be.smartschoolkatten.runtime;

import java.util.Locale;

/** Local display choice; never modifies a server response or permission. */
public final class StudentCardDisplay {
    private StudentCardDisplay() {}
    public static String content(String title,String original,boolean enabled) {
        if(!enabled || title==null || original==null)return original;
        String label=title.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s:]+"," ").trim();
        if(label.equals("mag school verlaten") && original.trim().equalsIgnoreCase("NEEN"))return "MA DI DO VR";
        return original;
    }
}
