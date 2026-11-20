package net.vanfleteren.jazygit.features.github.settings;

import static net.vanfleteren.jazygit.kernel.state.TestModels.typed;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.ArrayList;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.gh.account.StubAccount;
import net.vanfleteren.jazygit.core.gh.model.ChosenBrowser;
import net.vanfleteren.jazygit.core.gh.model.GhAccount;
import net.vanfleteren.jazygit.core.gh.model.GhLogin;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.branches.StubConfig;
import net.vanfleteren.jazygit.core.git.branches.StubRemotes;
import net.vanfleteren.jazygit.core.git.model.RemoteName;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.settings.AccountBrowsers;
import net.vanfleteren.jazygit.kernel.settings.RecentRepositories;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.settings.SettingsStore;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Landing;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.github.GitHubAccount;
import net.vanfleteren.jazygit.kernel.state.popup.ErrorPopup;
import net.vanfleteren.jazygit.kernel.state.preferences.SaveSettings;
import net.vanfleteren.jazygit.kernel.state.preferences.ShowAgain;
import net.vanfleteren.jazygit.kernel.state.settings.SettingsPanelPopup;
import net.vanfleteren.jazygit.kernel.state.view.BranchesTab;
import org.junit.jupiter.api.Test;

class GitHubSettingsUpdateTest {

    private static final GitHubSetup SETUP = new GitHubSetup(
            Either.right("2.102.0"),
            "github.com",
            Option.none(),
            Either.right(List.of(
                    new GhLogin("github.com", "alice", true, true),
                    new GhLogin("github.com", "alice-work", false, true))),
            Option.of(new GhAccount("github.com", "alice")),
            Option.of(new ChosenBrowser("xdg-open", ChosenBrowser.Source.DESKTOP)));
    private static final SettingsPanelPopup OPEN = new SettingsPanelPopup(TestModels.PLAIN.settings());
    private static final SettingsPanelPopup READ = OPEN.with(GitHubSettingsUpdate.SETUP, Option.of(SETUP));

    @Test
    void theSectionReadsHowGhIsSetUpAsThePanelOpens() {
        assertEquals(
                List.of(new GitHubSettingsCmd.ReadGitHub("gh")),
                GitHubSettingsDialogs.SECTION.onOpen().on(TestModels.loaded()));
    }

    @Test
    void whatIsReadShowsInTheOpenPanel() {
        Model open = TestModels.loaded().openPopup(OPEN);

        Model read =
                Update.update(open, new GitHubSettingsMsg.GitHubRead(SETUP)).model();

        assertEquals(Option.of(READ), read.popup(SettingsPanelPopup.class));
    }

    @Test
    void whatIsReadAfterThePanelClosedIsDropped() {
        Model closed = TestModels.loaded();

        assertEquals(
                closed,
                Update.update(closed, new GitHubSettingsMsg.GitHubRead(SETUP)).model());
    }

    @Test
    void anEnteredGhExecutableIsCheckedFirst() {
        Model entering = Update.update(
                        TestModels.loaded().openPopup(READ), new GitHubSettingsMsg.GhExecutableRequested())
                .model();
        assertEquals(Option.of(new GhExecutablePopup("")), entering.popup(GhExecutablePopup.class));

        Next next = Update.update(typed(entering, "  /opt/gh/bin/gh "), new GitHubSettingsMsg.GhExecutableEntered());

        assertEquals(Option.none(), next.model().popup());
        assertEquals(List.of(new GitHubSettingsCmd.CheckGhExecutable(Option.of("/opt/gh/bin/gh"))), next.cmds());
    }

    @Test
    void onlyTheCheckOfTheGhExecutableEnteredLastDecidesInWhateverOrderTheChecksFinish() {
        Model first = Update.update(
                        typed(TestModels.loaded().openPopup(new GhExecutablePopup("")), "/old/gh"),
                        new GitHubSettingsMsg.GhExecutableEntered())
                .model();
        Model second = Update.update(
                        typed(first.openPopup(new GhExecutablePopup("")), "/new/gh"),
                        new GitHubSettingsMsg.GhExecutableEntered())
                .model();
        Settings chosen = TestModels.PLAIN.settings().with(GitHubSettings.GH_EXECUTABLE, Option.of("/new/gh"));

        Next latestFirst = Update.update(
                second, new GitHubSettingsMsg.GhExecutableChecked(Option.of("/new/gh"), Either.right("2.102.0")));
        Next olderAfter = Update.update(
                latestFirst.model(),
                new GitHubSettingsMsg.GhExecutableChecked(Option.of("/old/gh"), Either.right("2.102.0")));
        Next olderFirst = Update.update(
                second, new GitHubSettingsMsg.GhExecutableChecked(Option.of("/old/gh"), Either.right("2.102.0")));

        assertEquals(chosen, latestFirst.model().preferences().settings());
        assertEquals(chosen, olderAfter.model().preferences().settings());
        assertEquals(List.empty(), olderAfter.cmds());
        assertEquals(second, olderFirst.model());
        assertEquals(List.empty(), olderFirst.cmds());
    }

    @Test
    void aCheckOfAnotherGhExecutableIsDroppedOnceTheOneOnThePathWasEnteredLast() {
        Settings saved = TestModels.PLAIN.settings().with(GitHubSettings.GH_EXECUTABLE, Option.of("/opt/gh/bin/gh"));
        Model first = Update.update(
                        typed(
                                TestModels.loaded()
                                        .mapPreferences(preferences -> preferences.withSettings(saved))
                                        .openPopup(new GhExecutablePopup("")),
                                "/old/gh"),
                        new GitHubSettingsMsg.GhExecutableEntered())
                .model();
        Model onThePath = Update.update(
                        typed(first.openPopup(new GhExecutablePopup("/old/gh")), " "),
                        new GitHubSettingsMsg.GhExecutableEntered())
                .model();

        Next older = Update.update(
                onThePath, new GitHubSettingsMsg.GhExecutableChecked(Option.of("/old/gh"), Either.right("2.102.0")));
        Next latest = Update.update(
                onThePath, new GitHubSettingsMsg.GhExecutableChecked(Option.none(), Either.right("2.102.0")));

        assertEquals(onThePath, older.model());
        assertEquals(List.empty(), older.cmds());
        assertEquals(Option.none(), latest.model().preferences().settings().get(GitHubSettings.GH_EXECUTABLE));
        assertEquals(
                List.of(new SaveSettings(saved, TestModels.PLAIN.settings()), new ShowAgain(Landing.START)),
                latest.cmds());
    }

    @Test
    void anEmptyGhExecutableIsTheOneOnThePath() {
        Model entering = TestModels.loaded().openPopup(new GhExecutablePopup("/opt/gh/bin/gh"));

        Next next = Update.update(typed(entering, " "), new GitHubSettingsMsg.GhExecutableEntered());

        assertEquals(List.of(new GitHubSettingsCmd.CheckGhExecutable(Option.none())), next.cmds());
    }

    @Test
    void aGhExecutableThatRunsIsKeptAndTheRepositoryShownAgain() {
        Settings chosen = TestModels.PLAIN.settings().with(GitHubSettings.GH_EXECUTABLE, Option.of("/opt/gh/bin/gh"));

        Next next = Update.update(
                TestModels.loaded()
                        .mapPreferences(
                                preferences -> preferences.withGhExecutableEntered(Option.of("/opt/gh/bin/gh"))),
                new GitHubSettingsMsg.GhExecutableChecked(Option.of("/opt/gh/bin/gh"), Either.right("2.102.0")));

        assertEquals(chosen, next.model().preferences().settings());
        assertEquals(
                List.of(new SaveSettings(TestModels.PLAIN.settings(), chosen), new ShowAgain(Landing.START)),
                next.cmds());
    }

    @Test
    void theRepositoryIsShownAgainWhereItWas() {
        Model there = TestModels.loaded()
                .mapRepoView(view -> view.mapBranches(branches -> branches.withTab(BranchesTab.WORKTREES))
                        .withFocusedPane(Option.of(new PaneId("commits"))))
                .mapPreferences(preferences -> preferences.withGhExecutableEntered(Option.of("/opt/gh/bin/gh")));

        Next next = Update.update(
                there, new GitHubSettingsMsg.GhExecutableChecked(Option.of("/opt/gh/bin/gh"), Either.right("2.102.0")));

        assertEquals(
                Option.of(new ShowAgain(new Landing(BranchesTab.WORKTREES, Option.of(new PaneId("commits"))))),
                next.cmds().find(ShowAgain.class::isInstance));
    }

    @Test
    void aGhExecutableThatDoesNotRunIsNotUsed() {
        Failure failure = new Failure.GitHub(new GhError.GhUnavailable(new java.io.IOException("no such file")));

        Next next = Update.update(
                TestModels.loaded()
                        .mapPreferences(preferences -> preferences.withGhExecutableEntered(Option.of("/nowhere/gh"))),
                new GitHubSettingsMsg.GhExecutableChecked(Option.of("/nowhere/gh"), Either.left(failure)));

        assertEquals(TestModels.PLAIN.settings(), next.model().preferences().settings());
        assertEquals(List.empty(), next.cmds());
        assertEquals(
                Option.of(new ErrorPopup(
                        new GitHubSettingsError.GhExecutableUnusable(Option.of("/nowhere/gh"), failure))),
                next.model().popup(ErrorPopup.class));
    }

    @Test
    void theAccountIsChosenFromTheOnesRead() {
        Model choosing = Update.update(TestModels.loaded().openPopup(READ), new GitHubSettingsMsg.AccountRequested())
                .model();
        assertEquals(Option.of(new GitHubAccountPopup(SETUP)), choosing.popup(GitHubAccountPopup.class));

        Next next = Update.update(choosing, new GitHubSettingsMsg.AccountChosen(Option.of("alice-work")));

        assertEquals(Option.none(), next.model().popup());
        assertEquals(List.of(new GitHubSettingsCmd.SetAccount(Option.of("alice-work"), Landing.START)), next.cmds());
    }

    @Test
    void theAccountCannotBeChosenWhileTheAccountsAreRead() {
        Model reading = TestModels.loaded().openPopup(OPEN);

        assertEquals(
                reading,
                Update.update(reading, new GitHubSettingsMsg.AccountRequested()).model());
    }

    @Test
    void theBrowserOfTheAccountIsAskedWithTheOneSetNow() {
        Model open = TestModels.loaded()
                .openPopup(READ.withSettings(READ.settings()
                        .with(
                                GitHubSettings.BROWSERS,
                                AccountBrowsers.NONE.with("github.com", "alice", Option.of("firefox -P personal")))));

        Model asking =
                Update.update(open, new GitHubSettingsMsg.BrowserRequested()).model();

        assertEquals(
                Option.of(new AccountBrowserPopup(new GhAccount("github.com", "alice"), "firefox -P personal")),
                asking.popup(AccountBrowserPopup.class));
    }

    @Test
    void theBrowserCannotBeSetWhileTheAccountIsNotKnown() {
        Model unknown = TestModels.loaded()
                .openPopup(READ.with(
                        GitHubSettingsUpdate.SETUP,
                        Option.of(new GitHubSetup(
                                SETUP.version(),
                                SETUP.host(),
                                SETUP.user(),
                                SETUP.logins(),
                                Option.none(),
                                Option.none()))));
        Model reading = TestModels.loaded().openPopup(OPEN);

        assertEquals(
                unknown,
                Update.update(unknown, new GitHubSettingsMsg.BrowserRequested()).model());
        assertEquals(
                reading,
                Update.update(reading, new GitHubSettingsMsg.BrowserRequested()).model());
    }

    @Test
    void anEnteredBrowserIsKeptForTheAccount() {
        GhAccount alice = new GhAccount("github.com", "alice");
        Settings kept = TestModels.PLAIN.settings();
        Settings chosen = kept.with(
                GitHubSettings.BROWSERS, AccountBrowsers.NONE.with("github.com", "alice", Option.of("firefox")));

        Next next = Update.update(
                typed(TestModels.loaded().openPopup(new AccountBrowserPopup(alice, "")), " firefox "),
                new GitHubSettingsMsg.BrowserEntered());

        assertEquals(Option.none(), next.model().popup());
        assertEquals(chosen, next.model().preferences().settings());
        assertEquals(List.of(new SaveSettings(kept, chosen)), next.cmds());
    }

    @Test
    void anEmptyBrowserTakesTheAccountsAway() {
        GhAccount alice = new GhAccount("github.com", "alice");
        Model set = TestModels.loaded()
                .mapPreferences(preferences -> preferences.withSettings(preferences
                        .settings()
                        .with(
                                GitHubSettings.BROWSERS,
                                AccountBrowsers.NONE.with("github.com", "alice", Option.of("firefox")))));
        Settings kept = set.preferences().settings();

        Next next = Update.update(
                set.openPopup(new AccountBrowserPopup(alice, "  ")), new GitHubSettingsMsg.BrowserEntered());

        assertEquals(AccountBrowsers.NONE, next.model().preferences().settings().get(GitHubSettings.BROWSERS));
        assertEquals(
                List.of(new SaveSettings(kept, kept.with(GitHubSettings.BROWSERS, AccountBrowsers.NONE))), next.cmds());
    }

    @Test
    void choosingTheAccountInUseChangesNothing() {
        Model choosing = TestModels.loaded().openPopup(new GitHubAccountPopup(SETUP));

        Next next = Update.update(choosing, new GitHubSettingsMsg.AccountChosen(Option.none()));

        assertEquals(Option.none(), next.model().popup());
        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void readingHowGhIsSetUp() {
        Env env = new Env(
                StubGit.of()
                        .withConfig(new StubConfig() {
                            @Override
                            public Either<GitError, Option<String>> local(String key) {
                                return Either.right(Option.when(key.equals(GitHubAccount.USER_KEY), "alice-work"));
                            }
                        })
                        .withRemotes(new StubRemotes() {
                            @Override
                            public Either<GitError, Map<RemoteName, String>> urls() {
                                return Either.right(
                                        HashMap.of(new RemoteName("origin"), "git@github.com:team/repo.git"));
                            }
                        }),
                StubGitHub.of().withAccount(new StubAccount() {
                    @Override
                    public Either<GhError, List<GhLogin>> logins() {
                        return Either.right(SETUP.logins().get());
                    }
                }));

        Msg read = new GitHubSettingsCmd.ReadGitHub("gh").run(env);

        assertEquals(
                new GitHubSettingsMsg.GitHubRead(new GitHubSetup(
                        Either.right("2.102.0"),
                        "github.com",
                        Option.of("alice-work"),
                        SETUP.logins(),
                        Option.none(),
                        Option.none())),
                read);
    }

    @Test
    void settingTheAccountConfiguresItAndShowsTheRepositoryAgain() {
        java.util.List<String> done = new ArrayList<>();
        Env env = new Env(
                StubGit.of(Path.of("/repos/a")).withConfig(new StubConfig() {
                    @Override
                    public Either<GitFailure, List<ExecutedCommand>> setLocal(String key, Option<String> value) {
                        done.add(key + "=" + value.getOrElse("<unset>"));
                        return Either.right(List.empty());
                    }
                }),
                StubGitHub.of(),
                SettingsStore.NONE,
                RecentRepositories.NONE,
                (directory, landing) -> {
                    done.add("reopened " + directory + " on " + landing.branchesTab());
                    return Either.right(directory);
                });

        Msg set = new GitHubSettingsCmd.SetAccount(
                        Option.of("alice-work"), new Landing(BranchesTab.REMOTE, Option.none()))
                .run(env);

        assertEquals(new GitHubSettingsMsg.AccountSet(), set);
        assertEquals(
                List.of("jazygit.githubUser=alice-work", "reopened " + Path.of("/repos/a") + " on REMOTE"),
                List.ofAll(done));
    }
}
