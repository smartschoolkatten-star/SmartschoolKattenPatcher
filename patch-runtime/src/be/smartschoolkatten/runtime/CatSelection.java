package be.smartschoolkatten.runtime;

import java.util.*;
import java.util.concurrent.*;

/** Selection never returns a disabled cached assignment; an empty pool falls back to the host. */
public final class CatSelection {
    private CatSelection() {}
    public static List<String> available(List<String> cats,Set<String> disabled) {
        List<String> result=new ArrayList<>();
        for(String cat:cats)if(!disabled.contains(cat))result.add(cat);
        return Collections.unmodifiableList(result);
    }
    public static String choose(String original,List<String> available,ConcurrentMap<String,String> assigned) {
        if(available.isEmpty() || available.contains(original))return original;
        while(true) {
            String previous=assigned.get(original);
            if(previous!=null) {
                if(available.contains(previous))return previous;
                assigned.remove(original,previous);continue;
            }
            String chosen=available.get(ThreadLocalRandom.current().nextInt(available.size()));
            if(assigned.putIfAbsent(original,chosen)==null)return chosen;
        }
    }
}
