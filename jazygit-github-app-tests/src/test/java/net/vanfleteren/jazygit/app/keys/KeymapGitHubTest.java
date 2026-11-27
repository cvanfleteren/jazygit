package net.vanfleteren.jazygit.app.keys;

import static net.vanfleteren.jazygit.app.GitHubKeys.github;
import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.features.agent.investigate.InvestigateMsg;
import net.vanfleteren.jazygit.features.github.browse.BrowseMsg;
import net.vanfleteren.jazygit.features.github.builds.screen.BuildsScopes;
import net.vanfleteren.jazygit.features.github.checks.ChecksDialog;
import net.vanfleteren.jazygit.features.github.prs.PrOverview;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import net.vanfleteren.jazygit.kernel.state.preferences.ScreenMsg;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import org.junit.jupiter.api.Test;

/**
 * The tests of KeymapTest with GitHub in the app (ADR 171).
 */
class KeymapGitHubTest {

    private final Keymap keymap = Keymap.standard();

    @Test
    void ctrlPSwitchesBetweenTheRepositoryAndThePullRequests() {
        assertEquals(
                Option.of(github(new ScreenMsg.Toggled(PrOverview.SCREEN))),
                keymap.handleGlobal(KeyEvent.ofChar('p', dev.tamboui.tui.event.KeyModifiers.CTRL)));
        // Plain p is left to the panels.
        assertEquals(Option.none(), keymap.handleGlobal(KeyEvent.ofChar('p')));
        assertTrue(keymap.globals().exists(global -> global.label().equals("ctrl+p")));
    }

    @Test
    void shiftOOpensTheBranchOnGitHub() {
        assertEquals(
                Option.of(github(new BrowseMsg.Requested(new BranchName("x")))),
                keymap.handle(Scopes.BRANCHES, KeyEvent.ofChar('O'), Option.of(new BranchName("x"))));
        RemoteBranch remote = new RemoteBranch("origin", new BranchName("x"));
        assertEquals(
                Option.of(github(new BrowseMsg.RemoteRequested(remote))),
                keymap.handle(Scopes.REMOTE_BRANCHES, KeyEvent.ofChar('O'), Option.of(remote)));
    }

    /**
     * The order of {@code Features.ALL} is the order of the keys in a panel (ADR 127); these are the scopes that more
     * than one of the features at its end add to, which moving a line there reorders.
     */
    @Test
    void thePanelsListTheKeysOfTheFeaturesInTheOrderOfTheFeatures() {
        assertEquals(
                List.of(
                        "changes.help.commitFiles.edit",
                        "github.help.commitFiles.refresh",
                        "changes.help.commitFiles.filter",
                        "changes.help.commitFiles.close",
                        "changes.help.commitFiles.allFiles",
                        "changes.help.commitFiles.listed",
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
                descriptions(Scopes.REMARKED_DIFF).filter(key -> key.startsWith("changes.help.diff.")));
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
    void iHandsTheFailedRunOfTheBuildsScreenAndTheChecksToAnAgent() {
        FailedBuild failed = new FailedBuild(
                Option.of(new RepoSlug("o/r")),
                new BranchName("feat"),
                Option.none(),
                Option.none(),
                List.of(new FailedBuild.Job("verify", Option.none())),
                false,
                "gh run view 401 --log-failed --repo o/r");

        for (Keymap.Scope<?> host : List.of(BuildsScopes.RUNS, BuildsScopes.CONTENT, ChecksDialog.SCOPE)) {
            assertTrue(
                    keymap.bindings(host.id()).exists(binding -> binding.key().equals("I")), host.id());
        }
        assertEquals(
                Option.of(new InvestigateMsg.Requested(failed)),
                keymap.handle(
                        ChecksDialog.SCOPE,
                        KeyEvent.ofChar('I'),
                        Option.none(),
                        new Keymap.Hosted<>(Scopes.FAILED_BUILD, Option.of(failed))));
        // Without a build handed over, it still says why it does nothing.
        assertEquals(
                Option.of(new InvestigateMsg.Unavailable()),
                keymap.handle(
                        BuildsScopes.RUNS,
                        KeyEvent.ofChar('I'),
                        Option.none(),
                        new Keymap.Hosted<>(Scopes.FAILED_BUILD, Option.none())));
    }
}
