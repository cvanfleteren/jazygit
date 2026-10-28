package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.PushDefault;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import org.junit.jupiter.api.Test;

class CliPushTest {

    private static final BranchName FEATURE = new BranchName("feature");
    private static final RemoteBranch DEVELOP = new RemoteBranch("origin", new BranchName("develop"));
    private static final RemoteBranch SAME_NAME = new RemoteBranch("origin", FEATURE);

    @Test
    void simplePushesOnlyToAnUpstreamOfTheSameName() {
        assertEquals(Option.of(SAME_NAME), CliPush.target(PushDefault.SIMPLE, FEATURE, SAME_NAME));
        assertEquals(Option.none(), CliPush.target(PushDefault.SIMPLE, FEATURE, DEVELOP));
    }

    @Test
    void currentAndMatchingPushToTheSameNameOnTheUpstreamsRemote() {
        assertEquals(Option.of(SAME_NAME), CliPush.target(PushDefault.CURRENT, FEATURE, DEVELOP));
        assertEquals(Option.of(SAME_NAME), CliPush.target(PushDefault.MATCHING, FEATURE, DEVELOP));
    }

    @Test
    void upstreamPushesToTheUpstream() {
        assertEquals(Option.of(DEVELOP), CliPush.target(PushDefault.UPSTREAM, FEATURE, DEVELOP));
    }

    @Test
    void nothingPushesNowhere() {
        assertEquals(Option.none(), CliPush.target(PushDefault.NOTHING, FEATURE, SAME_NAME));
    }

    @Test
    void pushDefaultIsReadAsGitReadsIt() {
        assertEquals(Option.of(PushDefault.UPSTREAM), PushDefault.parse("tracking"));
        assertEquals(Option.of(PushDefault.UPSTREAM), PushDefault.parse("upstream"));
        assertEquals(Option.of(PushDefault.SIMPLE), PushDefault.parse(" simple\n"));
        assertEquals(Option.none(), PushDefault.parse("sideways"));
        assertEquals(Option.none(), PushDefault.parse("Simple"));
    }
}
