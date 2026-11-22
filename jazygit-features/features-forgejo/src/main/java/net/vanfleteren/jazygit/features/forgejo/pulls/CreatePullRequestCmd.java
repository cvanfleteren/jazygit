package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.core.forgejo.ForgejoError;
import net.vanfleteren.jazygit.core.forgejo.ForgejoNewPullRequest;
import net.vanfleteren.jazygit.core.forgejo.ForgejoRepository;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.features.branches.branch.BranchCmd;
import net.vanfleteren.jazygit.features.branches.branch.push.PushCmd;
import net.vanfleteren.jazygit.features.forgejo.builds.ForgejoServices;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Cmd.Guard;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Creating the pull request of a branch on Forgejo (ADR 178): checking where it is pushed to, pushing it first when
 * it is not on its remote, reading the title it starts out with for a base, and creating it.
 */
public sealed interface CreatePullRequestCmd extends Cmd {

    Operation CREATE = Operation.of("forgejo.log.title.createPullRequest", "forgejo.error.createPullRequest.failed")
            .labelledWhenBusy("forgejo.branches.busy.pullRequest");

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
     * Checks that {@code target}, where {@code branch} is pushed to, is in a remote on the host of {@code home}, the
     * remote the pull requests are opened against (ADR 177).
     */
    record CheckHead(RemoteName home, BranchName branch, RemoteBranch target) implements CreatePullRequestCmd {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("forgejo.work.head", branch.value()));
        }

        @Override
        public Msg run(Env env) {
            return checked(ForgejoRemotes.of(env, ForgejoServices.of(env), home)
                    .map(remotes -> remotes.remotes().containsKey(target.remote())
                            ? Option.<CreateError>none()
                            : Option.<CreateError>of(new CreateError.Elsewhere(branch, target)))
                    .getOrElse(() -> Option.of(new CreateError.NoHome())));
        }

        @Override
        public Msg failed(Failure failure) {
            return checked(Option.of(new CreateError.NoHome()));
        }

        private Msg checked(Option<CreateError> error) {
            return new CreatePullRequestMsg.HeadChecked(branch, error);
        }
    }

    /**
     * Reads the title a new pull request of {@code branch} starts out with, to ask what it says: the subject of its
     * oldest commit that is not on {@code baseRef}, the branch the first of {@code bases} is on the home's remote.
     * Without it, the user still gets to enter one.
     */
    record ReadTitle(BranchName branch, List<BranchName> bases, BranchRef baseRef) implements CreatePullRequestCmd {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("forgejo.work.title", branch.value()));
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
            return new CreatePullRequestMsg.TitleRead(branch, bases, subject);
        }
    }

    /**
     * Reads the title a new pull request of {@code branch} starts out with again, for {@code base}, which the user
     * picked instead, as {@link ReadTitle} does.
     */
    record RereadTitle(BranchName branch, BranchName base, BranchRef baseRef) implements CreatePullRequestCmd {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("forgejo.work.title", branch.value()));
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
     * Pushes a branch as {@code push} does, before its pull request is created, which its success leads to (ADR 25).
     */
    record PushFirst(PushCmd push) implements CreatePullRequestCmd, BranchCmd.KeepsBranchBusy {

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

    /**
     * Creates the pull request of {@code branch}, which is pushed to {@code target}, into {@code base} of the home's
     * remote {@code home}, titled {@code title}, saying {@code body}, and a draft when {@code draft} says so. A branch
     * with no commit that the base lacks is not sent.
     */
    record CreatePullRequest(
            RemoteName home,
            BranchName branch,
            RemoteBranch target,
            BranchName base,
            String title,
            String body,
            boolean draft)
            implements CreatePullRequestCmd, OperationCmd {

        @Override
        public Operation operation() {
            return CREATE;
        }

        @Override
        public Option<String> subject() {
            return Option.of(branch.value());
        }

        @Override
        public Option<BranchRef> busyBranch() {
            return Option.of(new BranchRef.Local(branch));
        }

        // The pull requests are read again once it is created, as after any change to what Forgejo has.
        @Override
        public boolean changesForge() {
            return true;
        }

        @Override
        public Msg run(Env env) {
            Forgejo forgejo = ForgejoServices.of(env);
            return ForgejoRemotes.of(env, forgejo, home)
                    .map(remotes -> remotes.remotes()
                            .get(target.remote())
                            .map(repository -> empty(env)
                                    ? failed(new CreateFailure.Empty(branch, base))
                                    : create(forgejo, remotes.home(), repository))
                            .getOrElse(() -> failed(new CreateFailure.Elsewhere(branch))))
                    .getOrElse(() -> failed(new ForgejoFailure.NoHome()));
        }

        /**
         * Whether the branch has no commit that the base lacks. When that cannot be told, Forgejo is asked anyway.
         */
        private boolean empty(Env env) {
            return env.git()
                    .history()
                    .countNotOn(new BranchRef.Local(branch), new BranchRef.Remote(new RemoteBranch(home, base)), 1)
                    .contains(0);
        }

        private Msg create(Forgejo forgejo, ForgejoRepository home, ForgejoRepository repository) {
            String head =
                    ForgejoNewPullRequest.head(home, repository, target.branch().value());
            // A head with the owner before it is in a fork, which a 404 then says must be one of the home's.
            Option<String> fork = Option.when(!head.equals(target.branch().value()), () -> named(repository));
            return forgejo.createPullRequest(home, new ForgejoNewPullRequest(head, base.value(), title, body, draft))
                    .fold(
                            error -> refused(error, home, fork),
                            created -> new CreatePullRequestMsg.Created(this, created));
        }

        private Msg refused(ForgejoError error, ForgejoRepository home, Option<String> fork) {
            Option<Long> existing = error instanceof ForgejoError.Refused refused ? refused.existing() : Option.none();
            return existing.<Msg>map(number -> new CreatePullRequestMsg.AlreadyOpen(this, number))
                    .getOrElse(() -> failed(new CreateFailure.Refused(error, home.host(), named(home), fork)));
        }

        private static String named(ForgejoRepository repository) {
            return repository.owner() + "/" + repository.name();
        }
    }
}
