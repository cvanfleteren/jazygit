package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyEventKind;
import dev.tamboui.tui.event.KeyModifiers;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.BranchName;
import net.vanfleteren.jazygit.core.git.model.BranchRef;
import net.vanfleteren.jazygit.core.git.model.Commit;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.git.model.RemoteBranch;
import net.vanfleteren.jazygit.core.git.model.Sha;
import net.vanfleteren.jazygit.features.agent.investigate.InvestigateMsg;
import net.vanfleteren.jazygit.features.branches.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.features.branches.branch.commits.BranchCommitsMsg;
import net.vanfleteren.jazygit.features.branches.branch.delete.DeleteBranchMsg;
import net.vanfleteren.jazygit.features.branches.repos.RecentReposMsg;
import net.vanfleteren.jazygit.features.changes.diff.DiffOptionsMsg;
import net.vanfleteren.jazygit.features.changes.inprogress.InProgressMsg;
import net.vanfleteren.jazygit.features.chrome.help.HelpMsg;
import net.vanfleteren.jazygit.features.chrome.layout.LayoutMsg;
import net.vanfleteren.jazygit.features.chrome.settings.SettingsPanelMsg;
import net.vanfleteren.jazygit.features.github.browse.BrowseMsg;
import net.vanfleteren.jazygit.features.github.builds.BuildsAction;
import net.vanfleteren.jazygit.features.github.checks.ChecksAction;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import net.vanfleteren.jazygit.kernel.state.github.prs.PrOverviewMsg;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import org.junit.jupiter.api.Test;

class KeymapTest {

    private final Keymap keymap = Keymap.standard();

    @Test
    void ctrlSShowsTheSettings() {
        assertEquals(
                Option.of(new SettingsPanelMsg.Requested()),
                keymap.handleGlobal(KeyEvent.ofChar('s', dev.tamboui.tui.event.KeyModifiers.CTRL)));
        assertEquals(Option.none(), keymap.handleGlobal(KeyEvent.ofChar('s')));
    }

    @Test
    void ctrlPSwitchesBetweenTheRepositoryAndThePullRequests() {
        assertEquals(
                Option.of(new PrOverviewMsg.Toggled()),
                keymap.handleGlobal(KeyEvent.ofChar('p', dev.tamboui.tui.event.KeyModifiers.CTRL)));
        // Plain p is left to the panels.
        assertEquals(Option.none(), keymap.handleGlobal(KeyEvent.ofChar('p')));
        assertTrue(keymap.globals().exists(global -> global.label().equals("ctrl+p")));
    }

    @Test
    void ctrlRAsksWhichRecentRepositoryToSwitchTo() {
        assertEquals(
                Option.of(new RecentReposMsg.Requested()),
                keymap.handleGlobal(KeyEvent.ofChar('r', dev.tamboui.tui.event.KeyModifiers.CTRL)));
        assertEquals(Option.none(), keymap.handleGlobal(KeyEvent.ofChar('r')));
    }

    @Test
    void ctrlDShowsTheDiffOptionsAndTheirSwitchesHaveNoKeysOfTheirOwn() {
        assertEquals(
                Option.of(new DiffOptionsMsg.Requested()),
                keymap.handleGlobal(KeyEvent.ofChar('d', dev.tamboui.tui.event.KeyModifiers.CTRL)));
        assertTrue(keymap.globals().exists(global -> global.label().equals("ctrl+d")));
        for (char key : new char[] {'f', 'w', 'e'}) {
            assertEquals(
                    Option.none(), keymap.handleGlobal(KeyEvent.ofChar(key, dev.tamboui.tui.event.KeyModifiers.CTRL)));
        }
    }

    @Test
    void bracesShowMoreAndLessContextInTheDiffs() {
        assertEquals(Option.of(new DiffOptionsMsg.ContextWidened()), keymap.handleGlobal(KeyEvent.ofChar('}')));
        assertEquals(Option.of(new DiffOptionsMsg.ContextNarrowed()), keymap.handleGlobal(KeyEvent.ofChar('{')));
        assertTrue(keymap.globals().exists(global -> global.label().equals("}")));
        assertTrue(keymap.globals().exists(global -> global.label().equals("{")));
    }

    @Test
    void shiftMAsksWhatToDoWithTheOperationInProgressFromAnywhere() {
        assertEquals(Option.of(new InProgressMsg.Requested()), keymap.handleGlobal(KeyEvent.ofChar('M')));
        assertEquals(Option.none(), keymap.handleGlobal(KeyEvent.ofChar('m')));
        assertTrue(keymap.globals().exists(global -> global.label().equals("M")));
    }

    @Test
    void lessAndGreaterThanMakeTheLeftColumnNarrowerAndWider() {
        assertEquals(Option.of(new LayoutMsg.Stepped(-5)), keymap.handleGlobal(KeyEvent.ofChar('<')));
        assertEquals(Option.of(new LayoutMsg.Stepped(5)), keymap.handleGlobal(KeyEvent.ofChar('>')));
        assertEquals(Option.none(), keymap.handleGlobal(KeyEvent.ofChar('>', dev.tamboui.tui.event.KeyModifiers.ALT)));
        assertTrue(keymap.globals().exists(global -> global.label().equals("<")));
    }

    @Test
    void plusMinusAndShiftZResizeAndMaximizeTheFocusedPane() {
        assertEquals(Option.of(new LayoutMsg.PaneStepped(5)), keymap.handleGlobal(KeyEvent.ofChar('+')));
        assertEquals(Option.of(new LayoutMsg.PaneStepped(-5)), keymap.handleGlobal(KeyEvent.ofChar('-')));
        assertEquals(Option.of(new LayoutMsg.MaximizeToggled()), keymap.handleGlobal(KeyEvent.ofChar('Z')));
        // Lazygit users press z for undo, which must not maximize a pane.
        assertEquals(Option.none(), keymap.handleGlobal(KeyEvent.ofChar('z')));
        assertEquals(Option.none(), keymap.handleGlobal(KeyEvent.ofChar('-', dev.tamboui.tui.event.KeyModifiers.ALT)));
        assertEquals(Option.none(), keymap.handleGlobal(KeyEvent.ofChar('z', dev.tamboui.tui.event.KeyModifiers.CTRL)));
        assertTrue(keymap.globals().exists(global -> global.label().equals("Z")));
    }

    @Test
    void aKeyCannotBeBoundTwiceInAPanel() {
        Keymap once = Keymap.empty()
                .add(
                        Scopes.BRANCHES,
                        Keymap.Binding.onSelected("x", "app.help.global.quit", CheckoutMsg.Requested::new));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> once.add(
                        Scopes.BRANCHES,
                        Keymap.Binding.onSelected("x", "app.help.global.quit", CheckoutMsg.Requested::new)));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> once.add(
                        Scopes.BRANCHES,
                        Keymap.Binding.onSelected(
                                Keymap.HELP_KEY, "app.help.global.quit", CheckoutMsg.Requested::new)));
    }

    @Test
    void theKeypadsEnterIsEnterButNotWithCtrl() {
        BranchName branch = new BranchName("x");

        assertEquals(
                Option.of(new BranchCommitsMsg.Opened(new BranchRef.Local(branch))),
                keymap.handle(Scopes.BRANCHES, KeyEvent.ofKey(KeyCode.KP_ENTER), Option.of(branch)));
        assertEquals(
                Option.none(),
                keymap.handle(Scopes.BRANCHES, KeyEvent.ofKey(KeyCode.KP_ENTER, KeyModifiers.CTRL), Option.of(branch)));
    }

    @Test
    void aKeyActsOnTheSelection() {
        assertEquals(
                Option.of(new CheckoutMsg.Requested(new BranchName("x"))),
                keymap.handle(Scopes.BRANCHES, KeyEvent.ofChar(' '), Option.of(new BranchName("x"))));
    }

    @Test
    void spaceChecksOutTheCommitInTheCommitsPaneAndInTheCommitsOfABranch() {
        Sha sha = new Sha("bbbb");
        Commit commit = Fixtures.commit(sha, "a", "a@a", java.time.Instant.EPOCH, "m", "");

        for (Keymap.Scope<Commit> scope : List.of(Scopes.COMMITS, Scopes.BRANCH_COMMITS)) {
            assertEquals(
                    Option.of(new CheckoutMsg.CommitRequested(sha)),
                    keymap.handle(scope, KeyEvent.ofChar(' '), Option.of(commit)),
                    scope.id());
        }
    }

    @Test
    void shiftOOpensTheBranchOnGitHub() {
        assertEquals(
                Option.of(new BrowseMsg.Requested(new BranchName("x"))),
                keymap.handle(Scopes.BRANCHES, KeyEvent.ofChar('O'), Option.of(new BranchName("x"))));
        RemoteBranch remote = new RemoteBranch("origin", new BranchName("x"));
        assertEquals(
                Option.of(new BrowseMsg.RemoteRequested(remote)),
                keymap.handle(Scopes.REMOTE_BRANCHES, KeyEvent.ofChar('O'), Option.of(remote)));
    }

    @Test
    void dDeletesARemoteBranch() {
        RemoteBranch remote = new RemoteBranch("origin", new BranchName("x"));
        assertEquals(
                Option.of(new DeleteBranchMsg.RemoteRequested(remote)),
                keymap.handle(Scopes.REMOTE_BRANCHES, KeyEvent.ofChar('d'), Option.of(remote)));
    }

    @Test
    void aKeyLetGoDoesNothing() {
        // Terminals in the full Kitty keyboard protocol tell when a key is let go, as well as when it is pressed.
        KeyEvent released = KeyEvent.ofChar(' ', KeyModifiers.NONE, KeyEventKind.RELEASE);

        assertEquals(Option.none(), keymap.handle(Scopes.BRANCHES, released, Option.of(new BranchName("x"))));
        assertEquals(
                Option.none(),
                keymap.handle(
                        Scopes.BRANCHES,
                        KeyEvent.ofChar('?', KeyModifiers.NONE, KeyEventKind.RELEASE),
                        Option.of(new BranchName("x"))));
    }

    @Test
    void aKeyThatNeedsASelectionDoesNothingWithoutOne() {
        assertEquals(Option.none(), keymap.handle(Scopes.BRANCHES, KeyEvent.ofChar(' '), Option.none()));
    }

    @Test
    void aKeyWithCtrlOrAltIsNotTheKeyWithout() {
        Option<BranchName> selection = Option.of(new BranchName("x"));

        assertTrue(
                keymap.handle(Scopes.BRANCHES, KeyEvent.ofChar('r'), selection).isDefined());
        assertEquals(
                Option.none(),
                keymap.handle(
                        Scopes.BRANCHES, KeyEvent.ofChar('r', dev.tamboui.tui.event.KeyModifiers.CTRL), selection));
        assertEquals(
                Option.none(),
                keymap.handle(
                        Scopes.BRANCHES, KeyEvent.ofChar('r', dev.tamboui.tui.event.KeyModifiers.ALT), selection));
    }

    @Test
    void aPanelCanBindAKeyWithCtrl() {
        Keymap ctrlX = Keymap.empty()
                .add(
                        Scopes.BRANCHES,
                        Keymap.Binding.onSelected("ctrl+x", "app.help.global.quit", CheckoutMsg.Requested::new));
        Option<BranchName> selection = Option.of(new BranchName("x"));

        assertEquals(
                Option.of(new CheckoutMsg.Requested(new BranchName("x"))),
                ctrlX.handle(
                        Scopes.BRANCHES, KeyEvent.ofChar('x', dev.tamboui.tui.event.KeyModifiers.CTRL), selection));
        assertEquals(Option.none(), ctrlX.handle(Scopes.BRANCHES, KeyEvent.ofChar('x'), selection));
    }

    @Test
    void unknownKeysDoNothing() {
        assertEquals(
                Option.none(), keymap.handle(Scopes.BRANCHES, KeyEvent.ofChar('Z'), Option.of(new BranchName("x"))));
    }

    @Test
    void questionMarkAsksForTheHelpOfThePanel() {
        Option<Msg> msg = keymap.handle(Scopes.STASH, KeyEvent.ofChar('?'), Option.none());

        // Pop, drop and the changed files, none of which works without a selected stash.
        assertEquals(
                Option.of(
                        new HelpMsg.Requested(Scopes.STASH.id(), List.of(Option.none(), Option.none(), Option.none()))),
                msg);
    }

    /**
     * The order of {@code Features.ALL} is the order of the keys in a panel (ADR 127); these are the scopes that more
     * than one of the features at its end add to, which moving a line there reorders.
     */
    @Test
    void thePanelsListTheKeysOfTheFeaturesInTheOrderOfTheFeatures() {
        assertEquals(
                List.of(
                        "changes.help.commitFiles.filter",
                        "changes.help.commitFiles.close",
                        "changes.help.commitFiles.allFiles",
                        "changes.help.commitFiles.listed",
                        "changes.help.commitFiles.refresh",
                        "changes.help.commitFiles.version",
                        "changes.help.commitFiles.render",
                        "changes.help.commitFiles.blame"),
                descriptions(Scopes.COMMIT_FILES));
        assertEquals(
                List.of(
                        "changes.help.diff.edit",
                        "changes.help.diff.nextFile",
                        "changes.help.diff.previousFile",
                        "changes.help.diff.expandDown",
                        "changes.help.diff.expandUp",
                        "changes.help.diff.expandAll",
                        "changes.help.diff.foldAll",
                        "changes.help.diff.version",
                        "changes.help.diff.render",
                        "changes.help.diff.blame",
                        "changes.help.diff.search",
                        "changes.help.diff.nextMatch",
                        "changes.help.diff.previousMatch",
                        "changes.help.diff.goToLine",
                        "changes.help.diff.copy",
                        "changes.help.diff.range"),
                descriptions(Scopes.DIFF));
        assertEquals(
                List.of(
                        "changes.help.diff.nextFile",
                        "changes.help.diff.previousFile",
                        "changes.help.diff.expandDown",
                        "changes.help.diff.expandUp",
                        "changes.help.diff.expandAll",
                        "changes.help.diff.foldAll",
                        "changes.help.diff.version",
                        "changes.help.diff.render",
                        "changes.help.diff.blame",
                        "changes.help.diff.search",
                        "changes.help.diff.goToLine",
                        "changes.help.diff.copy"),
                descriptions(Scopes.PULL_REQUEST_DIFF).filter(key -> key.startsWith("changes.help.diff.")));
        assertEquals(
                List.of("changes.help.files.filter", "changes.help.files.allFiles", "changes.help.files.fetch"),
                descriptions(Scopes.FILES)
                        .filter(
                                List.of(
                                        "changes.help.files.filter",
                                        "changes.help.files.allFiles",
                                        "changes.help.files.fetch")::contains));
    }

    private List<String> descriptions(Keymap.Scope<?> scope) {
        return keymap.bindings(scope.id()).map(Keymap.Binding::descriptionKey);
    }

    @Test
    void aPanelDeclaresEachKeyOnce() {
        for (Keymap.Scope<?> scope : List.of(Scopes.FILES, Scopes.BRANCHES, Scopes.COMMITS, Scopes.STASH)) {
            List<String> keys = keymap.bindings(scope.id()).map(Keymap.Binding::key);
            assertEquals(keys.size(), keys.distinct().size(), scope.id() + " " + keys);
            assertTrue(keymap.titleKey(scope.id()).isDefined());
        }
    }

    @Test
    void iHandsTheFailedBuildTheBuildsAndTheChecksShowToAnAgent() {
        FailedBuild failed = new FailedBuild(
                Option.of("o/r"),
                new BranchName("feat"),
                Option.none(),
                Option.none(),
                List.of(new FailedBuild.Job("verify", Option.of("401"), Option.none())),
                false,
                "gh run view 401 --log-failed --repo o/r");

        for (Keymap.Scope<?> host : List.of(BuildsAction.SCOPE, ChecksAction.SCOPE)) {
            assertTrue(
                    keymap.bindings(host.id()).exists(binding -> binding.key().equals("I")), host.id());
        }
        assertEquals(
                Option.of(new InvestigateMsg.Requested(failed)),
                keymap.handle(
                        ChecksAction.SCOPE,
                        KeyEvent.ofChar('I'),
                        Option.none(),
                        new Keymap.Hosted<>(Scopes.FAILED_BUILD, Option.of(failed))));
        // Without a build handed over, it still says why it does nothing.
        assertEquals(
                Option.of(new InvestigateMsg.Unavailable()),
                keymap.handle(
                        BuildsAction.SCOPE,
                        KeyEvent.ofChar('I'),
                        Option.none(),
                        new Keymap.Hosted<>(Scopes.FAILED_BUILD, Option.none())));
    }
}
