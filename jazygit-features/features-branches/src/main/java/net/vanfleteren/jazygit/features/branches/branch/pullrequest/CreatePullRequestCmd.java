package net.vanfleteren.jazygit.features.branches.branch.pullrequest;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.features.branches.branch.BranchCmd;
import net.vanfleteren.jazygit.features.branches.branch.push.PushCmd;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Cmd.Guard;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * What creating a pull request asks of git, on any forge: pushing the branch first when it is not on its remote
 * (ADR 25), and reading the title it starts out with for a base (ADR 18).
 */
public sealed interface CreatePullRequestCmd extends Cmd {

    /**
     * The subject of the oldest commit of {@code branch} that is not on {@code baseRef}, if it could be read.
     */
    private static Option<String> firstSubject(Env env, BranchName branch, BranchRef baseRef) {
        return env.git()
                .history()
                .firstSubject(new BranchRef.Local(branch), baseRef)
                .getOrElse(Option.none());
    }

    /**
     * Reads the title a new pull request of {@code branch} starts out with, to ask what it says: the subject of its
     * oldest commit that is not on {@code baseRef}, the branch the base offered first is on its remote. Without it, the
     * user still gets to enter one, in the popup {@code opening} opens.
     */
    record ReadTitle(BranchName branch, BranchRef baseRef, FormOpening opening) implements CreatePullRequestCmd {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("branches.work.title", branch.value()));
        }

        @Override
        public Msg run(Env env) {
            return read(firstSubject(env, branch, baseRef));
        }

        @Override
        public Msg failed(Failure failure) {
            return read(Option.none());
        }

        private Msg read(Option<String> subject) {
            return new CreatePullRequestMsg.TitleRead(branch, opening, subject);
        }
    }

    /**
     * Reads the title a new pull request of {@code branch} starts out with again, for {@code base}, which the user
     * picked instead, as {@link ReadTitle} does.
     */
    record RereadTitle(BranchName branch, BranchName base, BranchRef baseRef) implements CreatePullRequestCmd {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("branches.work.title", branch.value()));
        }

        @Override
        public Msg run(Env env) {
            return read(firstSubject(env, branch, baseRef));
        }

        @Override
        public Msg failed(Failure failure) {
            return read(Option.none());
        }

        private Msg read(Option<String> subject) {
            return new CreatePullRequestMsg.BaseTitleRead(branch, base, subject);
        }
    }

    /**
     * Pushes a branch as {@code push} does, before its pull request is created, which its success leads to: what
     * {@code then} does.
     */
    record PushFirst(PushCmd push, AfterPush then) implements CreatePullRequestCmd, BranchCmd.KeepsBranchBusy {

        @Override
        public BranchName branch() {
            return push.branch();
        }

        @Override
        public Operation operation() {
            return push.operation();
        }

        @Override
        public boolean changesForge() {
            return push.changesForge();
        }

        @Override
        public Option<Guard> guard() {
            return push.guard();
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return push.perform(git);
        }

        @Override
        public Msg succeeded(List<ExecutedCommand> commands) {
            return new CreatePullRequestMsg.Pushed(this, commands);
        }
    }
}
