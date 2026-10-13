package net.vanfleteren.jazygit.feature.github;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.OperationCmd;

/**
 * Commands that talk to GitHub, through gh.
 */
public sealed interface PullRequestCmd extends Cmd {

    /**
     * Creates a pull request from {@code branch}, as it is on its remote, into {@code base}.
     */
    record CreatePullRequest(String branch, String base) implements PullRequestCmd, OperationCmd {

        @Override
        public Operation operation() {
            return Operation.CREATE_PR;
        }

        @Override
        public Option<String> subject() {
            return Option.of(branch);
        }

        @Override
        public Option<String> busyBranch() {
            return Option.of(branch);
        }

        @Override
        public Msg run(Env env) {
            // The branch can have another name on its remote; gh needs that one.
            return env.git().remoteBranch(branch).fold(this::failedWith, remote -> {
                String head = remote.getOrElse(branch);
                return env.gitHub().createPullRequest(head, base).fold(
                        failure -> failed(Failure.of(failure)),
                        created -> new PullRequestMsg.Created(this,
                                created.number().map(number -> new PullRequest(number, head, false, Checks.NONE, Review.NONE)),
                                created.commands()));
            });
        }
    }

    /**
     * Opens pull request {@code number}, from {@code branch}, in the browser.
     */
    record OpenPullRequest(String branch, int number) implements PullRequestCmd, OperationCmd {

        @Override
        public Operation operation() {
            return Operation.OPEN_PR;
        }

        @Override
        public Option<String> subject() {
            return Option.of(branch);
        }

        @Override
        public Msg run(Env env) {
            return env.gitHub().openPullRequest(number).fold(failure -> failed(Failure.of(failure)), this::succeeded);
        }
    }

    /**
     * Reads the open pull requests. It runs in the background, as it talks to GitHub, and nobody waits for it.
     */
    record LoadPullRequests() implements PullRequestCmd {

        @Override
        public boolean background() {
            return true;
        }

        @Override
        public Msg run(Env env) {
            return env.gitHub().pullRequests().fold(
                    error -> failed(new Failure.GitHub(error)),
                    PullRequestMsg.Loaded::new);
        }

        @Override
        public Msg failed(Failure failure) {
            return new PullRequestMsg.LoadFailed(failure);
        }
    }
}
