package net.vanfleteren.jazygit.features.github.builds.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.git.model.BranchName;
import net.vanfleteren.jazygit.features.github.builds.BuildsShown;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import org.junit.jupiter.api.Test;

/**
 * Which failed build the builds of a branch hand to the keys other features add to it (ADR 161).
 */
class BuildsSourceTest {

    private static final BranchName FEAT = new BranchName("feat");
    private static final Instant T = Instant.parse("2026-11-19T10:00:00Z");

    private static Build build(long run, String sha) {
        return new Build(
                "CI",
                "push",
                run,
                1,
                sha,
                "Commit " + run,
                T,
                List.of(new Build.Job("verify", "FAILURE", Option.of(T), Option.of(T))));
    }

    private static BuildsShown shown(List<Build> builds, boolean allBranches, Set<String> mine, long picked) {
        return new BuildsShown(
                List.empty(),
                List.empty(),
                0,
                allBranches,
                new Loadable.Loaded<>(builds),
                builds.size(),
                mine,
                Option.none(),
                HashMap.empty(),
                Option.of(picked));
    }

    @Test
    void theBuildOnTheTimeLineOfTheBranchIsHandedOver() {
        Option<FailedBuild> failed = BuildsSource.failedBuild(
                FEAT, shown(List.of(build(401, "aaa")), false, HashSet.empty(), 401), Option.none());

        assertEquals(Option.of("aaa"), failed.flatMap(FailedBuild::commit));
    }

    @Test
    void withEveryBranchShownTheRunOfAnotherBranchIsNotHandedOverAsThisOnes() {
        BuildsShown other = shown(List.of(build(401, "main1"), build(402, "feat1")), true, HashSet.of("feat1"), 401);
        BuildsShown own = shown(List.of(build(401, "main1"), build(402, "feat1")), true, HashSet.of("feat1"), 402);

        assertEquals(Option.none(), BuildsSource.failedBuild(FEAT, other, Option.none()));
        assertEquals(
                Option.of("feat1"),
                BuildsSource.failedBuild(FEAT, own, Option.none()).flatMap(FailedBuild::commit));
    }
}
