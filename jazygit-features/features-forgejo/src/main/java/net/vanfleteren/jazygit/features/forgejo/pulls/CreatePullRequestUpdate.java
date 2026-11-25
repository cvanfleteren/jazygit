package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequest;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.RemoteRef;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.NewPullRequestCmd.ReadTitle;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.NewPullRequestUpdate;
import net.vanfleteren.jazygit.features.forgejo.pulls.CreatePullRequestCmd.CheckHead;
import net.vanfleteren.jazygit.features.forgejo.pulls.CreatePullRequestCmd.CreatePullRequest;
import net.vanfleteren.jazygit.features.forgejo.pulls.CreatePullRequestMsg.*;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeHome;
import net.vanfleteren.jazygit.kernel.state.operation.OperationMsg;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Creating the pull request on Forgejo of a branch that has none (ADR 178): checking that it is pushed to a remote
 * on the host of the home, pushing it first when it is not on its remote, reading the title it starts out with, then
 * asking what it merges into and what it says, in one popup. Pushing it first and the title that follows the base are
 * {@link NewPullRequestUpdate}'s.
 */
public final class CreatePullRequestUpdate {

    // A remote's own pointer to its default branch, which is no branch to merge into.
    private static final BranchName REMOTE_HEAD = new BranchName("HEAD");

    private CreatePullRequestUpdate() {}

    public static Next update(Model model, CreatePullRequestMsg msg) {
        return switch (msg) {
            case HeadChecked(var branch, var error) -> headChecked(model, branch, error);
            case Edited(var title, var body, var draft) -> edited(model, title, body, draft);
            case Confirmed() -> confirmed(model);
            case Created(var cmd, var created) -> created(model, cmd, created);
            case AlreadyOpen(var cmd, var number) -> alreadyOpen(model, cmd, number);
        };
    }

    /**
     * Starts creating the pull request of {@code branch} into {@code home}, which has none: where it is pushed to is
     * checked first, before anything is pushed or asked.
     */
    static Next start(Model model, Branch branch, RemoteName home) {
        return Next.of(model.clearError(), new CheckHead(home, branch.name(), branch.pushTarget()));
    }

    /**
     * Once where it is pushed to was checked: why it cannot be created there, or on to pushing it (ADR 25). The branch
     * may have gone, or become busy, meanwhile; then nothing happens.
     */
    private static Next headChecked(Model model, BranchName branch, Option<CreateError> error) {
        return error.map(why -> Next.of(model.fail(why)))
                .getOrElse(() -> model.idleBranch(branch)
                        .map(known -> NewPullRequestUpdate.askPushed(model, known, new AskPullRequest()))
                        .getOrElse(() -> Next.of(model)));
    }

    /**
     * Asks what the pull request of {@code branch} merges into and what it says, once the title it starts out with
     * is read for the base offered first. With no branch of the home's remote to merge into, there is nothing to ask.
     */
    static Next ask(Model model, Branch branch) {
        return ForgeHome.of(model)
                .map(home -> {
                    List<BranchName> bases = bases(model, home, branch);
                    return bases.headOption()
                            .map(first -> Next.of(
                                    model,
                                    new ReadTitle(
                                            branch.name(),
                                            baseRef(home, first),
                                            new NewPullRequestPopup.Opening(branch.name(), bases))))
                            .getOrElse(() -> Next.of(model.fail(new CreateError.NoBase(branch.name()))));
                })
                .getOrElse(() -> Next.of(model.fail(new CreateError.NoHome())));
    }

    /**
     * The branches of the home's remote {@code home} that a pull request from {@code branch} can merge into (ADR
     * 178): all but the one it is pushed to, its recorded parent first, then the one of the name of the default
     * branch.
     */
    static List<BranchName> bases(Model model, RemoteName home, Branch branch) {
        List<BranchName> names = model.repo()
                .remoteBranches()
                .toOption()
                .getOrElse(List.empty())
                .map(RemoteRef::branch)
                .filter(remote -> remote.remote().equals(home)
                        && !remote.equals(branch.pushTarget())
                        && !remote.branch().equals(REMOTE_HEAD))
                .map(RemoteBranch::branch)
                .distinct();
        Option<BranchName> main = model.repo()
                .branches()
                .toOption()
                .flatMap(branches -> branches.find(Branch::isDefault))
                .map(Branch::name);
        return first(first(names, main), model.repo().parents().get(branch.name()));
    }

    /**
     * {@code bases} with {@code first} first, if it is one of them.
     */
    private static List<BranchName> first(List<BranchName> bases, Option<BranchName> first) {
        return first.filter(bases::contains)
                .map(found -> bases.remove(found).prepend(found))
                .getOrElse(bases);
    }

    /**
     * The branch {@code base} is on the home's remote {@code home}, which a pull request merges into.
     */
    static BranchRef baseRef(RemoteName home, BranchName base) {
        return new BranchRef.Remote(new RemoteBranch(home, base));
    }

    private static Next edited(Model model, String title, String body, boolean draft) {
        return Next.of(model.popup(NewPullRequestPopup.class)
                .map(popup ->
                        model.openPopup(popup.withTitle(title).withBody(body).withDraft(draft)))
                .getOrElse(model));
    }

    /**
     * Creates the pull request the popup describes. A blank title keeps the popup open, as Forgejo needs one.
     */
    private static Next confirmed(Model model) {
        if (model.popup(NewPullRequestPopup.class).exists(popup -> popup.title().isBlank())) {
            return Next.of(model);
        }
        return ForgeHome.of(model)
                .map(home -> PopupUpdate.confirm(
                        model,
                        NewPullRequestPopup.class,
                        popup -> model.repo()
                                .branch(popup.branch())
                                .map(known -> new CreatePullRequest(
                                        home,
                                        popup.branch(),
                                        known.pushTarget(),
                                        popup.base(),
                                        popup.title().strip(),
                                        popup.body(),
                                        popup.draft()))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Done like any operation: the new pull request is the branch's at once, and the pull requests are read again,
     * as after any operation that changes what Forgejo has.
     */
    private static Next created(Model model, CreatePullRequest cmd, ForgejoPullRequest created) {
        return OperationUpdate.succeeded(model, cmd, List.empty())
                .map(done ->
                        done.mapPart(ForgejoPullRequests.PART, prs -> prs.created(cmd.home(), cmd.target(), created)));
    }

    /**
     * Failed as the branch has a pull request open already, which the pull requests read before did not have: they
     * are read again, so that {@code o} shows it next.
     */
    private static Next alreadyOpen(Model model, CreatePullRequest cmd, long number) {
        return OperationUpdate.update(
                        model, new OperationMsg.Failed(cmd, new CreateFailure.AlreadyOpen(cmd.branch(), number)))
                .and(new ReadPullRequests(cmd.home()));
    }
}
