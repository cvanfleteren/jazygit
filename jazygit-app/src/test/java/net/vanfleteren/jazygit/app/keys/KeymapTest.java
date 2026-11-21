package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyEventKind;
import dev.tamboui.tui.event.KeyModifiers;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.branches.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.features.branches.branch.commits.BranchCommitsMsg;
import net.vanfleteren.jazygit.features.branches.branch.delete.DeleteBranchMsg;
import net.vanfleteren.jazygit.features.branches.repos.RecentReposMsg;
import net.vanfleteren.jazygit.features.changes.cherrypick.CherryPickMsg;
import net.vanfleteren.jazygit.features.changes.diff.DiffOptionsMsg;
import net.vanfleteren.jazygit.features.changes.inprogress.InProgressMsg;
import net.vanfleteren.jazygit.features.chrome.help.HelpMsg;
import net.vanfleteren.jazygit.features.chrome.layout.LayoutMsg;
import net.vanfleteren.jazygit.features.chrome.settings.SettingsPanelMsg;
import net.vanfleteren.jazygit.kernel.state.Msg;
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
    void escLetsGoOfTheCopiedCommitWhenNothingElseTakesIt() {
        assertEquals(Option.of(new CherryPickMsg.Dropped()), keymap.handleGlobal(KeyEvent.ofKey(KeyCode.ESCAPE)));
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
}
