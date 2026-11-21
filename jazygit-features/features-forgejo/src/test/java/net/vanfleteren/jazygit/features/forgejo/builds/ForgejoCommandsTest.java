package net.vanfleteren.jazygit.features.forgejo.builds;

import static net.vanfleteren.jazygit.features.forgejo.builds.FakeForgejo.origin;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.forgejo.ForgejoFeature;
import net.vanfleteren.jazygit.kernel.state.FeatureMsg;
import org.junit.jupiter.api.Test;

class ForgejoCommandsTest {

    private static final Sha PASSED = new Sha("a".repeat(40));
    private static final Sha UNKNOWN = new Sha("b".repeat(40));
    private static final String REMOTE = "ssh://git@code.example.net:10022/cvf/nix.git";

    private static final FakeForgejo FORGEJO =
            new FakeForgejo(HashSet.of("code.example.net"), HashMap.of(PASSED, CommitStatus.State.PASSED));

    @Test
    void forgejoAppliesWithARemoteOnAHostThatIsForgejo() {
        assertEquals(
                new FeatureMsg.Applies(ForgejoFeature.ID, true), new DetectForgejo().run(FORGEJO.env(origin(REMOTE))));
    }

    @Test
    void itDoesNotWithoutOne() {
        assertEquals(
                new FeatureMsg.Applies(ForgejoFeature.ID, false),
                new DetectForgejo().run(FORGEJO.env(origin("git@github.com:cvanfleteren/jazygit.git"))));
        assertEquals(
                new FeatureMsg.Applies(ForgejoFeature.ID, false),
                new DetectForgejo().run(FORGEJO.env(origin("https://elsewhere.example.net/cvf/nix.git"))));
    }

    @Test
    void theBuildsOfTheTipsAreReadAndThoseThatCannotBeAreLeftOut() {
        List<Tip> tips = List.of(
                new Tip(new RemoteName("origin"), PASSED),
                new Tip(new RemoteName("origin"), UNKNOWN),
                new Tip(new RemoteName("gone"), PASSED));
        // A remote that is gone is on no Forgejo.
        assertEquals(
                new BuildsMsg.Read(
                        tips, HashMap.of(PASSED, CommitStatus.State.PASSED), HashSet.of(new RemoteName("gone"))),
                new ReadBuilds(tips).run(FORGEJO.env(origin(REMOTE))));
    }

    @Test
    void aRemoteThatIsNotOnForgejoIsNotAskedAndIsSaidToBeElsewhere() {
        List<Tip> tips = List.of(new Tip(new RemoteName("origin"), PASSED));
        assertEquals(
                new BuildsMsg.Read(tips, HashMap.empty(), HashSet.of(new RemoteName("origin"))),
                new ReadBuilds(tips).run(FORGEJO.env(origin("https://elsewhere.example.net/cvf/nix.git"))));
    }
}
