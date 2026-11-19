import be.smartschoolkatten.runtime.UpdatePolicy;

public final class UpdatePolicyTest {
    public static void main(String[] args) {
        if(UpdatePolicy.version("katten-r45")!=120300045L)throw new AssertionError("Version code mismatch");
        if(!UpdatePolicy.newer("katten-r46",120300045L))throw new AssertionError("New release ignored");
        for(String tag:new String[]{null,"","r46","katten-r0","katten-r-1","katten-r46-beta","katten-r999999999","katten-r44","katten-r45"})
            if(UpdatePolicy.newer(tag,120300045L))throw new AssertionError("Invalid, same or older release accepted: "+tag);
        System.out.println("PASS APK release versions: newer, same, older and malformed tags");
    }
}
