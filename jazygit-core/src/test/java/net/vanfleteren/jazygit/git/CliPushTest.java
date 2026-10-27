package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import org.junit.jupiter.api.Test;

class CliPushTest {

    private static final BranchName FEATURE = new BranchName("feature");
    private static final RemoteBranch DEVELOP = new RemoteBranch("origin", new BranchName("develop"));
    private static final RemoteBranch SAME_NAME = new RemoteBranch("origin", FEATURE);

    @Test
    void simplePushesOnlyToAnUpstreamOfTheSameName() {
        assertEquals(Option.of(SAME_NAME), CliPush.target("simple", FEATURE, SAME_NAME));
        assertEquals(Option.none(), CliPush.target("simple", FEATURE, DEVELOP));
    }

    @Test
    void currentAndMatchingPushToTheSameNameOnTheUpstreamsRemote() {
        assertEquals(Option.of(SAME_NAME), CliPush.target("current", FEATURE, DEVELOP));
        assertEquals(Option.of(SAME_NAME), CliPush.target("matching", FEATURE, DEVELOP));
    }

    @Test
    void upstreamAndTrackingPushToTheUpstream() {
        assertEquals(Option.of(DEVELOP), CliPush.target("upstream", FEATURE, DEVELOP));
        assertEquals(Option.of(DEVELOP), CliPush.target("tracking", FEATURE, DEVELOP));
    }

    @Test
    void nothingPushesNowhere() {
        assertEquals(Option.none(), CliPush.target("nothing", FEATURE, SAME_NAME));
    }

    @Test
    void aValueGitDoesNotKnowIsTakenAsSimple() {
        assertEquals(Option.none(), CliPush.target("sideways", FEATURE, DEVELOP));
        assertEquals(Option.of(SAME_NAME), CliPush.target("sideways", FEATURE, SAME_NAME));
    }
}
