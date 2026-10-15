package net.vanfleteren.jazygit.feature.settings;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.commit.CommitPopup;
import net.vanfleteren.jazygit.feature.theme.ThemePopup;
import net.vanfleteren.jazygit.gh.GhError;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.GhLogin;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.settings.RecentRepositories;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.ErrorPopup;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.GitHubAccount;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsPanelUpdateTest {

    private static final GitHubSetup SETUP = new GitHubSetup(Either.right("2.102.0"), "github.com", Option.none(),
            Either.right(List.of(new GhLogin("github.com", "alice", true, true),
                    new GhLogin("github.com", "alice-work", false, true))));
    private static final SettingsPanelPopup READ = new SettingsPanelPopup(ThemeName.DARK, Option.none(), Option.of(SETUP));

    @Test
    void requestingShowsTheSettingsAndReadsHowGhIsSetUp() {
        Next next = Update.update(TestModels.loaded(), new SettingsPanelMsg.Requested());

        assertEquals(Option.of(new SettingsPanelPopup(ThemeName.DARK, Option.none(), Option.none())),
                next.model().popup(SettingsPanelPopup.class));
        assertEquals(List.of(new SettingsPanelCmd.ReadGitHub("gh")), next.cmds());
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
        Model open = Update.update(TestModels.loaded(), new SettingsPanelMsg.Requested()).model();

        Model read = Update.update(open, new SettingsPanelMsg.GitHubRead(SETUP)).model();

        assertEquals(Option.of(READ), read.popup(SettingsPanelPopup.class));
    }

    @Test
    void whatIsReadAfterThePanelClosedIsDropped() {
        Model closed = TestModels.loaded();

        assertEquals(closed, Update.update(closed, new SettingsPanelMsg.GitHubRead(SETUP)).model());
    }

    @Test
    void theThemeIsChosenInItsOwnDialog() {
        Model open = TestModels.loaded().openPopup(READ);

        Model choosing = Update.update(open, new SettingsPanelMsg.ThemeRequested()).model();

        assertEquals(Option.of(new ThemePopup(ThemeName.DARK)), choosing.popup(ThemePopup.class));
    }

    @Test
    void anEnteredGhExecutableIsCheckedFirst() {
        Model entering = Update.update(TestModels.loaded().openPopup(READ), new SettingsPanelMsg.GhExecutableRequested()).model();
        assertEquals(Option.of(new GhExecutablePopup("")), entering.popup(GhExecutablePopup.class));

        Next next = Update.update(entering, new SettingsPanelMsg.GhExecutableEntered("  /opt/gh/bin/gh "));

        assertEquals(Option.none(), next.model().popup());
        assertEquals(List.of(new SettingsPanelCmd.CheckGhExecutable(Option.of("/opt/gh/bin/gh"))), next.cmds());
    }

    @Test
    void anEmptyGhExecutableIsTheOneOnThePath() {
        Model entering = TestModels.loaded().openPopup(new GhExecutablePopup("/opt/gh/bin/gh"));

        Next next = Update.update(entering, new SettingsPanelMsg.GhExecutableEntered(" "));

        assertEquals(List.of(new SettingsPanelCmd.CheckGhExecutable(Option.none())), next.cmds());
    }

    @Test
    void aGhExecutableThatRunsIsKeptAndTheRepositoryShownAgain() {
        Settings chosen = Settings.DEFAULT.withGhExecutable(Option.of("/opt/gh/bin/gh"));

        Next next = Update.update(TestModels.loaded(),
                new SettingsPanelMsg.GhExecutableChecked(Option.of("/opt/gh/bin/gh"), Either.right("2.102.0")));

        assertEquals(chosen, next.model().settings());
        assertEquals(List.of(new Cmd.SaveSettings(Settings.DEFAULT, chosen), new SettingsPanelCmd.Reopen()), next.cmds());
    }

    @Test
    void aGhExecutableThatDoesNotRunIsNotUsed() {
        Failure failure = new Failure.GitHub(new GhError.GhUnavailable(new java.io.IOException("no such file")));

        Next next = Update.update(TestModels.loaded(),
                new SettingsPanelMsg.GhExecutableChecked(Option.of("/nowhere/gh"), Either.left(failure)));

        assertEquals(Settings.DEFAULT, next.model().settings());
        assertEquals(List.empty(), next.cmds());
        assertEquals(Option.of(new ErrorPopup(new AppError.GhExecutableUnusable(Option.of("/nowhere/gh"), failure))),
                next.model().popup(ErrorPopup.class));
    }

    @Test
    void theAccountIsChosenFromTheOnesRead() {
        Model choosing = Update.update(TestModels.loaded().openPopup(READ), new SettingsPanelMsg.AccountRequested()).model();
        assertEquals(Option.of(new GitHubAccountPopup(SETUP)), choosing.popup(GitHubAccountPopup.class));

        Next next = Update.update(choosing, new SettingsPanelMsg.AccountChosen(Option.of("alice-work")));

        assertEquals(Option.none(), next.model().popup());
        assertEquals(List.of(new SettingsPanelCmd.SetAccount(Option.of("alice-work"))), next.cmds());
    }

    @Test
    void theAccountCannotBeChosenWhileTheAccountsAreRead() {
        Model reading = TestModels.loaded().openPopup(new SettingsPanelPopup(ThemeName.DARK, Option.none(), Option.none()));

        assertEquals(reading, Update.update(reading, new SettingsPanelMsg.AccountRequested()).model());
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
        Env env = new Env(new StubGitProvider() {
            @Override
            public Either<ProcessError, Option<String>> localConfig(String key) {
                return Either.right(Option.when(key.equals(GitHubAccount.USER_KEY), "alice-work"));
            }

            @Override
            public Either<ProcessError, Map<String, String>> remoteUrls() {
                return Either.right(HashMap.of("origin", "git@github.com:team/repo.git"));
            }
        }, new StubGitHub() {
            @Override
            public Either<GhError, List<GhLogin>> logins() {
                return Either.right(SETUP.logins().get());
            }
        });

        Msg read = new SettingsPanelCmd.ReadGitHub("gh").run(env);

        assertEquals(new SettingsPanelMsg.GitHubRead(new GitHubSetup(Either.right("2.102.0"), "github.com",
                Option.of("alice-work"), SETUP.logins())), read);
    }

    @Test
    void settingTheAccountConfiguresItAndShowsTheRepositoryAgain() {
        java.util.List<String> done = new ArrayList<>();
        Env env = new Env(new StubGitProvider() {
            @Override
            public Either<GitFailure<ProcessError>, List<ExecutedCommand>> setLocalConfig(String key, Option<String> value) {
                done.add(key + "=" + value.getOrElse("<unset>"));
                return Either.right(List.empty());
            }

            @Override
            public Path workTree() {
                return Path.of("/repos/a");
            }
        }, StubGitHub.empty(), SettingsStore.NONE, RecentRepositories.NONE, directory -> {
            done.add("reopened " + directory);
            return Either.right(directory);
        });

        Msg set = new SettingsPanelCmd.SetAccount(Option.of("alice-work")).run(env);

        assertEquals(new SettingsPanelMsg.AccountSet(), set);
        assertEquals(List.of("jazygit.githubUser=alice-work", "reopened " + Path.of("/repos/a")), List.ofAll(done));
    }
}
