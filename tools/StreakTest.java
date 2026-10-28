import be.smartschoolkatten.runtime.Streak;
import java.time.LocalDate;
public final class StreakTest {
    public static void main(String[] args) {
        LocalDate day=LocalDate.of(2026,10,7);
        Streak first=new Streak(0,0,null).click(day);
        check(first.count==1 && first.best==1,"first click");
        check(first.click(day)==first,"duplicate click");
        Streak second=first.click(day.plusDays(1));
        check(second.count==2 && second.best==2,"consecutive day");
        Streak skipped=second.refresh(day.plusDays(3));
        check(skipped.count==0 && skipped.best==2,"missed day clears only current streak");
        Streak resumed=skipped.click(day.plusDays(3));
        check(resumed.count==1 && resumed.best==2,"check-in after gap");
        Streak dst=new Streak(7,8,LocalDate.of(2026,10,24)).click(LocalDate.of(2026,10,25));
        check(dst.count==8,"calendar days across daylight saving");
        Streak year=new Streak(3,5,LocalDate.of(2026,12,31)).click(LocalDate.of(2027,1,1));
        check(year.count==4,"calendar year boundary");
        System.out.println("Streak: 7 scenario’s geslaagd");
    }
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
}
