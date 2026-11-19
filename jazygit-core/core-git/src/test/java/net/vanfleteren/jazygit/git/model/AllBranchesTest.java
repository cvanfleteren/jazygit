package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * What the branches read tell of the bases of the branches (ADR 20, 46).
 */
class AllBranchesTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName FEATURE = new BranchName("feature");

    private static RemoteRef remote(String remote, BranchName branch, Sha tip) {
        return new RemoteRef(new RemoteBranch(new RemoteName(remote), branch), tip, Instant.EPOCH);
    }

    @Test
    void theTipsAreThoseOfTheLocalBranchesAndOfTheOnesOnOriginOnly() {
        AllBranches branches = new AllBranches(
                List.of(Fixtures.branch(FEATURE, true, new Sha("aaaa"))),
                List.of(remote("origin", MAIN, new Sha("bbbb")), remote("upstream", MAIN, new Sha("cccc"))));

        assertEquals(
                HashMap.of(
                        new BranchRef.Local(FEATURE), new Sha("aaaa"),
                        new BranchRef.Remote(new RemoteBranch(new RemoteName("origin"), MAIN)), new Sha("bbbb")),
                branches.tips());
    }

    @Test
    void theMainBranchIsTheLocalOneThatIsTheDefault() {
        Branch main = Fixtures.branch(MAIN, false, new Sha("aaaa"), Instant.EPOCH, false, true);
        Branch feature = Fixtures.branch(FEATURE, true, new Sha("bbbb"));

        assertEquals(Option.of(MAIN), new AllBranches(List.of(feature, main), List.empty()).main());
        assertEquals(Option.none(), new AllBranches(List.of(feature), List.empty()).main());
    }
}
