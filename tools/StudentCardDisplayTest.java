import be.smartschoolkatten.runtime.StudentCardDisplay;
import java.util.Objects;

public final class StudentCardDisplayTest {
    public static void main(String[] args) {
        check("NEEN",StudentCardDisplay.content("Mag school verlaten","NEEN",false));
        check("MA DI DO VR",StudentCardDisplay.content("Mag school verlaten","NEEN",true));
        check("MA DI DO VR",StudentCardDisplay.content(" Mag school verlaten: "," neen ",true));
        check("NEEN",StudentCardDisplay.content("Andere toestemming","NEEN",true));
        check("Onbekend",StudentCardDisplay.content("Mag school verlaten","Onbekend",true));
        check(null,StudentCardDisplay.content("Mag school verlaten",null,true));
        check("NEEN",StudentCardDisplay.content(null,"NEEN",true));
        System.out.println("PASS local display override: off, on, title matching, unrelated fields and missing values");
    }
    private static void check(String expected,String actual) {
        if(!Objects.equals(expected,actual))throw new AssertionError("Expected "+expected+", got "+actual);
    }
}
