package net.vanfleteren.jazygit.features.github.builds.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.BuildsPage;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.github.builds.BuildPickPopup;
import net.vanfleteren.jazygit.features.github.builds.BuildsAction;
import net.vanfleteren.jazygit.features.github.builds.BuildsAt;
import net.vanfleteren.jazygit.features.github.builds.BuildsMsg;
import net.vanfleteren.jazygit.features.github.builds.BuildsPopup;
import net.vanfleteren.jazygit.features.github.builds.BuildsShown;
import net.vanfleteren.jazygit.features.github.builds.BuildsView;
import net.vanfleteren.jazygit.features.github.builds.BuildsView.FileAt;
import net.vanfleteren.jazygit.features.github.builds.BuildsWindowPopup;
import net.vanfleteren.jazygit.features.github.builds.ReadWorkflows;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import org.junit.jupiter.api.Test;

/**
 * Choosing what the builds of a branch show: the workflow of a tab, how many builds, and the build whose jobs show
 * on the time line (ADR 131, 133).
 */
public class BuildsPickTest {

    @Test
    void bracketsStepThroughTheWorkflowsAndWChoosesOne() {
        Keymap keys = Keymap.standard();

        assertEquals(
                Option.of(new BuildsMsg.Stepped(1)),
                keys.handle(BuildsAction.SCOPE, KeyEvent.ofChar(']'), Option.none()));
        assertEquals(
                Option.of(new BuildsMsg.Stepped(-1)),
                keys.handle(BuildsAction.SCOPE, KeyEvent.ofChar('['), Option.none()));
        assertEquals(
                Option.of(new BuildsMsg.PickRequested()),
                keys.handle(BuildsAction.SCOPE, KeyEvent.ofChar('w'), Option.none()));
        assertEquals(
                Option.of(new BuildsMsg.Closed()),
                keys.handle(BuildsAction.SCOPE, KeyEvent.ofChar('B'), Option.none()));
        assertEquals(
                Option.of(new BuildsMsg.AllToggled()),
                keys.handle(BuildsAction.SCOPE, KeyEvent.ofChar('a'), Option.none()));
        assertEquals(
                Option.of(new BuildsMsg.WindowRequested()),
                keys.handle(BuildsAction.SCOPE, KeyEvent.ofChar('c'), Option.none()));
        assertEquals(
                Option.of(new BuildsMsg.BuildStepped(1)),
                keys.handle(BuildsAction.SCOPE, KeyEvent.ofChar('n'), Option.none()));
        assertEquals(
                Option.of(new BuildsMsg.BuildStepped(-1)),
                keys.handle(BuildsAction.SCOPE, KeyEvent.ofChar('N'), Option.none()));
    }

    @Test
    void aTabClickedShowsItsWorkflowAndBInTheBuildsShowsTheLog() {
        Model shown = Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                .model();
        BuildsView.Pipeline lint = new BuildsView.Pipeline("Markdown", "pull_request");

        Model clicked = Update.update(shown, new BuildsMsg.TabClicked(lint)).model();
        Model closed = Update.update(clicked, new BuildsMsg.Closed()).model();

        assertEquals(Option.of(lint), clicked.part(BuildsView.PART).get().selected());
        assertEquals(Option.none(), closed.part(BuildsView.PART));
    }

    @Test
    void aWorkflowChosenInThePopupShowsAndStaysForAnotherBranch() {
        Build markdown = new Build(
                "Markdown",
                "pull_request",
                2,
                1,
                new Sha("abcdef0"),
                "A commit",
                BuildsFixtures.BUILD.createdAt(),
                BuildsFixtures.BUILD.jobs());
        Model shown = Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                .model();
        Model read = Update.update(
                        shown,
                        new BuildsMsg.Read(
                                BuildsFixtures.ORIGIN_MAIN,
                                Option.none(),
                                Option.of(new BuildsPage(List.of(BuildsFixtures.BUILD, markdown), 3, Option.none()))))
                .model();
        BuildsView.Pipeline build = new BuildsView.Pipeline("Build", "push");
        BuildsView.Pipeline lint = new BuildsView.Pipeline("Markdown", "pull_request");
        assertEquals(
                build,
                ((BuildsBody) ((Shown.Area) BuildsFixtures.content(read)).body())
                        .shown()
                        .shown());

        Model asked = Update.update(read, new BuildsMsg.PickRequested()).model();
        assertEquals(
                List.of(build, lint),
                asked.popup(BuildsPopup.class).get().choices().map(BuildsPopup.Choice::pipeline));

        Model picked = Update.update(asked, new BuildsMsg.Picked(lint)).model();
        assertTrue(picked.popup(BuildsPopup.class).isEmpty());
        assertEquals(
                lint,
                ((BuildsBody) ((Shown.Area) BuildsFixtures.content(picked)).body())
                        .shown()
                        .shown());
        assertEquals(
                build,
                ((BuildsBody) ((Shown.Area) BuildsFixtures.content(Update.update(picked, new BuildsMsg.Stepped(1))
                                        .model()))
                                .body())
                        .shown()
                        .shown());

        Model again = Update.update(picked, new BuildsMsg.Toggled(BuildsFixtures.LOCAL))
                .model();
        assertEquals(Option.of(lint), again.part(BuildsView.PART).get().selected());
    }

    @Test
    void cAsksHowManyBuildsShowAndTheChoiceStaysForAnotherBranch() {
        Model shown = Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                .model();
        Model read = Update.update(
                        shown,
                        new BuildsMsg.Read(
                                BuildsFixtures.ORIGIN_MAIN,
                                Option.none(),
                                Option.of(new BuildsPage(
                                        List.of(BuildsFixtures.BUILD, BuildsFixtures.BUILD, BuildsFixtures.BUILD),
                                        3,
                                        Option.none()))))
                .model();

        Model asked = Update.update(read, new BuildsMsg.WindowRequested()).model();
        assertEquals(Option.of(new BuildsWindowPopup(Option.none())), asked.popup(BuildsWindowPopup.class));

        Model picked =
                Update.update(asked, new BuildsMsg.WindowPicked(Option.of(2))).model();
        BuildsShown drawn = ((BuildsBody) ((Shown.Area) BuildsFixtures.content(picked)).body()).shown();
        assertTrue(picked.popup(BuildsWindowPopup.class).isEmpty());
        assertEquals(2, drawn.builds().getOrElse(List.empty()).size());
        assertEquals(3, drawn.of());

        Model other = Update.update(picked, new BuildsMsg.Toggled(BuildsFixtures.LOCAL))
                .model();
        assertEquals(Option.of(2), other.part(BuildsView.PART).get().window());
    }

    @Test
    void aBuildPickedThatDoesNotShowLeavesTheLastBuildMarkedInThePopup() {
        Build before = new Build(
                "Build",
                "push",
                2,
                1,
                new Sha("1234567"),
                "Before",
                BuildsFixtures.BUILD.createdAt().minusSeconds(3600),
                BuildsFixtures.BUILD.jobs());
        Build lint = new Build(
                "Markdown",
                "pull_request",
                3,
                1,
                new Sha("abcdef0"),
                "A commit",
                BuildsFixtures.BUILD.createdAt(),
                BuildsFixtures.BUILD.jobs());
        Model read = Update.update(
                        Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                                .model(),
                        new BuildsMsg.Read(
                                BuildsFixtures.ORIGIN_MAIN,
                                Option.none(),
                                Option.of(
                                        new BuildsPage(List.of(BuildsFixtures.BUILD, lint, before), 2, Option.none()))))
                .model();
        Model picked = Update.update(
                        Update.update(read, new BuildsMsg.TabClicked(new BuildsView.Pipeline("Build", "push")))
                                .model(),
                        new BuildsMsg.BuildClicked(2))
                .model();
        assertEquals(
                Option.of(new BuildPickPopup(List.of(BuildsFixtures.BUILD, before), Option.of(2L))),
                Update.update(picked, new BuildsMsg.BuildPickRequested())
                        .model()
                        .popup(BuildPickPopup.class));

        // On another tab, the time line shows its last build: the popup marks that.
        Model otherTab = Update.update(
                        picked, new BuildsMsg.TabClicked(new BuildsView.Pipeline("Markdown", "pull_request")))
                .model();
        assertEquals(
                Option.of(new BuildPickPopup(List.of(lint), Option.none())),
                Update.update(otherTab, new BuildsMsg.BuildPickRequested())
                        .model()
                        .popup(BuildPickPopup.class));

        // Nor once fewer builds show than the one picked.
        Model fewer =
                Update.update(picked, new BuildsMsg.WindowPicked(Option.of(1))).model();
        assertEquals(
                Option.of(new BuildPickPopup(List.of(BuildsFixtures.BUILD), Option.none())),
                Update.update(fewer, new BuildsMsg.BuildPickRequested()).model().popup(BuildPickPopup.class));
        assertEquals(Option.of(2L), fewer.part(BuildsView.PART).get().picked());
    }

    @Test
    void pPicksTheBuildWhoseJobsShowOnTheTimeLineReadingItsWorkflowAtItsCommit() {
        String older = "1234567".repeat(5) + "12345";
        Build tip = BuildsFixtures.withFile(BuildsFixtures.BUILD);
        Build before = BuildsFixtures.withFile(new Build(
                "Build",
                "push",
                2,
                1,
                new Sha(older),
                "Before",
                BuildsFixtures.BUILD.createdAt().minusSeconds(3600),
                BuildsFixtures.BUILD.jobs()));
        Model read = Update.update(
                        Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                                .model(),
                        new BuildsMsg.Read(
                                BuildsFixtures.ORIGIN_MAIN,
                                Option.none(),
                                Option.of(new BuildsPage(List.of(tip, before), 2, Option.none()))))
                .model();

        Model asked = Update.update(read, new BuildsMsg.BuildPickRequested()).model();
        assertEquals(
                Option.of(new BuildPickPopup(List.of(tip, before), Option.none())), asked.popup(BuildPickPopup.class));

        Next picked = Update.update(asked, new BuildsMsg.BuildPicked(Option.of(2L)));
        assertTrue(picked.model().popup(BuildPickPopup.class).isEmpty());
        assertEquals(
                List.of(new ReadWorkflows(new FileAt(new Sha(older), ".github/workflows/publish.yml"))), picked.cmds());
        BuildsShown drawn = ((BuildsBody) ((Shown.Area) BuildsFixtures.content(picked.model())).body()).shown();
        assertEquals(Option.of(2L), drawn.picked());

        Model again =
                Update.update(picked.model(), new BuildsMsg.BuildClicked(2)).model();
        assertEquals(Option.none(), again.part(BuildsView.PART).get().picked());
        Model clicked = Update.update(again, new BuildsMsg.BuildClicked(1)).model();
        assertEquals(Option.of(1L), clicked.part(BuildsView.PART).get().picked());
    }

    @Test
    void nAndNStepThroughTheBuildsThatShowFromTheOneOnTheTimeLine() {
        Model read = steppable();

        // From the last build that is done, not the newest, which runs.
        Model next = Update.update(read, new BuildsMsg.BuildStepped(1)).model();
        assertEquals(Option.of(3L), picked(next));
        // It stops at the oldest.
        assertEquals(
                Option.of(3L),
                picked(Update.update(next, new BuildsMsg.BuildStepped(1)).model()));
        Model back = Update.update(next, new BuildsMsg.BuildStepped(-1)).model();
        assertEquals(Option.of(2L), picked(back));
        Model newest = Update.update(back, new BuildsMsg.BuildStepped(-1)).model();
        assertEquals(Option.of(1L), picked(newest));
        // And at the newest.
        assertEquals(
                Option.of(1L),
                picked(Update.update(newest, new BuildsMsg.BuildStepped(-1)).model()));
    }

    @Test
    void aPickThatNoLongerShowsIsSteppedFromTheLastBuildDoneThatShows() {
        Model oldest = Update.update(steppable(), new BuildsMsg.BuildClicked(3)).model();
        Model fewer =
                Update.update(oldest, new BuildsMsg.WindowPicked(Option.of(2))).model();

        // Build 3 is still picked, but no longer shows: the time line shows build 2, the last done of those that do.
        assertEquals(
                Option.of(1L),
                picked(Update.update(fewer, new BuildsMsg.BuildStepped(-1)).model()));
        assertEquals(
                Option.of(2L),
                picked(Update.update(fewer, new BuildsMsg.BuildStepped(1)).model()));
    }

    @Test
    void withNoBuildDoneNAndNPickTheNewest() {
        Build running = new Build(
                "Build", "push", 1, 1, new Sha("abcdef0"), "Running", BuildsFixtures.BUILD.createdAt(), List.empty());
        Build queued = new Build(
                "Build",
                "push",
                2,
                1,
                new Sha("1234567"),
                "Queued",
                BuildsFixtures.BUILD.createdAt().minusSeconds(60),
                List.empty());
        Model read = buildsRead(List.of(running, queued));

        assertEquals(
                Option.of(1L),
                picked(Update.update(read, new BuildsMsg.BuildStepped(1)).model()));
        assertEquals(
                Option.of(1L),
                picked(Update.update(read, new BuildsMsg.BuildStepped(-1)).model()));
    }

    @Test
    void withoutBuildsNAndNChangeNothing() {
        Model reading = Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                .model();
        assertEquals(
                reading, Update.update(reading, new BuildsMsg.BuildStepped(1)).model());

        Model none = buildsRead(List.empty());
        assertEquals(none, Update.update(none, new BuildsMsg.BuildStepped(1)).model());
    }

    @Test
    void whileASearchIsKeptNAndNDoNotStepTheBuilds() {
        Keymap keys = Keymap.standard();
        Option<BuildsAt> searching = Option.of(new BuildsAt(true));

        assertEquals(Option.none(), keys.handle(BuildsAction.SCOPE, KeyEvent.ofChar('n'), searching));
        assertEquals(Option.none(), keys.handle(BuildsAction.SCOPE, KeyEvent.ofChar('N'), searching));
        assertEquals(
                Option.of(new BuildsMsg.BuildStepped(1)),
                keys.handle(BuildsAction.SCOPE, KeyEvent.ofChar('n'), Option.of(new BuildsAt(false))));
    }

    /**
     * The builds of the main branch, read: one running, then two done, titled "Running", "Done" and "Older".
     */
    public static Model steppable() {
        Build tip = BuildsFixtures.withFile(new Build(
                "Build", "push", 1, 1, new Sha("abcdef0"), "Running", BuildsFixtures.BUILD.createdAt(), List.empty()));
        Build done = BuildsFixtures.withFile(new Build(
                "Build",
                "push",
                2,
                1,
                new Sha("1234567"),
                "Done",
                BuildsFixtures.BUILD.createdAt().minusSeconds(60),
                BuildsFixtures.BUILD.jobs()));
        Build older = BuildsFixtures.withFile(new Build(
                "Build",
                "push",
                3,
                1,
                new Sha("7654321"),
                "Older",
                BuildsFixtures.BUILD.createdAt().minusSeconds(120),
                BuildsFixtures.BUILD.jobs()));
        return buildsRead(List.of(tip, done, older));
    }

    private static Model buildsRead(List<Build> builds) {
        return Update.update(
                        Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                                .model(),
                        new BuildsMsg.Read(
                                BuildsFixtures.ORIGIN_MAIN,
                                Option.none(),
                                Option.of(new BuildsPage(builds, builds.size(), Option.none()))))
                .model();
    }

    private static Option<Long> picked(Model model) {
        return model.part(BuildsView.PART).get().picked();
    }
}
