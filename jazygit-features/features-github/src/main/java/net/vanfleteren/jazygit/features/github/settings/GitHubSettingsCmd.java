package net.vanfleteren.jazygit.features.github.settings;

import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.GhAccount;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.features.github.state.*;
import net.vanfleteren.jazygit.kernel.state.*;

/**
 * The IO of the GitHub section of the settings panel.
 */
public sealed interface GitHubSettingsCmd extends Cmd {

    /**
     * Reads how gh is set up for the repository: the version of the gh at {@code executable}, the account the
     * repository is configured to use, the accounts gh is logged in to, the account gh runs as and the browser gh
     * would open its links in. In the background, as gh talks to GitHub to check the accounts.
     */
    record ReadGitHub(String executable) implements GitHubSettingsCmd {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("github.work.gh"));
        }

        @Override
        public boolean background() {
            return true;
        }

        @Override
        public Msg run(Env env) {
            Option<GhAccount> account = env.gitHub().account().current();
            return new GitHubSettingsMsg.GitHubRead(new GitHubSetup(
                    env.gitHub().account().version(executable).mapLeft(GitHubFailure::new),
                    GitHubAccount.host(env.git()),
                    env.git().config().local(GitHubAccount.USER_KEY).getOrElse(Option.none()),
                    env.gitHub().account().logins().mapLeft(GitHubFailure::new),
                    account,
                    account.flatMap(known -> env.gitHub().account().ghBrowser(known.host()))));
        }

        @Override
        public Msg failed(Failure failure) {
            return new GitHubSettingsMsg.GitHubRead(new GitHubSetup(
                    Either.left(failure), "", Option.none(), Either.left(failure), Option.none(), Option.none()));
        }
    }

    /**
     * Checks that {@code executable}, none for the one on the PATH, is a gh that runs.
     */
    record CheckGhExecutable(Option<String> executable) implements GitHubSettingsCmd {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("github.work.gh"));
        }

        @Override
        public boolean background() {
            return true;
        }

        @Override
        public Msg run(Env env) {
            return new GitHubSettingsMsg.GhExecutableChecked(
                    executable,
                    env.gitHub().account().version(executable.getOrElse("gh")).mapLeft(GitHubFailure::new));
        }

        @Override
        public Msg failed(Failure failure) {
            return new GitHubSettingsMsg.GhExecutableChecked(executable, Either.left(failure));
        }
    }

    /**
     * Configures the repository to use the account {@code user}, none for gh's active one, and shows it again,
     * so gh and git run as that account from now on; it is shown where {@code landing} says, where it was.
     */
    record SetAccount(Option<String> user, Landing landing) implements GitHubSettingsCmd {

        @Override
        public Msg run(Env env) {
            return env.git()
                    .config()
                    .setLocal(GitHubAccount.USER_KEY, user)
                    .map(commands -> env.repositories()
                            .switchTo(env.git().worktree(), landing)
                            .mapLeft(Failure::of)
                            .fold(this::failed, reopened -> new GitHubSettingsMsg.AccountSet()))
                    .mapLeft(Failure::of)
                    .getOrElseGet(this::failed);
        }

        @Override
        public Msg failed(Failure failure) {
            return new GitHubSettingsMsg.AccountNotSet(failure);
        }
    }
}
