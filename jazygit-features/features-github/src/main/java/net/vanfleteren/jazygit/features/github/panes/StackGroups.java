package net.vanfleteren.jazygit.features.github.panes;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.GhBranch;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteRef;
import net.vanfleteren.jazygit.features.github.stack.StackAction;
import net.vanfleteren.jazygit.features.github.state.FromGitHub;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchGroups;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchGroups.Entry;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchGroups.Group;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;

/**
 * The stacks of pull requests on GitHub that have a local branch, as groups of the branches pane (ADR 72, ADR 167):
 * a header with the number and the base of the stack, how many pull requests it has, and whether it diverged (ADR 73);
 * under it its pull requests in GitHub's order, the bottom one first, a local branch where one has it, else a dimmed
 * line saying it is not checked out, merged or closed.
 */
public final class StackGroups {

    private StackGroups() {}

    /**
     * The stacks of {@code branches} in {@code model}, in the order their first branch is listed.
     */
    public static List<Group> of(Model model, List<Branch> branches) {
        GitHubState gitHub = model.part(GitHubState.PART);
        List<Tuple2<Branch, PullRequestStack>> stacked = branches.flatMap(
                branch -> gitHub.pullRequest(branch).flatMap(PullRequest::stack).map(stack -> Tuple.of(branch, stack)));
        return stacked.map(member -> member._2().number())
                .distinct()
                .map(number -> group(
                        model,
                        gitHub,
                        number,
                        stacked.filter(member -> member._2().number() == number)));
    }

    /**
     * The group of stack {@code number}, with {@code members}, its local branches in the order they are listed, each
     * with the stack as it was read with its pull request.
     */
    private static Group group(
            Model model, GitHubState gitHub, int number, List<Tuple2<Branch, PullRequestStack>> members) {
        // The stacks of its pull requests are read with each, and the one that lists the most of them says most.
        PullRequestStack stack =
                members.map(Tuple2::_2).maxBy(read -> read.entries().size()).get();
        List<Entry> listed = stack.entries()
                .sortBy(PullRequestStack.Entry::position)
                .map(entry -> members.find(member -> gitHub.pullRequest(member._1())
                                .exists(pr -> pr.number().equals(entry.number())))
                        .<Entry>map(member -> new Entry.Member(member._1().name()))
                        .getOrElse(() -> elsewhere(model, gitHub, entry)));
        // A branch whose pull request the stack it was read with does not list goes after the ones it does.
        List<Entry> unlisted = members.filter(member ->
                        !listed.exists(entry -> entry instanceof Entry.Member listedMember
                                && listedMember.branch().equals(member._1().name())))
                .sortBy(member -> member._2().position())
                .map(member -> new Entry.Member(member._1().name()));
        List<LoadableList.Seg> diverged = diverged(model, members.map(Tuple2::_1))
                ? List.of(LoadableList.Seg.styled(" " + Messages.get("github.stacks.diverged"), Theme.Classes.ERROR))
                : List.empty();
        return new Group(
                StackAction.groupKey(number),
                List.of(LoadableList.Seg.styled(
                        Messages.get(
                                "github.stacks.header",
                                number,
                                stack.base().map(GhBranch::name).getOrElse("?")),
                        Theme.Classes.PULL_REQUEST)),
                List.of(LoadableList.Seg.styled(
                                " " + Messages.get("github.stacks.size", stack.size()), Theme.Classes.DIM))
                        .appendAll(diverged),
                listed.appendAll(unlisted),
                new BranchGroups.Keys<>(StackAction.STACKS, number));
    }

    /**
     * A pull request of a stack whose branch is not checked out, or that is merged or closed: it shows the log of the
     * remote-tracking branch of its branch, once that is fetched.
     */
    private static Entry elsewhere(Model model, GitHubState gitHub, PullRequestStack.Entry entry) {
        String key = switch (entry.state()) {
            case OPEN -> "github.stacks.notCheckedOut";
            case MERGED -> "github.stacks.merged";
            case CLOSED -> "github.stacks.closed";
        };
        return new Entry.Elsewhere(
                "pullRequest:" + entry.number().value(),
                Messages.get(key, entry.number().value()),
                entry.headBranch().flatMap(head -> fetched(model, gitHub, head)),
                "#" + entry.number().value());
    }

    /**
     * Whether a branch of {@code members}, the local branches of a stack, does not have all the commits of the one
     * below it in the stack (ADR 73). One that is only behind the base of the stack is not: GitHub merges that.
     */
    static boolean diverged(Model model, List<Branch> members) {
        List<BranchName> names = members.map(Branch::name);
        return members.exists(b -> model.repo().parents().get(b.name()).exists(names::contains)
                && model.repo().behindParent(b.name()).isDefined());
    }

    /**
     * The remote-tracking branch on GitHub of {@code head}, the branch a pull request merges from, if it was fetched.
     */
    private static Option<RemoteBranch> fetched(Model model, GitHubState gitHub, GhBranch head) {
        return model.repo()
                .remoteBranches()
                .toOption()
                .getOrElse(List.empty())
                .iterator()
                .map(RemoteRef::branch)
                .find(remote -> remote.branch().equals(FromGitHub.branch(head)) && gitHub.mayBeOnGitHub(remote));
    }
}
