package net.vanfleteren.jazygit.feature.settings;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.gh.model.GhAccount;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.github.*;
import net.vanfleteren.jazygit.state.operation.Operation;

/**
 * The IO of the settings.
 */
public sealed interface SettingsPanelCmd extends Cmd {

    /**
     * Reads how gh is set up for the repository: the version of the gh at {@code executable}, the account the
     * repository is configured to use, the accounts gh is logged in to, the account gh runs as and the browser gh
     * would open its links in. In the background, as gh talks to GitHub to check the accounts.
     */
    record ReadGitHub(String executable) implements SettingsPanelCmd {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.gh"));
        }

        @Override
        public boolean background() {
            return true;
        }

        @Override
        public Msg run(Env env) {
            Option<GhAccount> account = env.gitHub().account().current();
            return new SettingsPanelMsg.GitHubRead(new GitHubSetup(
                    env.gitHub().account().version(executable).mapLeft(Failure.GitHub::new),
                    GitHubAccount.host(env.git()),
                    env.git().config().local(GitHubAccount.USER_KEY).getOrElse(Option.none()),
                    env.gitHub().account().logins().mapLeft(Failure.GitHub::new),
                    account,
                    account.flatMap(known -> env.gitHub().account().ghBrowser(known.host()))));
        }

        @Override
        public Msg failed(Failure failure) {
            return new SettingsPanelMsg.GitHubRead(new GitHubSetup(
                    Either.left(failure), "", Option.none(), Either.left(failure), Option.none(), Option.none()));
        }
    }

    /**
     * Checks that {@code executable}, none for the one on the PATH, is a gh that runs.
     */
    record CheckGhExecutable(Option<String> executable) implements SettingsPanelCmd {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.gh"));
        }

        @Override
        public boolean background() {
            return true;
        }

        @Override
        public Msg run(Env env) {
            return new SettingsPanelMsg.GhExecutableChecked(
                    executable, env.gitHub().account().version(executable.getOrElse("gh")).mapLeft(Failure.GitHub::new));
        }

        @Override
        public Msg failed(Failure failure) {
            return new SettingsPanelMsg.GhExecutableChecked(executable, Either.left(failure));
        }
    }

    /**
     * Configures the repository to use the account {@code user}, none for gh's active one, and shows it again,
     * so gh and git run as that account from now on; it is shown where {@code landing} says, where it was.
     */
    record SetAccount(Option<String> user, Landing landing) implements SettingsPanelCmd {

        @Override
        public Msg run(Env env) {
            return env.git()
                    .config()
                    .setLocal(GitHubAccount.USER_KEY, user)
                    .map(commands -> env.repositories()
                            .switchTo(env.git().worktree(), landing)
                            .mapLeft(Failure::of)
                            .fold(this::failed, reopened -> new SettingsPanelMsg.AccountSet()))
                    .mapLeft(Failure::of)
                    .getOrElseGet(this::failed);
        }

        @Override
        public Msg failed(Failure failure) {
            return new SettingsPanelMsg.AccountNotSet(failure);
        }
    }

    /**
     * Shows the repository again, so gh runs as the settings say now; it is shown where {@code landing} says,
     * where it was.
     */
    record Reopen(Landing landing) implements SettingsPanelCmd {

        @Override
        public Msg run(Env env) {
            return env.repositories()
                    .switchTo(env.git().worktree(), landing)
                    .mapLeft(Failure::of)
                    .fold(this::failed, reopened -> new SettingsPanelMsg.Reopened());
        }

        @Override
        public Msg failed(Failure failure) {
            return new SettingsPanelMsg.NotReopened(failure);
        }
    }

    /**
     * Reads the settings file again, as the panel opens, for what was edited outside jazygit (ADR 111). On the
     * {@code io} executor, as saving runs there: a save asked for before is written before this reads.
     */
    record ReadFile() implements SettingsPanelCmd {

        @Override
        public Msg run(Env env) {
            return new SettingsPanelMsg.FileRead(
                    env.settings().file().map(Path::toString), env.settings().load());
        }

        @Override
        public Msg failed(Failure failure) {
            return new SettingsPanelMsg.FileNotRead(failure);
        }
    }

    /**
     * Opens the settings file in git's editor, with the terminal handed over to it, made empty first when it is not
     * there, and reads it again once the editor is closed (ADR 111). On the {@code io} executor, as saving runs there.
     */
    record EditFile() implements SettingsPanelCmd {

        public static final Operation EDIT_SETTINGS =
                Operation.of("log.title.editSettings", "error.editSettings.failed");

        @Override
        public Msg run(Env env) {
            return env.settings()
                    .made()
                    .<Failure>mapLeft(Failure.Settings::new)
                    .flatMap(file -> env.terminal()
                            .handOver(() -> env.git().editor().edit(List.of(file.toString())))
                            .mapLeft(Failure::of))
                    .fold(
                            this::failed,
                            commands -> new SettingsPanelMsg.FileEdited(
                                    commands, env.settings().load()));
        }

        @Override
        public Msg failed(Failure failure) {
            return new SettingsPanelMsg.FileNotEdited(failure);
        }
    }
}
