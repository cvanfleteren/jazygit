package net.vanfleteren.jazygit.features.github.stack;

import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.function.Function;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.core.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.core.gh.model.StackId;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.features.github.GitHubError;
import net.vanfleteren.jazygit.features.github.prs.PrOverview;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.MenuRequested;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.PullRequestMenuRequested;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.Toggled;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.ViewToggled;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.kernel.state.BranchBases;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.AheadUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Opening and closing a stack in the branches pane and showing the stacks or not (ADR 72), and the menu of a stack
 * (ADR 73, 99). Whether a stack is open, and whether the stacks show, is view state, as the cursors are, and is not
 * kept. The other stack updates act on the menu this one opens.
 */
final class StackMenuUpdate {

    private StackMenuUpdate() {}

    static Next update(Model model, StackMsg.Showing msg) {
        return switch (msg) {
            case Toggled(StackId stack) -> toggled(model, stack);
            case ViewToggled() -> viewToggled(model);
            case MenuRequested(Option<BranchName> branch, Option<StackId> stack) -> menu(model, branch, stack);
            case PullRequestMenuRequested(PullRequestSummary pullRequest) -> pullRequestMenu(model, pullRequest);
        };
    }

    /**
     * What {@code then} does with the menu of a stack, if it is open.
     */
    static Next chosen(Model model, Function<StackPopups.Menu, Next> then) {
        return model.popup(StackPopups.Menu.class).map(then).getOrElse(() -> Next.of(model));
    }

    /**
     * The local branches, none while they are not read.
     */
    static List<Branch> local(Model model) {
        return model.repo().branches().toOption().getOrElse(List.empty());
    }

    // What is stated is composed once, not for each branch a sort compares.
    static Predicate<BranchName> root(Model model) {
        BranchBases bases = BranchBases.in(model);
        Option<BranchName> main = AheadUpdate.main(model);
        return name -> bases.root(name, main);
    }

    /**
     * Opens stack {@code stack} in the branches pane, or closes it.
     */
    private static Next toggled(Model model, StackId stack) {
        String group = StackAction.groupKey(stack);
        return Next.of(model.mapRepoView(repoView -> repoView.mapBranches(branches -> branches.withClosedGroups(
                branches.closedGroups().contains(group)
                        ? branches.closedGroups().remove(group)
                        : branches.closedGroups().add(group)))));
    }

    /**
     * Shows the stacks in the branches pane, or lists their branches as any other.
     */
    private static Next viewToggled(Model model) {
        return Next.of(model.mapRepoView(
                repoView -> repoView.mapBranches(branches -> branches.withGrouped(!branches.grouped()))));
    }

    /**
     * The menu of the stack of {@code pullRequest}, highlighted on the pull requests screen (ADR 99): only for one of
     * this repository, and in a stack.
     */
    private static Next pullRequestMenu(Model model, PullRequestSummary pullRequest) {
        if (pullRequest.stack().isEmpty()) {
            return Next.of(model);
        }
        PullRequestStack stack = pullRequest.stack().get();
        return PrOverview.ofThisRepository(model, pullRequest)
                .fold(
                        unknown -> Next.of(model.fail(unknown)),
                        ofThis -> ofThis
                                ? PopupUpdate.ask(
                                        model,
                                        new StackPopups.Menu(
                                                Option.none(),
                                                Option.none(),
                                                Option.of(stack),
                                                top(model, local(model), stack),
                                                false,
                                                List.empty(),
                                                Option.of(pullRequest.number()),
                                                StackCheckout.status(model, stack)))
                                : Next.of(model.fail(new GitHubError.OtherRepository(
                                        pullRequest.repository().value() + "#"
                                                + pullRequest.number().value()))));
    }

    /**
     * The menu of the stack of {@code branch}, or of {@code stack}; nothing for a branch that is not listed, or when
     * nothing of it applies.
     */
    private static Next menu(Model model, Option<BranchName> branch, Option<StackId> stack) {
        Option<Branch> highlighted = branch.flatMap(model.repo()::branch);
        if (branch.isDefined() && highlighted.isEmpty()) {
            return Next.of(model);
        }
        Option<PullRequest> pullRequest = highlighted.flatMap(model.part(GitHubState.PART)::pullRequest);
        Option<PullRequestStack> stacked =
                pullRequest.flatMap(PullRequest::stack).orElse(() -> stack.flatMap(number -> stack(model, number)));
        List<Branch> local = local(model);
        StackPopups.Menu menu = new StackPopups.Menu(
                highlighted,
                pullRequest,
                stacked,
                stacked.flatMap(on -> top(model, local, on)),
                highlighted.exists(b -> root(model).test(b.name())),
                highlighted.map(b -> targets(model, local, b)).getOrElse(List.empty()),
                pullRequest.map(PullRequest::number),
                stacked.map(on -> StackCheckout.status(model, on)).getOrElse(StackCheckout.Status.NOTHING));
        // Nothing opens where nothing applies, as on a root.
        return PopupUpdate.ask(model, Option.of(menu).filter(StackPopups.Menu::anyApplies));
    }

    /**
     * Stack {@code number} as a pull request of it with a local branch has it.
     */
    private static Option<PullRequestStack> stack(Model model, StackId number) {
        return model.part(GitHubState.PART)
                .pullRequests()
                .toList()
                .flatMap(open -> open.values())
                .flatMap(PullRequest::stack)
                .filter(stack -> stack.number().equals(number))
                .maxBy(stack -> stack.entries().size());
    }

    /**
     * The local branch of the top open pull request of {@code stack}, if it is checked out.
     */
    private static Option<Branch> top(Model model, List<Branch> local, PullRequestStack stack) {
        return stack.entries()
                .filter(entry -> entry.state() == State.OPEN)
                .maxBy(PullRequestStack.Entry::position)
                .flatMap(entry -> StackPush.checkedOut(model, entry));
    }

    /**
     * The branches whose pull request the one of {@code branch} can be stacked on: other branches that are no roots,
     * with an open pull request in no stack or on the top of one, and not built on {@code branch}, which would make a
     * pull request the base of its own base. Its parent comes first (ADR 148).
     */
    private static List<StackPopups.Target> targets(Model model, List<Branch> local, Branch branch) {
        Predicate<BranchName> root = root(model);
        Option<BranchName> parent = model.repo().parents().get(branch.name());
        return local.filter(b -> !b.name().equals(branch.name())
                        && !root.test(b.name())
                        && !builtOn(model, b.name(), branch.name()))
                .flatMap(b -> model.part(GitHubState.PART)
                        .pullRequest(b)
                        .filter(pr -> pr.stack().forAll(PullRequestStack::isTop))
                        .map(pr -> new StackPopups.Target(b, pr)))
                .sortBy(target -> !parent.contains(target.branch().name()));
    }

    /**
     * Whether {@code branch} is built on {@code below}, through its parent, or its parent's, and so on (ADR 72).
     */
    private static boolean builtOn(Model model, BranchName branch, BranchName below) {
        Map<BranchName, BranchName> parents = model.repo().parents();
        return Iterator.iterate(parents.get(branch), parent -> parent.flatMap(parents::get))
                .take(parents.size())
                .takeWhile(Option::isDefined)
                .exists(parent -> parent.contains(below));
    }
}
