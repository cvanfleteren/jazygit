package net.vanfleteren.jazygit.features.github.builds.content;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.BuildsPage;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.Upstream;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.features.github.builds.BuildsMsg;
import net.vanfleteren.jazygit.features.github.builds.BuildsShown;
import net.vanfleteren.jazygit.features.github.builds.BuildsView;
import net.vanfleteren.jazygit.features.github.builds.LoadBuilds;
import net.vanfleteren.jazygit.features.github.builds.Paging;
import net.vanfleteren.jazygit.features.github.checks.branches.BranchChecksMsg;
import net.vanfleteren.jazygit.features.github.state.GitHubChecks;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;
import net.vanfleteren.jazygit.kernel.ui.content.Tone;
import net.vanfleteren.jazygit.kernel.ui.text.Placeholders;
import org.junit.jupiter.api.Test;

/**
 * Showing the builds of a local branch on GitHub in place of its log, read some commits at a time (ADR 131).
 */
class BuildsContentTest {

    private static BuildsPage page(int commits, Option<String> next) {
        return new BuildsPage(List.of(BuildsFixtures.BUILD), commits, next);
    }

    @Test
    void bOnABranchReadsTheBuildsOfTheBranchItFollowsFromItsTip() {
        Next asked = Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN));

        assertEquals(
                Option.of(BuildsView.reading(BuildsFixtures.MAIN, BuildsFixtures.ORIGIN_MAIN)),
                asked.model().part(BuildsView.PART));
        assertEquals(List.of(new LoadBuilds(BuildsFixtures.ORIGIN_MAIN, Option.none())), asked.cmds());
        assertEquals(
                "Builds: main · reading, 0 commits so far",
                ((Shown.Message) BuildsFixtures.content(asked.model())).title());
    }

    @Test
    void bAgainShowsTheLogAgain() {
        Model shown = Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                .model();

        Next again = Update.update(shown, new BuildsMsg.Toggled(BuildsFixtures.MAIN));

        assertEquals(Option.none(), again.model().part(BuildsView.PART));
        assertEquals(
                AreaId.LOG,
                ((Shown.Area) BuildsFixtures.content(again.model())).area().id());
    }

    /**
     * The builds take the focus from the branches for the branch B opened them for, and give it back once B closes
     * them (ADR 155).
     */
    @Test
    void theAreaOfTheBuildsHasOpenTheBranchBOpenedThemFor() {
        Model shown = Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                .model();
        Model closed =
                Update.update(shown, new BuildsMsg.Toggled(BuildsFixtures.MAIN)).model();

        assertEquals(Option.none(), BuildsSource.AREA.opened(BuildsFixtures.model()));
        assertEquals(Option.of(BuildsFixtures.MAIN), BuildsSource.AREA.opened(shown));
        assertEquals(Option.none(), BuildsSource.AREA.opened(closed));
    }

    @Test
    void theFirstCommitsShowWhileTheNextAreRead() {
        Model shown = Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                .model();

        Next read = Update.update(
                shown,
                new BuildsMsg.Read(BuildsFixtures.ORIGIN_MAIN, Option.none(), Option.of(page(25, Option.of("c25")))));

        assertEquals(List.of(new LoadBuilds(BuildsFixtures.ORIGIN_MAIN, Option.of("c25"))), read.cmds());
        Shown.Area area = (Shown.Area) BuildsFixtures.content(read.model());
        assertEquals(BuildsSource.AREA, area.area());
        assertEquals("Builds: main · reading, 25 commits so far", area.title());
        BuildsShown drawn = ((BuildsBody) area.body()).shown();
        assertEquals(new BuildsView.Pipeline("Build", "push"), drawn.shown());
        assertEquals(new Loadable.Loaded<>(List.of(BuildsFixtures.BUILD)), drawn.builds());
        assertEquals(Option.none(), drawn.baseline());
    }

    @Test
    void itStopsReadingAtAHundredCommits() {
        Model reading = Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                .model();
        Option<String> after = Option.none();
        for (int page = 1; page <= 4; page++) {
            Next read = Update.update(
                    reading,
                    new BuildsMsg.Read(
                            BuildsFixtures.ORIGIN_MAIN, after, Option.of(page(25, Option.of("c" + page * 25)))));
            reading = read.model();
            after = Option.of("c" + page * 25);
            assertEquals(page < 4, !read.cmds().isEmpty());
        }

        BuildsView view = reading.part(BuildsView.PART).get();
        assertFalse(view.reading());
        assertEquals(100, view.commits());
        assertEquals(4, view.builds().getOrElse(List.empty()).size());
        assertEquals(
                "Builds: main · last 100 commits · m for more", ((Shown.Area) BuildsFixtures.content(reading)).title());
    }

    @Test
    void mReadsAHundredMoreCommitsFromWhereThoseReadEnd() {
        Model read = Update.update(
                        Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                                .model(),
                        new BuildsMsg.Read(
                                BuildsFixtures.ORIGIN_MAIN, Option.none(), Option.of(page(100, Option.of("c100")))))
                .model();

        Next more = Update.update(read, new BuildsMsg.MoreRequested());

        assertEquals(List.of(new LoadBuilds(BuildsFixtures.ORIGIN_MAIN, Option.of("c100"))), more.cmds());
        assertEquals(
                "Builds: main · reading, 100 commits so far",
                ((Shown.Area) BuildsFixtures.content(more.model())).title());
        assertTrue(Update.update(more.model(), new BuildsMsg.MoreRequested())
                .cmds()
                .isEmpty());

        Next next = Update.update(
                more.model(),
                new BuildsMsg.Read(
                        BuildsFixtures.ORIGIN_MAIN, Option.of("c100"), Option.of(page(75, Option.of("c175")))));
        assertEquals(List.of(new LoadBuilds(BuildsFixtures.ORIGIN_MAIN, Option.of("c175"))), next.cmds());
        Model done = Update.update(
                        next.model(),
                        new BuildsMsg.Read(
                                BuildsFixtures.ORIGIN_MAIN, Option.of("c175"), Option.of(page(25, Option.of("c200")))))
                .model();
        BuildsView view = done.part(BuildsView.PART).get();
        assertFalse(view.reading());
        assertEquals(200, view.commits());
        assertEquals(3, view.builds().getOrElse(List.empty()).size());
        assertEquals(
                "Builds: main · last 200 commits · m for more", ((Shown.Area) BuildsFixtures.content(done)).title());
    }

    @Test
    void mReadsNothingAtTheOldestCommit() {
        Model read = Update.update(
                        Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                                .model(),
                        new BuildsMsg.Read(
                                BuildsFixtures.ORIGIN_MAIN, Option.none(), Option.of(page(3, Option.none()))))
                .model();

        Next more = Update.update(read, new BuildsMsg.MoreRequested());

        assertSame(read, more.model());
        assertTrue(more.cmds().isEmpty());
        assertEquals("Builds: main · last 3 commits", ((Shown.Area) BuildsFixtures.content(read)).title());
    }

    @Test
    void mReadsAgainFromWhereReadingFailed() {
        Model read = Update.update(
                        Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                                .model(),
                        new BuildsMsg.Read(
                                BuildsFixtures.ORIGIN_MAIN, Option.none(), Option.of(page(25, Option.of("c25")))))
                .model();
        Model stopped = Update.update(
                        read,
                        new BuildsMsg.Failed(
                                BuildsFixtures.ORIGIN_MAIN,
                                Option.of("c25"),
                                GitHubFailure.of(new GhError.UnexpectedOutput("timeout"))))
                .model();

        Next again = Update.update(stopped, new BuildsMsg.MoreRequested());

        assertEquals(
                "Builds: main · 25 commits, could not read more · m to try again",
                ((Shown.Area) BuildsFixtures.content(stopped)).title());
        assertEquals(List.of(new LoadBuilds(BuildsFixtures.ORIGIN_MAIN, Option.of("c25"))), again.cmds());
        BuildsView view = again.model().part(BuildsView.PART).get();
        assertEquals(new Paging.Reading(Option.of("c25")), view.paging());
        assertEquals(125, view.limit());
    }

    @Test
    void itStopsAtTheOldestCommit() {
        Model shown = Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                .model();

        Next read = Update.update(
                shown,
                new BuildsMsg.Read(BuildsFixtures.ORIGIN_MAIN, Option.none(), Option.of(page(3, Option.none()))));

        assertTrue(read.cmds().isEmpty());
        assertFalse(read.model().part(BuildsView.PART).get().reading());
    }

    @Test
    void commitsNoLongerAwaitedAreLeftOut() {
        Model shown = Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                .model();

        Next stale = Update.update(
                shown,
                new BuildsMsg.Read(BuildsFixtures.ORIGIN_MAIN, Option.of("c50"), Option.of(page(25, Option.none()))));

        assertSame(shown, stale.model());
        assertTrue(stale.cmds().isEmpty());
    }

    @Test
    void aBranchNotOnGitHubHasNoBuilds() {
        Model shown = Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                .model();

        Model read = Update.update(shown, new BuildsMsg.Read(BuildsFixtures.ORIGIN_MAIN, Option.none(), Option.none()))
                .model();

        assertEquals(
                new Shown.Message("Builds: main · last 0 commits", "origin/main is not on GitHub", Tone.DIM),
                BuildsFixtures.content(read));
    }

    @Test
    void commitsWithoutBuildsSayHowManyWereRead() {
        Model shown = Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                .model();

        Model read = Update.update(
                        shown,
                        new BuildsMsg.Read(
                                BuildsFixtures.ORIGIN_MAIN,
                                Option.none(),
                                Option.of(new BuildsPage(List.empty(), 25, Option.none()))))
                .model();

        assertEquals(
                new Shown.Message(
                        "Builds: main · last 25 commits",
                        "No builds on the last 25 commits of origin/main on GitHub",
                        Tone.DIM),
                BuildsFixtures.content(read));
    }

    @Test
    void commitsWithoutBuildsWhileMoreAreReadShowLoading() {
        Model shown = Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                .model();

        Model read = Update.update(
                        shown,
                        new BuildsMsg.Read(
                                BuildsFixtures.ORIGIN_MAIN,
                                Option.none(),
                                Option.of(new BuildsPage(List.empty(), 25, Option.of("c25")))))
                .model();

        assertEquals(
                new Shown.Message("Builds: main · reading, 25 commits so far", Placeholders.loading(), Tone.DIM),
                BuildsFixtures.content(read));
    }

    @Test
    void aBranchThatFollowsNoneSaysSoWithoutReading() {
        Model local = Update.update(
                        BuildsFixtures.model(),
                        new SelectionMsg.BranchSelected(new BranchRef.Local(BuildsFixtures.LOCAL)))
                .model();
        local = Update.update(
                        local, TestModels.branchLogLoaded(new BranchRef.Local(BuildsFixtures.LOCAL), List.empty()))
                .model();

        Next asked = Update.update(local, new BuildsMsg.Toggled(BuildsFixtures.LOCAL));

        assertTrue(asked.cmds().isEmpty());
        assertEquals(
                "local follows no branch on GitHub, so it has no builds to show",
                ((Shown.Message) BuildsFixtures.content(asked.model())).text());
    }

    @Test
    void aFailedFirstReadShowsWhy() {
        Model shown = Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                .model();

        Model failed = Update.update(
                        shown,
                        new BuildsMsg.Failed(
                                BuildsFixtures.ORIGIN_MAIN,
                                Option.none(),
                                GitHubFailure.of(new GhError.UnexpectedOutput("timeout"))))
                .model();

        assertInstanceOf(
                Loadable.Failed.class, failed.part(BuildsView.PART).get().builds());
        assertEquals(Tone.ERROR, ((Shown.Message) BuildsFixtures.content(failed)).tone());
    }

    @Test
    void mReadsAgainFromTheTipWhenTheFirstReadFailed() {
        Model failed = Update.update(
                        Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                                .model(),
                        new BuildsMsg.Failed(
                                BuildsFixtures.ORIGIN_MAIN,
                                Option.none(),
                                GitHubFailure.of(new GhError.UnexpectedOutput("timeout"))))
                .model();
        assertEquals(
                "Builds: main · last 0 commits · m to try again",
                ((Shown.Message) BuildsFixtures.content(failed)).title());

        Next again = Update.update(failed, new BuildsMsg.MoreRequested());

        assertEquals(List.of(new LoadBuilds(BuildsFixtures.ORIGIN_MAIN, Option.none())), again.cmds());
        BuildsView view = again.model().part(BuildsView.PART).get();
        assertTrue(view.reading());
        assertInstanceOf(Loadable.Loading.class, view.builds());
        assertTrue(Update.update(again.model(), new BuildsMsg.MoreRequested())
                .cmds()
                .isEmpty());
        Model read = Update.update(
                        again.model(),
                        new BuildsMsg.Read(
                                BuildsFixtures.ORIGIN_MAIN, Option.none(), Option.of(page(3, Option.none()))))
                .model();
        assertEquals(
                new Loadable.Loaded<>(List.of(BuildsFixtures.BUILD)),
                read.part(BuildsView.PART).get().builds());
    }

    @Test
    void aFailedLaterReadKeepsWhatWasRead() {
        Model shown = Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                .model();
        Model first = Update.update(
                        shown,
                        new BuildsMsg.Read(
                                BuildsFixtures.ORIGIN_MAIN, Option.none(), Option.of(page(25, Option.of("c25")))))
                .model();

        Next failed = Update.update(
                first,
                new BuildsMsg.Failed(
                        BuildsFixtures.ORIGIN_MAIN,
                        Option.of("c25"),
                        GitHubFailure.of(new GhError.UnexpectedOutput("timeout"))));

        assertTrue(failed.cmds().isEmpty());
        assertEquals(
                List.of(BuildsFixtures.BUILD),
                failed.model().part(BuildsView.PART).get().builds().getOrElse(List.empty()));
        assertEquals(
                "Builds: main · 25 commits, could not read more · m to try again",
                ((Shown.Area) BuildsFixtures.content(failed.model())).title());
    }

    @Test
    void anotherBranchHighlightedShowsItsLog() {
        Model shown = Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                .model();
        shown = Update.update(
                        shown,
                        new BuildsMsg.Read(
                                BuildsFixtures.ORIGIN_MAIN, Option.none(), Option.of(page(3, Option.none()))))
                .model();

        Model other = Update.update(shown, new SelectionMsg.BranchSelected(new BranchRef.Local(BuildsFixtures.LOCAL)))
                .model();
        other = Update.update(
                        other, TestModels.branchLogLoaded(new BranchRef.Local(BuildsFixtures.LOCAL), List.empty()))
                .model();

        assertEquals(
                AreaId.LOG, ((Shown.Area) BuildsFixtures.content(other)).area().id());
    }

    @Test
    void aBranchOnARemoteNotOnGitHubSaysSoWithoutReading() {
        RemoteBranch elsewhere = new RemoteBranch("gitlab", BuildsFixtures.LOCAL);
        Model read = TestModels.localBranchesRead(
                        BuildsFixtures.model(),
                        List.of(
                                BuildsFixtures.branch(
                                        BuildsFixtures.MAIN,
                                        Option.of(
                                                new Upstream(new BranchRef.Remote(BuildsFixtures.ORIGIN_MAIN), 0, 0))),
                                BuildsFixtures.branch(
                                        BuildsFixtures.LOCAL,
                                        Option.of(new Upstream(new BranchRef.Remote(elsewhere), 0, 0)))))
                .model();
        Model known = Update.update(
                        read,
                        new BranchChecksMsg.Loaded(
                                new GitHubChecks(HashSet.of(new RemoteName("origin")), HashMap.empty())))
                .model();
        Model local = Update.update(known, new SelectionMsg.BranchSelected(new BranchRef.Local(BuildsFixtures.LOCAL)))
                .model();
        local = Update.update(
                        local, TestModels.branchLogLoaded(new BranchRef.Local(BuildsFixtures.LOCAL), List.empty()))
                .model();

        Next asked = Update.update(local, new BuildsMsg.Toggled(BuildsFixtures.LOCAL));

        assertTrue(asked.cmds().isEmpty());
        assertEquals(
                "local follows no branch on GitHub, so it has no builds to show",
                ((Shown.Message) BuildsFixtures.content(asked.model())).text());
    }
}
