package net.vanfleteren.jazygit.features.forgejo.create;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.core.forgejo.ForgejoError;
import net.vanfleteren.jazygit.core.forgejo.ForgejoNewPullRequest;
import net.vanfleteren.jazygit.core.forgejo.ForgejoRepository;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.features.forgejo.ForgejoFailure;
import net.vanfleteren.jazygit.features.forgejo.builds.ForgejoServices;
import net.vanfleteren.jazygit.features.forgejo.pulls.ForgejoRemotes;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Creating the pull request of a branch on Forgejo (ADR 178): checking where it is pushed to, and creating it. Pushing
 * it first and reading its title are {@code CreatePullRequestCmd}'s.
 */
public sealed interface ForgejoCreatePullRequestCmd extends Cmd {

    Operation CREATE = Operation.of("forgejo.log.title.createPullRequest", "forgejo.error.createPullRequest.failed")
            .labelledWhenBusy("forgejo.branches.busy.pullRequest");

    /**
     * Checks that {@code target}, where {@code branch} is pushed to, is in a remote on the host of {@code home}, the
     * remote the pull requests are opened against (ADR 177).
     */
    record CheckHead(RemoteName home, BranchName branch, RemoteBranch target) implements ForgejoCreatePullRequestCmd {

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
            return new ForgejoCreatePullRequestMsg.HeadChecked(branch, error);
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
            implements ForgejoCreatePullRequestCmd, OperationCmd {

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
            Option<RepoSlug> fork = Option.when(!head.equals(target.branch().value()), repository::slug);
            return forgejo.createPullRequest(
                            home, new ForgejoNewPullRequest(head, new ForgeBranch(base.value()), title, body, draft))
                    .fold(
                            error -> refused(error, home, fork),
                            created -> new ForgejoCreatePullRequestMsg.Created(this, created));
        }

        private Msg refused(ForgejoError error, ForgejoRepository home, Option<RepoSlug> fork) {
            Option<PrNumber> existing =
                    error instanceof ForgejoError.Refused refused ? refused.existing() : Option.none();
            return existing.<Msg>map(number -> new ForgejoCreatePullRequestMsg.AlreadyOpen(this, number))
                    .getOrElse(() -> failed(new CreateFailure.Refused(error, home.host(), home.slug(), fork)));
        }
    }
}
