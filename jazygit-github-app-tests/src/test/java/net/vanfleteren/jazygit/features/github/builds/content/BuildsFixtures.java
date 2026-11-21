package net.vanfleteren.jazygit.features.github.builds.content;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Upstream;
import net.vanfleteren.jazygit.kernel.content.ContentViews;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchesPanel;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;

/**
 * What the tests of the builds of a branch in the content panel share: the branches, a build, and the models that
 * show them.
 */
final class BuildsFixtures {

    private BuildsFixtures() {}

    static final BranchName MAIN = new BranchName("main");

    static final BranchName LOCAL = new BranchName("local");

    static final RemoteBranch ORIGIN_MAIN = new RemoteBranch("origin", MAIN);

    static final Build BUILD = new Build(
            "Build",
            "push",
            1,
            1,
            new Sha("abcdef0"),
            "A commit",
            Instant.parse("2026-11-14T23:00:00Z"),
            List.of(new Build.Job(
                    "verify",
                    "SUCCESS",
                    Option.of(Instant.parse("2026-11-14T23:00:05Z")),
                    Option.of(Instant.parse("2026-11-14T23:04:00Z")))));

    static Branch branch(BranchName name, Option<Upstream> upstream) {
        return Fixtures.branch(name, false, new Sha("abc"), Instant.EPOCH, true, false, upstream);
    }

    /**
     * The sample model with {@code main}, which follows {@code origin/main}, and {@code local}, which follows
     * nothing; {@code main} highlighted in the branches pane, its log read.
     */
    static Model model() {
        Model read = TestModels.localBranchesRead(
                        TestModels.loaded(),
                        List.of(
                                branch(MAIN, Option.of(new Upstream(new BranchRef.Remote(ORIGIN_MAIN), 0, 0))),
                                branch(LOCAL, Option.none())))
                .model();
        BranchRef main = new BranchRef.Local(MAIN);
        return Update.update(
                        Update.update(read, new SelectionMsg.BranchSelected(main))
                                .model(),
                        TestModels.branchLogLoaded(main, List.empty()))
                .model();
    }

    static Shown content(Model model) {
        return ContentViews.of(model, BranchesPanel.PANE);
    }

    static Build withFile(Build build) {
        return new Build(
                build.workflow(),
                build.event(),
                build.runId(),
                build.attempt(),
                build.sha(),
                build.title(),
                build.createdAt(),
                build.jobs(),
                Option.of(".github/workflows/publish.yml"));
    }
}
