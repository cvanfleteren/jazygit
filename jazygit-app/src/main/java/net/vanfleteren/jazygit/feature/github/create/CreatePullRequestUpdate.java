package net.vanfleteren.jazygit.feature.github.create;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.fetch.FetchUpdate;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.Base;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.CreatePullRequest;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.ReadParent;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.ReadTitle;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestMsg.*;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Creating the pull request of a branch: reading the branch it came from, asking what it merges into, reading the
 * title it starts out with, and asking what it says.
 */
public final class CreatePullRequestUpdate {

    private CreatePullRequestUpdate() {}

    public static Next update(Model model, CreatePullRequestMsg msg) {
        return switch (msg) {
            // The branch may have gone while its parent was read; then there is nothing to ask.
            case ParentRead(var branch, var parent) ->
                Next.of(model.repo()
                        .branch(branch)
                        .map(known -> model.clearError()
                                .openPopupIfFree(new BasePopup(branch, parentFirst(bases(model, branch), parent))))
                        .getOrElse(model));
            case BaseChosen(Branch.Name base) ->
                model.popup(BasePopup.class)
                        .flatMap(popup -> model.repo().branch(popup.branch()))
                        // The branch can have another name on its remote; gh needs that one.
                        .map(known -> Next.of(
                                model.withoutPopup(BasePopup.class),
                                new ReadTitle(known.name(), known.pushTarget().branch(), base, baseRef(model, base))))
                        .getOrElse(() -> Next.of(model.withoutPopup(BasePopup.class)));
            case BaseCancelled() -> Next.of(model.withoutPopup(BasePopup.class));
            // Without a commit of its own to name it after, it is named after the branch, as gh would.
            case TitleRead(var branch, var head, var base, var subject) ->
                Next.of(model.openPopupIfFree(new NewPullRequestPopup(
                        branch, head, base, new NewPullRequest(subject.getOrElse(branch::value), "", false))));
            case PullRequestEdited(NewPullRequest pullRequest) ->
                Next.of(model.popup(NewPullRequestPopup.class)
                        .map(popup -> model.openPopup(popup.withPullRequest(pullRequest)))
                        .getOrElse(model));
            // A blank title keeps the popup open, as GitHub needs one.
            case Confirmed()
            when model.popup(NewPullRequestPopup.class)
                    .exists(popup -> popup.pullRequest().title().isBlank()) -> Next.of(model);
            case Confirmed() ->
                Update.confirm(
                        model,
                        NewPullRequestPopup.class,
                        popup -> model.repo()
                                .branch(popup.branch())
                                .map(known -> new CreatePullRequest(
                                        popup.branch(), popup.head(), popup.base(), stripped(popup.pullRequest()))));
            case Cancelled() -> Next.of(model.withoutPopup(NewPullRequestPopup.class));
            case Created(CreatePullRequest cmd, var pullRequest, var commands) -> {
                // Done like any operation. The new pull request shows at once, as GitHub may not list it yet;
                // reading the pull requests then gives its checks and review.
                Next done = OperationUpdate.succeeded(model, cmd, commands);
                Model shown = pullRequest
                        .map(pr -> done.model()
                                .mapGitHub(gitHub -> gitHub.withPullRequests(gitHub.pullRequests()
                                        .map(open -> open.put(new Branch.Name(pr.headBranch()), pr)))))
                        .getOrElse(done.model());
                // A read that is still running may have missed it, so this one is started anyway. GitHub may have
                // changed the remote too, which a fetch brings in.
                yield FetchUpdate.requested(PullRequestsUpdate.loadAnyway(new Next(shown, done.cmds())));
            }
        };
    }

    /**
     * {@code pullRequest} without the whitespace around its title.
     */
    private static NewPullRequest stripped(NewPullRequest pullRequest) {
        return new NewPullRequest(pullRequest.title().strip(), pullRequest.body(), pullRequest.draft());
    }

    /**
     * Asks which branch the pull request of {@code branch}, which has none yet, merges into, once the branch it came
     * from is read, which is offered first. With one to choose or none, there is nothing to read.
     */
    public static Next askBase(Model model, Branch.Name branch) {
        List<Branch.Name> bases = bases(model, branch);
        return bases.size() < 2
                ? Update.ask(model, new BasePopup(branch, bases))
                : Next.of(model, new ReadParent(branch, bases.map(base -> new Base(base, baseRef(model, base)))));
    }

    /**
     * {@code bases} with {@code parent} first, if it is one of them.
     */
    static List<Branch.Name> parentFirst(List<Branch.Name> bases, Option<Branch.Name> parent) {
        return parent.filter(bases::contains)
                .map(first -> bases.remove(first).prepend(first))
                .getOrElse(bases);
    }

    /**
     * The branch {@code base} is on its remote, which a pull request merges into; the local one when it has no
     * upstream there.
     */
    private static BranchRef baseRef(Model model, Branch.Name base) {
        return model.repo()
                .branch(base)
                .flatMap(Branch::remoteUpstream)
                .map(upstream -> BranchRef.of(upstream.branch()))
                .getOrElse(() -> new BranchRef.Local(base));
    }

    /**
     * The branches a pull request from {@code branch} can merge into: the others that are on their remote, the
     * default branch first.
     */
    static List<Branch.Name> bases(Model model, Branch.Name branch) {
        return model.repo()
                .branches()
                .toOption()
                .getOrElse(List.empty())
                .filter(b -> b.hasRemote() && !b.name().equals(branch))
                .sortBy(b -> !b.isDefault())
                .map(Branch::name);
    }
}
