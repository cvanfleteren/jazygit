package net.vanfleteren.jazygit.feature.github.create;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.fetch.FetchUpdate;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.Base;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.CreatePullRequest;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.ReadParent;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.ReadTitle;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.RereadTitle;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestMsg.*;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Creating the pull request of a branch: reading the branch it came from and the title it starts out with, then
 * asking what it merges into and what it says, in one popup, where the title follows the base until the user types
 * one.
 */
public final class CreatePullRequestUpdate {

    private CreatePullRequestUpdate() {}

    public static Next update(Model model, CreatePullRequestMsg msg) {
        return switch (msg) {
            // The branch may have gone while its parent was read; then there is nothing to ask.
            case ParentRead(var branch, var parent) ->
                model.repo()
                        .branch(branch)
                        .map(known -> readTitle(model, known, parentFirst(bases(model, branch), parent)))
                        .getOrElse(() -> Next.of(model));
            // Without a commit of its own to name it after, it is named after the branch, as gh would.
            case TitleRead(var branch, var head, var bases, var subject) -> {
                String title = subject.getOrElse(branch::value);
                yield Next.of(model.clearError()
                        .openPopupIfFree(new NewPullRequestPopup(
                                branch, head, bases, bases.head(), title, new NewPullRequest(title, "", false))));
            }
            case BaseMoved(var cursor, var base) ->
                model.popup(NewPullRequestPopup.class)
                        .map(popup -> moved(model.withListCursor(cursor), popup, base))
                        .getOrElse(() -> Next.of(model));
            // A title read for a base no longer picked, or after the user typed one, is dropped.
            case BaseTitleRead(var branch, var base, var subject) ->
                Next.of(model.popup(NewPullRequestPopup.class)
                        .filter(popup ->
                                popup.branch().equals(branch) && popup.base().equals(base) && popup.titleUntouched())
                        .map(popup -> model.openPopup(titled(popup, subject.getOrElse(branch::value))))
                        .getOrElse(model));
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
     * {@code popup} with {@code title} read for its base, as the title too.
     */
    private static NewPullRequestPopup titled(NewPullRequestPopup popup, String title) {
        NewPullRequest pullRequest = popup.pullRequest();
        return popup.withTitleRead(title)
                .withPullRequest(new NewPullRequest(title, pullRequest.body(), pullRequest.draft()));
    }

    /**
     * {@code model}, where the user moved in the list of bases of {@code popup} to {@code base}, if the filter shows
     * any: that is the base then, and the title is read for it while it is the one read before.
     */
    private static Next moved(Model model, NewPullRequestPopup popup, Option<Branch.Name> base) {
        return base.filter(picked -> !picked.equals(popup.base()))
                .map(picked -> {
                    Model picking = model.openPopup(popup.withBase(picked));
                    return popup.titleUntouched()
                            ? Next.of(picking, new RereadTitle(popup.branch(), picked, baseRef(model, picked)))
                            : Next.of(picking);
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Asks what the pull request of {@code branch}, which has none yet, merges into and what it says, once the title
     * it starts out with is read, and before that, with several to choose from, the branch it came from, which is
     * offered first. With none to merge into, there is nothing to ask.
     */
    public static Next ask(Model model, Branch branch) {
        List<Branch.Name> bases = bases(model, branch.name());
        return bases.size() < 2
                ? readTitle(model, branch, bases)
                : Next.of(
                        model, new ReadParent(branch.name(), bases.map(base -> new Base(base, baseRef(model, base)))));
    }

    /**
     * Reads the title the pull request of {@code branch} into the first of {@code bases} starts out with. The branch
     * can have another name on its remote; gh needs that one. Without bases, which the branches read while the
     * parent was can also leave, there is nothing to ask.
     */
    private static Next readTitle(Model model, Branch branch, List<Branch.Name> bases) {
        return bases.headOption()
                .map(first -> Next.of(
                        model,
                        new ReadTitle(branch.name(), branch.pushTarget().branch(), bases, baseRef(model, first))))
                .getOrElse(() -> Next.of(model.fail(new AppError.NoBase(branch.name()))));
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
