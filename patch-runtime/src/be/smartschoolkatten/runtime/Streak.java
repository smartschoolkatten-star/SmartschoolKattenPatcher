package be.smartschoolkatten.runtime;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Calendar-day check-ins: duplicate clicks do not increment the streak. */
public final class Streak {
    public final int count, best;
    public final LocalDate last;
    public Streak(int count,int best,LocalDate last) {this.count=count;this.best=best;this.last=last;}
    public Streak refresh(LocalDate today) {
        return last!=null && ChronoUnit.DAYS.between(last,today)>=2 ? new Streak(0,best,last) : this;
    }
    public Streak click(LocalDate today) {
        if(today.equals(last))return this;
        int next=last!=null && ChronoUnit.DAYS.between(last,today)==1?count+1:1;
        return new Streak(next,Math.max(next,best),today);
    }
}
