package net.vanfleteren.jazygit.feature.settings;

import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Label;

/**
 * The IO of the settings.
 */
public sealed interface SettingsPanelCmd extends Cmd {

    /**
     * Reads how gh is set up for the repository: the version of the gh at {@code executable}, the account the
     * repository is configured to use, and the accounts gh is logged in to. In the background, as gh talks to
     * GitHub to check the accounts.
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
            return new SettingsPanelMsg.GitHubRead(new GitHubSetup(
                    env.gitHub().version(executable).mapLeft(Failure.GitHub::new),
                    GitHubAccount.host(env.git()),
                    env.git().localConfig(GitHubAccount.USER_KEY).getOrElse(Option.none()),
                    env.gitHub().logins().mapLeft(Failure.GitHub::new)));
        }

        @Override
        public Msg failed(Failure failure) {
            return new SettingsPanelMsg.GitHubRead(
                    new GitHubSetup(Either.left(failure), "", Option.none(), Either.left(failure)));
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
                    executable, env.gitHub().version(executable.getOrElse("gh")).mapLeft(Failure.GitHub::new));
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
                    .setLocalConfig(GitHubAccount.USER_KEY, user)
                    .map(commands -> env.repositories()
                            .switchTo(env.git().worktree(), landing)
                            .fold(this::failedWith, reopened -> new SettingsPanelMsg.AccountSet()))
                    .getOrElseGet(this::failedWith);
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
                    .fold(this::failedWith, reopened -> new SettingsPanelMsg.Reopened());
        }

        @Override
        public Msg failed(Failure failure) {
            return new SettingsPanelMsg.NotReopened(failure);
        }
    }
}
