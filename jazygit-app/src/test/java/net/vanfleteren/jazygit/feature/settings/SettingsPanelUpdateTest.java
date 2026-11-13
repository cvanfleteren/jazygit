package net.vanfleteren.jazygit.feature.settings;

import static net.vanfleteren.jazygit.state.TestModels.typed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.feature.commit.create.CommitPopup;
import net.vanfleteren.jazygit.feature.diff.DiffOptionsUpdate;
import net.vanfleteren.jazygit.feature.theme.ThemeMsg;
import net.vanfleteren.jazygit.feature.theme.ThemePopup;
import net.vanfleteren.jazygit.feature.theme.ThemeScope;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.account.StubAccount;
import net.vanfleteren.jazygit.gh.model.ChosenBrowser;
import net.vanfleteren.jazygit.gh.model.GhAccount;
import net.vanfleteren.jazygit.gh.model.GhLogin;
import net.vanfleteren.jazygit.gh.process.GhError;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.branches.StubConfig;
import net.vanfleteren.jazygit.git.branches.StubRemotes;
import net.vanfleteren.jazygit.git.files.StubEditor;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.git.process.GitFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.settings.AccountBrowsers;
import net.vanfleteren.jazygit.settings.RecentRepositories;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.settings.SettingsProblem;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.github.*;
import net.vanfleteren.jazygit.state.operation.CommandLog;
import net.vanfleteren.jazygit.state.popup.*;
import net.vanfleteren.jazygit.state.preferences.RepositoryTheme;
import net.vanfleteren.jazygit.state.preferences.SaveSettings;
import net.vanfleteren.jazygit.state.preferences.SettingsError;
import net.vanfleteren.jazygit.state.preferences.SettingsMsg;
import net.vanfleteren.jazygit.state.view.*;
import org.junit.jupiter.api.Test;

class SettingsPanelUpdateTest {

    private static final GitHubSetup SETUP = new GitHubSetup(
            Either.right("2.102.0"),
            "github.com",
            Option.none(),
            Either.right(List.of(
                    new GhLogin("github.com", "alice", true, true),
                    new GhLogin("github.com", "alice-work", false, true))),
            Option.of(new GhAccount("github.com", "alice")),
            Option.of(new ChosenBrowser("xdg-open", ChosenBrowser.Source.DESKTOP)));
    private static final SettingsPanelPopup READ = new SettingsPanelPopup(
            ThemeName.DARK,
            RepositoryTheme.NONE,
            Option.none(),
            Option.of(SETUP),
            true,
            AccountBrowsers.NONE,
            Option.none());

    @Test
    void requestingShowsTheSettingsAndReadsHowGhIsSetUp() {
        Next next = Update.update(TestModels.loaded(), new SettingsPanelMsg.Requested());

        assertEquals(
                Option.of(new SettingsPanelPopup(
                        ThemeName.DARK,
                        RepositoryTheme.NONE,
                        Option.none(),
                        Option.none(),
                        true,
                        AccountBrowsers.NONE,
                        Option.none())),
                next.model().popup(SettingsPanelPopup.class));
        assertEquals(List.of(new SettingsPanelCmd.ReadGitHub("gh"), new SettingsPanelCmd.ReadFile()), next.cmds());
    }

    @Test
    void requestingWhileADialogIsOpenLeavesThatDialogOpen() {
        Model typing = TestModels.loaded().openPopup(new CommitPopup());

        Next next = Update.update(typing, new SettingsPanelMsg.Requested());

        assertEquals(typing, next.model());
        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void whatIsReadShowsInTheOpenPanel() {
        Model open = Update.update(TestModels.loaded(), new SettingsPanelMsg.Requested())
                .model();

        Model read = Update.update(open, new SettingsPanelMsg.GitHubRead(SETUP)).model();

        assertEquals(Option.of(READ), read.popup(SettingsPanelPopup.class));
    }

    @Test
    void whatIsReadAfterThePanelClosedIsDropped() {
        Model closed = TestModels.loaded();

        assertEquals(
                closed,
                Update.update(closed, new SettingsPanelMsg.GitHubRead(SETUP)).model());
    }

    @Test
    void theThemeIsChosenInItsOwnDialog() {
        Model open = TestModels.loaded().openPopup(READ);

        Model choosing =
                Update.update(open, new SettingsPanelMsg.ThemeRequested()).model();

        assertEquals(
                Option.of(new ThemePopup(
                        ThemeName.DARK,
                        Option.none(),
                        ThemeScope.EVERYWHERE,
                        RepositoryTheme.NONE,
                        ThemeName.DARK,
                        Option.of(READ.withOpensAt(Option.of('t'))))),
                choosing.popup(ThemePopup.class));
    }

    @Test
    void cancellingTheThemeGoesBackToTheSettingsOnTheTheme() {
        Model open = TestModels.loaded().openPopup(READ);
        Model choosing =
                Update.update(open, new SettingsPanelMsg.ThemeRequested()).model();
        Model previewed =
                Update.update(choosing, new ThemeMsg.Previewed(ThemeName.LIGHT)).model();

        Model cancelled = Update.update(previewed, new ThemeMsg.Cancelled()).model();

        assertEquals(Option.of(READ.withOpensAt(Option.of('t'))), cancelled.popup(SettingsPanelPopup.class));
        assertEquals(ThemeName.DARK, cancelled.preferences().theme());
    }

    @Test
    void cancellingTheRepositoryThemeGoesBackToTheSettingsOnTheRepositoryTheme() {
        Model open = TestModels.loaded().openPopup(READ);
        Model choosing = Update.update(open, new SettingsPanelMsg.RepositoryThemeRequested())
                .model();

        Model cancelled = Update.update(choosing, new ThemeMsg.Cancelled()).model();

        assertEquals(Option.of(READ.withOpensAt(Option.of('r'))), cancelled.popup(SettingsPanelPopup.class));
    }

    @Test
    void anEnteredGhExecutableIsCheckedFirst() {
        Model entering = Update.update(
                        TestModels.loaded().openPopup(READ), new SettingsPanelMsg.GhExecutableRequested())
                .model();
        assertEquals(Option.of(new GhExecutablePopup("")), entering.popup(GhExecutablePopup.class));

        Next next = Update.update(typed(entering, "  /opt/gh/bin/gh "), new SettingsPanelMsg.GhExecutableEntered());

        assertEquals(Option.none(), next.model().popup());
        assertEquals(List.of(new SettingsPanelCmd.CheckGhExecutable(Option.of("/opt/gh/bin/gh"))), next.cmds());
    }

    @Test
    void onlyTheCheckOfTheGhExecutableEnteredLastDecidesInWhateverOrderTheChecksFinish() {
        Model first = Update.update(
                        typed(TestModels.loaded().openPopup(new GhExecutablePopup("")), "/old/gh"),
                        new SettingsPanelMsg.GhExecutableEntered())
                .model();
        Model second = Update.update(
                        typed(first.openPopup(new GhExecutablePopup("")), "/new/gh"),
                        new SettingsPanelMsg.GhExecutableEntered())
                .model();
        Settings chosen = TestModels.PLAIN.settings().withGhExecutable(Option.of("/new/gh"));

        Next latestFirst = Update.update(
                second, new SettingsPanelMsg.GhExecutableChecked(Option.of("/new/gh"), Either.right("2.102.0")));
        Next olderAfter = Update.update(
                latestFirst.model(),
                new SettingsPanelMsg.GhExecutableChecked(Option.of("/old/gh"), Either.right("2.102.0")));
        Next olderFirst = Update.update(
                second, new SettingsPanelMsg.GhExecutableChecked(Option.of("/old/gh"), Either.right("2.102.0")));

        assertEquals(chosen, latestFirst.model().preferences().settings());
        assertEquals(chosen, olderAfter.model().preferences().settings());
        assertEquals(List.empty(), olderAfter.cmds());
        assertEquals(second, olderFirst.model());
        assertEquals(List.empty(), olderFirst.cmds());
    }

    @Test
    void aCheckOfAnotherGhExecutableIsDroppedOnceTheOneOnThePathWasEnteredLast() {
        Settings saved = TestModels.PLAIN.settings().withGhExecutable(Option.of("/opt/gh/bin/gh"));
        Model first = Update.update(
                        typed(
                                TestModels.loaded()
                                        .mapPreferences(preferences -> preferences.withSettings(saved))
                                        .openPopup(new GhExecutablePopup("")),
                                "/old/gh"),
                        new SettingsPanelMsg.GhExecutableEntered())
                .model();
        Model onThePath = Update.update(
                        typed(first.openPopup(new GhExecutablePopup("/old/gh")), " "),
                        new SettingsPanelMsg.GhExecutableEntered())
                .model();

        Next older = Update.update(
                onThePath, new SettingsPanelMsg.GhExecutableChecked(Option.of("/old/gh"), Either.right("2.102.0")));
        Next latest = Update.update(
                onThePath, new SettingsPanelMsg.GhExecutableChecked(Option.none(), Either.right("2.102.0")));

        assertEquals(onThePath, older.model());
        assertEquals(List.empty(), older.cmds());
        assertEquals(Option.none(), latest.model().preferences().settings().ghExecutable());
        assertEquals(
                List.of(
                        new SaveSettings(saved, TestModels.PLAIN.settings()),
                        new SettingsPanelCmd.Reopen(Landing.START)),
                latest.cmds());
    }

    @Test
    void anEmptyGhExecutableIsTheOneOnThePath() {
        Model entering = TestModels.loaded().openPopup(new GhExecutablePopup("/opt/gh/bin/gh"));

        Next next = Update.update(typed(entering, " "), new SettingsPanelMsg.GhExecutableEntered());

        assertEquals(List.of(new SettingsPanelCmd.CheckGhExecutable(Option.none())), next.cmds());
    }

    @Test
    void aGhExecutableThatRunsIsKeptAndTheRepositoryShownAgain() {
        Settings chosen = TestModels.PLAIN.settings().withGhExecutable(Option.of("/opt/gh/bin/gh"));

        Next next = Update.update(
                TestModels.loaded().mapJobs(jobs -> jobs.withGhExecutableEntered(Option.of("/opt/gh/bin/gh"))),
                new SettingsPanelMsg.GhExecutableChecked(Option.of("/opt/gh/bin/gh"), Either.right("2.102.0")));

        assertEquals(chosen, next.model().preferences().settings());
        assertEquals(
                List.of(
                        new SaveSettings(TestModels.PLAIN.settings(), chosen),
                        new SettingsPanelCmd.Reopen(Landing.START)),
                next.cmds());
    }

    @Test
    void theRepositoryIsShownAgainWhereItWas() {
        Model there = TestModels.loaded()
                .mapRepoView(view -> view.withBranchesTab(BranchesTab.WORKTREES).withFocusedPane(Option.of("commits")))
                .mapJobs(jobs -> jobs.withGhExecutableEntered(Option.of("/opt/gh/bin/gh")));

        Next next = Update.update(
                there, new SettingsPanelMsg.GhExecutableChecked(Option.of("/opt/gh/bin/gh"), Either.right("2.102.0")));

        assertEquals(
                Option.of(new SettingsPanelCmd.Reopen(new Landing(BranchesTab.WORKTREES, Option.of("commits")))),
                next.cmds().find(SettingsPanelCmd.Reopen.class::isInstance));
    }

    @Test
    void aGhExecutableThatDoesNotRunIsNotUsed() {
        Failure failure = new Failure.GitHub(new GhError.GhUnavailable(new java.io.IOException("no such file")));

        Next next = Update.update(
                TestModels.loaded().mapJobs(jobs -> jobs.withGhExecutableEntered(Option.of("/nowhere/gh"))),
                new SettingsPanelMsg.GhExecutableChecked(Option.of("/nowhere/gh"), Either.left(failure)));

        assertEquals(TestModels.PLAIN.settings(), next.model().preferences().settings());
        assertEquals(List.empty(), next.cmds());
        assertEquals(
                Option.of(
                        new ErrorPopup(new SettingsPanelError.GhExecutableUnusable(Option.of("/nowhere/gh"), failure))),
                next.model().popup(ErrorPopup.class));
    }

    @Test
    void theAccountIsChosenFromTheOnesRead() {
        Model choosing = Update.update(TestModels.loaded().openPopup(READ), new SettingsPanelMsg.AccountRequested())
                .model();
        assertEquals(Option.of(new GitHubAccountPopup(SETUP)), choosing.popup(GitHubAccountPopup.class));

        Next next = Update.update(choosing, new SettingsPanelMsg.AccountChosen(Option.of("alice-work")));

        assertEquals(Option.none(), next.model().popup());
        assertEquals(List.of(new SettingsPanelCmd.SetAccount(Option.of("alice-work"), Landing.START)), next.cmds());
    }

    @Test
    void theAccountCannotBeChosenWhileTheAccountsAreRead() {
        Model reading = TestModels.loaded()
                .openPopup(new SettingsPanelPopup(
                        ThemeName.DARK,
                        RepositoryTheme.NONE,
                        Option.none(),
                        Option.none(),
                        true,
                        AccountBrowsers.NONE,
                        Option.none()));

        assertEquals(
                reading,
                Update.update(reading, new SettingsPanelMsg.AccountRequested()).model());
    }

    @Test
    void theAnimationsAreTurnedOffAndOnAgainAndKeptWithThePanelStillOpen() {
        Model open = TestModels.loaded().openPopup(READ);
        Settings kept = open.preferences().settings();

        Next off = Update.update(open, new SettingsPanelMsg.AnimationsToggled());

        assertFalse(off.model().preferences().settings().animations());
        assertEquals(Option.of(READ.withAnimations(false)), off.model().popup(SettingsPanelPopup.class));
        assertEquals(List.of(new SaveSettings(kept, kept.withAnimations(false))), off.cmds());

        Next on = Update.update(off.model(), new SettingsPanelMsg.AnimationsToggled());

        assertTrue(on.model().preferences().settings().animations());
        assertEquals(Option.of(READ), on.model().popup(SettingsPanelPopup.class));
    }

    @Test
    void theBrowserOfTheAccountIsAskedWithTheOneSetNow() {
        Model open = TestModels.loaded()
                .openPopup(READ.withBrowsers(
                        AccountBrowsers.NONE.with("github.com", "alice", Option.of("firefox -P personal"))));

        Model asking =
                Update.update(open, new SettingsPanelMsg.BrowserRequested()).model();

        assertEquals(
                Option.of(new AccountBrowserPopup(new GhAccount("github.com", "alice"), "firefox -P personal")),
                asking.popup(AccountBrowserPopup.class));
    }

    @Test
    void theBrowserCannotBeSetWhileTheAccountIsNotKnown() {
        Model unknown = TestModels.loaded()
                .openPopup(READ.withGitHub(Option.of(new GitHubSetup(
                        SETUP.version(), SETUP.host(), SETUP.user(), SETUP.logins(), Option.none(), Option.none()))));
        Model reading = TestModels.loaded().openPopup(READ.withGitHub(Option.none()));

        assertEquals(
                unknown,
                Update.update(unknown, new SettingsPanelMsg.BrowserRequested()).model());
        assertEquals(
                reading,
                Update.update(reading, new SettingsPanelMsg.BrowserRequested()).model());
    }

    @Test
    void anEnteredBrowserIsKeptForTheAccount() {
        GhAccount alice = new GhAccount("github.com", "alice");
        Settings kept = TestModels.PLAIN.settings();
        Settings chosen = kept.withBrowsers(AccountBrowsers.NONE.with("github.com", "alice", Option.of("firefox")));

        Next next = Update.update(
                typed(TestModels.loaded().openPopup(new AccountBrowserPopup(alice, "")), " firefox "),
                new SettingsPanelMsg.BrowserEntered());

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
                        .withBrowsers(AccountBrowsers.NONE.with("github.com", "alice", Option.of("firefox")))));
        Settings kept = set.preferences().settings();

        Next next = Update.update(
                set.openPopup(new AccountBrowserPopup(alice, "  ")), new SettingsPanelMsg.BrowserEntered());

        assertEquals(AccountBrowsers.NONE, next.model().preferences().settings().browsers());
        assertEquals(List.of(new SaveSettings(kept, kept.withBrowsers(AccountBrowsers.NONE))), next.cmds());
    }

    @Test
    void choosingTheAccountInUseChangesNothing() {
        Model choosing = TestModels.loaded().openPopup(new GitHubAccountPopup(SETUP));

        Next next = Update.update(choosing, new SettingsPanelMsg.AccountChosen(Option.none()));

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

        Msg read = new SettingsPanelCmd.ReadGitHub("gh").run(env);

        assertEquals(
                new SettingsPanelMsg.GitHubRead(new GitHubSetup(
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

        Msg set = new SettingsPanelCmd.SetAccount(
                        Option.of("alice-work"), new Landing(BranchesTab.REMOTE, Option.none()))
                .run(env);

        assertEquals(new SettingsPanelMsg.AccountSet(), set);
        assertEquals(
                List.of("jazygit.githubUser=alice-work", "reopened " + Path.of("/repos/a") + " on REMOTE"),
                List.ofAll(done));
    }

    private static final Path FILE = Path.of("/home/me/.config/jazygit/config.toml");
    private static final SettingsProblem INVALID_THEME = new SettingsProblem.Invalid(FILE, List.of("ui.theme"));

    private static SettingsStore.Loaded read(Settings settings) {
        return new SettingsStore.Loaded(settings, Option.none());
    }

    /**
     * A settings file at {@link #FILE} that reads as {@code loaded}, and is made as {@code made} says.
     */
    private static SettingsStore file(SettingsStore.Loaded loaded, Either<SettingsProblem, Path> made) {
        return new SettingsStore() {
            @Override
            public Loaded load() {
                return loaded;
            }

            @Override
            public Either<SettingsProblem, Settings> save(Settings previous, Settings next) {
                return Either.right(next);
            }

            @Override
            public Option<Path> file() {
                return Option.of(FILE);
            }

            @Override
            public Either<SettingsProblem, Path> made() {
                return made;
            }
        };
    }

    private static Env env(Git git, SettingsStore settings, Terminal terminal) {
        return new Env(git, StubGitHub.of(), settings, RecentRepositories.NONE, Repositories.NONE, terminal);
    }

    @Test
    void eOpensTheSettingsFile() {
        Next next = Update.update(TestModels.loaded().openPopup(READ), new SettingsPanelMsg.FileRequested());

        assertEquals(List.of(new SettingsPanelCmd.EditFile()), next.cmds());
        assertEquals(Option.of(READ), next.model().popup(SettingsPanelPopup.class));
    }

    @Test
    void theFileIsMadeEditedWithTheTerminalHandedOverAndReadOnceTheEditorIsClosed() {
        java.util.List<String> events = new ArrayList<>();
        Terminal terminal = new Terminal() {
            @Override
            public <T> T handOver(Supplier<T> program) {
                events.add("leave");
                T result = program.get();
                events.add("come back");
                return result;
            }
        };
        Git git = StubGit.of().withEditor(new StubEditor() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> edit(List<String> files) {
                events.add("edit " + files.mkString(" "));
                return Either.right(List.empty());
            }
        });
        SettingsStore.Loaded edited = read(Settings.DEFAULT.withAnimations(false));

        Msg msg = new SettingsPanelCmd.EditFile().run(env(git, file(edited, Either.right(FILE)), terminal));

        assertEquals(new SettingsPanelMsg.FileEdited(List.empty(), edited), msg);
        assertEquals(java.util.List.of("leave", "edit " + FILE, "come back"), events);
    }

    @Test
    void aFileThatCannotBeMadeIsNotOpened() {
        java.util.List<String> events = new ArrayList<>();
        Git git = StubGit.of().withEditor(new StubEditor() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> edit(List<String> files) {
                events.add("edit");
                return Either.right(List.empty());
            }
        });
        SettingsProblem unwritable = new SettingsProblem.Unwritable(FILE, "Permission denied");

        Msg msg = new SettingsPanelCmd.EditFile()
                .run(env(git, file(SettingsStore.Loaded.DEFAULT, Either.left(unwritable)), Terminal.NONE));

        assertEquals(new SettingsPanelMsg.FileNotEdited(new Failure.Settings(unwritable)), msg);
        assertEquals(java.util.List.of(), events);
        AppError.OperationFailed error = assertInstanceOf(
                AppError.OperationFailed.class,
                Update.update(TestModels.loaded().openPopup(READ), msg)
                        .model()
                        .outcome()
                        .error()
                        .get());
        assertEquals(SettingsPanelCmd.EditFile.EDIT_SETTINGS, error.operation());
    }

    @Test
    void anEditorThatFailsShowsWhy() {
        Git git = StubGit.of().withEditor(new StubEditor() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> edit(List<String> files) {
                return Either.left(new GitFailure(new GitError.EditorFailed("nvim", 1), List.empty()));
            }
        });

        Msg msg = new SettingsPanelCmd.EditFile()
                .run(env(git, file(SettingsStore.Loaded.DEFAULT, Either.right(FILE)), Terminal.NONE));

        assertInstanceOf(SettingsPanelMsg.FileNotEdited.class, msg);
    }

    @Test
    void whatChangedInTheFileAppliesOnceTheEditorIsClosedAndThePanelShowsIt() {
        Settings edited = Settings.DEFAULT.withTheme(ThemeName.DARCULA).withAnimations(false);

        Model model = Update.update(
                        TestModels.loaded().openPopup(READ),
                        new SettingsPanelMsg.FileEdited(List.empty(), read(edited)))
                .model();

        assertEquals(edited, model.preferences().settings());
        assertEquals(read(edited), model.preferences().file());
        assertEquals(
                Option.of(READ.withTheme(ThemeName.DARCULA).withAnimations(false)),
                model.popup(SettingsPanelPopup.class));
        assertEquals(Option.none(), model.outcome().error());
    }

    @Test
    void readingTheFileAsThePanelOpensShowsWhereItIsAndKeepsASettingOnlyTheSessionHas() {
        // The animations were turned off, but could not be saved: the file has them on still.
        Model model = TestModels.loaded()
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withAnimations(false)))
                .openPopup(READ.withAnimations(false));

        Model read = Update.update(
                        model, new SettingsPanelMsg.FileRead(Option.of(FILE.toString()), SettingsStore.Loaded.DEFAULT))
                .model();

        assertFalse(read.preferences().settings().animations());
        assertEquals(
                Option.of(READ.withAnimations(false).withFile(Option.of(FILE.toString()))),
                read.popup(SettingsPanelPopup.class));
    }

    @Test
    void aValueThatIsNotValidKeepsTheOneInUseAndIsReported() {
        Model model = TestModels.loaded()
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withTheme(ThemeName.DARCULA)))
                .openPopup(READ);

        Model read = Update.update(
                        model,
                        new SettingsPanelMsg.FileEdited(
                                List.empty(), new SettingsStore.Loaded(Settings.DEFAULT, Option.of(INVALID_THEME))))
                .model();

        assertEquals(ThemeName.DARCULA, read.preferences().settings().theme());
        assertEquals(
                Option.of(new SettingsError.NotReread(INVALID_THEME)),
                read.outcome().error());
        assertEquals(Option.of(INVALID_THEME), read.preferences().reported());
    }

    @Test
    void aProblemReportedBeforeIsReportedAgainAfterTheEditor() {
        Model model = TestModels.loaded()
                .mapPreferences(preferences -> preferences.withReported(Option.of(INVALID_THEME)))
                .openPopup(READ);

        Model read = Update.update(
                        model,
                        new SettingsPanelMsg.FileEdited(
                                List.empty(), new SettingsStore.Loaded(Settings.DEFAULT, Option.of(INVALID_THEME))))
                .model();

        assertEquals(
                Option.of(new SettingsError.NotReread(INVALID_THEME)),
                read.outcome().error());
    }

    @Test
    void aProblemReportedBeforeIsNotReportedAgainAsThePanelOpens() {
        Model model = TestModels.loaded()
                .mapPreferences(preferences -> preferences.withReported(Option.of(INVALID_THEME)))
                .openPopup(READ);
        SettingsProblem other = new SettingsProblem.Invalid(FILE, List.of("ui.animations"));

        Model same = Update.update(
                        model,
                        new SettingsPanelMsg.FileRead(
                                Option.of(FILE.toString()),
                                new SettingsStore.Loaded(Settings.DEFAULT, Option.of(INVALID_THEME))))
                .model();
        Model changed = Update.update(
                        model,
                        new SettingsPanelMsg.FileRead(
                                Option.of(FILE.toString()),
                                new SettingsStore.Loaded(Settings.DEFAULT, Option.of(other))))
                .model();

        assertEquals(Option.none(), same.outcome().error());
        assertEquals(
                Option.of(new SettingsError.NotReread(other)), changed.outcome().error());
    }

    @Test
    void aThemeChangedInTheFileReplacesTheOneAskedForAtStartup() {
        Model model = TestModels.loaded()
                .mapPreferences(preferences -> preferences.withThemeOverride(Option.of(ThemeName.LIGHT)))
                .openPopup(READ);

        Model changed = Update.update(
                        model,
                        new SettingsPanelMsg.FileEdited(
                                List.empty(), read(Settings.DEFAULT.withTheme(ThemeName.DARCULA))))
                .model();
        Model unchanged = Update.update(
                        model,
                        new SettingsPanelMsg.FileEdited(List.empty(), read(Settings.DEFAULT.withAnimations(false))))
                .model();

        assertEquals(ThemeName.DARCULA, changed.preferences().theme());
        assertEquals(ThemeName.LIGHT, unchanged.preferences().theme());
    }

    @Test
    void aGhExecutableChangedInTheFileShowsTheRepositoryAgain() {
        Model model = TestModels.loaded().openPopup(READ);

        Next next = Update.update(
                model,
                new SettingsPanelMsg.FileEdited(
                        List.empty(), read(Settings.DEFAULT.withGhExecutable(Option.of("/opt/gh")))));

        assertEquals(Option.of("/opt/gh"), next.model().preferences().settings().ghExecutable());
        assertEquals(List.of(new SettingsPanelCmd.Reopen(Landing.of(model.repoView()))), next.cmds());
    }

    @Test
    void aProblemFoundWithAGhExecutableChangedInTheFileGoesOnToTheRepositoryShownAgain() {
        Model model = TestModels.loaded().openPopup(READ);

        Next next = Update.update(
                model,
                new SettingsPanelMsg.FileEdited(
                        List.empty(),
                        new SettingsStore.Loaded(
                                Settings.DEFAULT.withGhExecutable(Option.of("/opt/gh")), Option.of(INVALID_THEME))));
        // The repository is shown again, in a session of its own.
        Model shownAgain = Update.update(TestModels.loaded(), SettingsPanelUpdate.carried(next.model()))
                .model();

        assertEquals(List.of(new SettingsPanelCmd.Reopen(Landing.of(model.repoView()))), next.cmds());
        assertEquals(
                Option.of(new SettingsError.NotReread(INVALID_THEME)),
                shownAgain.outcome().error());
    }

    @Test
    void whatTheSessionKnewOfTheFileGoesOnWithoutAnotherErrorItWasShowing() {
        SettingsStore.Loaded file = read(Settings.DEFAULT.withAnimations(false));
        Model last = TestModels.loaded()
                .mapPreferences(preferences -> preferences.withFile(file).withReported(Option.of(INVALID_THEME)))
                .fail(new SettingsError.NotSaved(
                        new Failure.Settings(new SettingsProblem.Unwritable(FILE, "Permission denied"))));

        assertEquals(
                new SettingsMsg.Carried(file, Option.of(INVALID_THEME), Option.none()),
                SettingsPanelUpdate.carried(last));
    }

    @Test
    void aDiffStyleChangedInTheFileReadsTheDiffsAgain() {
        Model model = TestModels.loaded().openPopup(READ);
        Settings edited =
                Settings.DEFAULT.withDiffStyle(Settings.DEFAULT.diffStyle().toggled());

        Next next = Update.update(model, new SettingsPanelMsg.FileEdited(List.empty(), read(edited)));

        assertEquals(edited.diffStyle(), next.model().preferences().settings().diffStyle());
        assertEquals(DiffOptionsUpdate.changed(next.model()).cmds(), next.cmds());
    }

    @Test
    void theEditIsInTheCommandLog() {
        List<ExecutedCommand> commands = List.of(ExecutedCommand.of("nvim", FILE.toString()));

        Model model = Update.update(
                        TestModels.loaded().openPopup(READ),
                        new SettingsPanelMsg.FileEdited(commands, SettingsStore.Loaded.DEFAULT))
                .model();

        assertEquals(
                CommandLog.append(TestModels.loaded(), SettingsPanelCmd.EditFile.EDIT_SETTINGS, commands)
                        .outcome()
                        .commandLog(),
                model.outcome().commandLog());
    }

    @Test
    void readingTheFileReadsItAndWhereItIs() {
        SettingsStore.Loaded loaded = read(Settings.DEFAULT.withAnimations(false));

        Msg msg =
                new SettingsPanelCmd.ReadFile().run(env(StubGit.of(), file(loaded, Either.right(FILE)), Terminal.NONE));

        assertEquals(new SettingsPanelMsg.FileRead(Option.of(FILE.toString()), loaded), msg);
    }
}
