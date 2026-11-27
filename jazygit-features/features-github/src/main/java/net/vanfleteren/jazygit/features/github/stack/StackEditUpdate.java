package net.vanfleteren.jazygit.features.github.stack;

import static net.vanfleteren.jazygit.features.github.stack.StackMenuUpdate.chosen;
import static net.vanfleteren.jazygit.features.github.stack.StackMenuUpdate.local;
import static net.vanfleteren.jazygit.features.github.stack.StackMenuUpdate.root;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.core.gh.model.StackId;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.AddChosen;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.BottomChosen;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.BuiltOnRead;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.MakeChosen;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.PickConfirmed;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.StackOnChosen;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.TargetChosen;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.TopChosen;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.UnstackChosen;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.ListCursor;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Making a stack (ADR 73), adding branches to the top of one and stacking a pull request on another (ADR 148), and
 * taking a stack apart, from the menu of a stack.
 */
final class StackEditUpdate {

    private StackEditUpdate() {}

    static Next update(Model model, StackMsg.Editing msg) {
        return switch (msg) {
            case MakeChosen() -> make(model);
            case AddChosen() -> add(model);
            case BuiltOnRead(StackId stack, BranchName top, var eligible, var containing) ->
                builtOnRead(model, stack, top, eligible, containing);
            case StackOnChosen() -> stackOn(model);
            case TargetChosen(StackPopups.Target target) -> stackedOn(model, target);
            case UnstackChosen() -> unstack(model);
            case BottomChosen(int row) -> bottomChosen(model, row);
            case TopChosen(int row) -> topChosen(model, row);
            case PickConfirmed() -> confirmed(model);
        };
    }

    /**
     * Asks about taking the stack of the menu apart, where that applies.
     */
    private static Next unstack(Model model) {
        return chosen(
                model,
                menu -> menu.canUnstack()
                        ? PopupUpdate.ask(
                                model.withoutPopup(StackPopups.Menu.class),
                                new StackPopups.Unstack(menu.stack().get()))
                        : Next.of(model));
    }

    /**
     * Makes row {@code row} the bottom of the stack being made, or says why it cannot be.
     */
    private static Next bottomChosen(Model model, int row) {
        return picked(
                model,
                pick -> pick.choice()
                        .withBottom(row)
                        .map(pick::withChoice)
                        .getOrElse(() -> pick.withRefused(
                                pick.addTo().isDefined()
                                        ? "github.dialog.addToStack.bottomFixed"
                                        : "github.dialog.makeStack.bottomAbove")));
    }

    /**
     * Makes row {@code row} the top of the stack being made, or says why it cannot be.
     */
    private static Next topChosen(Model model, int row) {
        return picked(
                model,
                pick -> pick.choice()
                        .withTop(row)
                        .map(pick::withChoice)
                        .getOrElse(() -> pick.withRefused("github.dialog.makeStack.topBelow")));
    }

    /**
     * Opens the choice of the branches of a stack to make from the branch of the menu, on that branch.
     */
    private static Next make(Model model) {
        return model.popup(StackPopups.Menu.class)
                .filter(StackPopups.Menu::canMake)
                .map(menu -> {
                    StackChoice choice = StackChoice.of(
                            menu.branch().get(),
                            local(model),
                            model.repo().parents(),
                            root(model),
                            model.part(GitHubState.PART)::pullRequest);
                    return Next.of(model.withoutPopup(StackPopups.Menu.class)
                            .openPopup(new StackPopups.Pick(choice, Option.none(), Option.none()))
                            .withListCursor(ListCursor.START.at(choice.branch())));
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Opens the choice of the branches to put on the top of the stack of the menu, on the top chosen at first. The
     * branches that may be built on its top by their history are read first, where there are any (ADR 148).
     */
    private static Next add(Model model) {
        return model.popup(StackPopups.Menu.class)
                .filter(StackPopups.Menu::canAdd)
                .map(menu -> {
                    Branch top = menu.top().get();
                    StackId stack = menu.stack().get().number();
                    Model closed = model.withoutPopup(StackPopups.Menu.class);
                    Map<BranchName, Sha> eligible = unplaced(model, top);
                    // A branch found can be on one listed above the top by its parent too.
                    Map<BranchName, Sha> listed = StackChoice.above(
                                    top,
                                    local(model),
                                    model.repo().parents(),
                                    model.part(GitHubState.PART)::pullRequest)
                            .rows()
                            .drop(1)
                            .toMap(
                                    row -> row.branch().name(),
                                    row -> row.branch().tip());
                    return eligible.isEmpty()
                            ? addPick(closed, top, stack, model.repo().parents())
                            : Next.of(closed, new ReadBuiltOn(stack, top.name(), top.tip(), eligible, listed));
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * The local branches that may be built on {@code top} by their history, with their tips: those that have no
     * parent, are no root, and are not at its tip (ADR 148).
     */
    private static Map<BranchName, Sha> unplaced(Model model, Branch top) {
        Map<BranchName, BranchName> parents = model.repo().parents();
        Predicate<BranchName> root = root(model);
        return local(model)
                .filter(b -> !atTop(b, top) && !root.test(b.name()) && !parents.containsKey(b.name()))
                .toMap(Branch::name, Branch::tip);
    }

    /**
     * Whether {@code branch} is {@code top}, or another branch at its tip.
     */
    private static boolean atTop(Branch branch, Branch top) {
        return branch.name().equals(top.name()) || branch.tip().equals(top.tip());
    }

    /**
     * Opens the choice of the branches to put on {@code top}, the top of stack {@code stack}, with the branches of
     * {@code eligible} placed by their history as {@code containing} says, unless another popup opened meanwhile.
     */
    private static Next builtOnRead(
            Model model,
            StackId stack,
            BranchName top,
            Set<BranchName> eligible,
            Map<BranchName, List<BranchName>> containing) {
        Map<BranchName, BranchName> parents = model.repo().parents();
        return model.popup().isDefined()
                ? Next.of(model)
                : model.repo()
                        .branch(top)
                        .map(known -> addPick(
                                model, known, stack, parents.merge(StackChoice.byHistory(top, eligible, containing))))
                        .getOrElse(() -> Next.of(model));
    }

    private static Next addPick(Model model, Branch top, StackId stack, Map<BranchName, BranchName> parents) {
        StackChoice choice = StackChoice.above(top, local(model), parents, model.part(GitHubState.PART)::pullRequest);
        return Next.of(model.openPopup(new StackPopups.Pick(choice, Option.none(), Option.of(stack)))
                .withListCursor(ListCursor.START.at(choice.top())));
    }

    /**
     * Asks to create the pull request of {@code branch} on {@code base} and to stack it on the pull request of
     * {@code base} (ADR 148); nothing when {@code base} has none.
     */
    private static Next createFirst(Model model, BranchName branch, Branch base) {
        return model.part(GitHubState.PART)
                .pullRequest(base)
                .map(below -> PopupUpdate.ask(
                        model,
                        new StackPopups.CreateFirst(
                                branch,
                                base.name(),
                                below.number(),
                                below.stack().map(PullRequestStack::number))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Opens the choice of the pull request to stack the one of the branch of the menu on.
     */
    private static Next stackOn(Model model) {
        return chosen(
                model,
                menu -> menu.canStackOn()
                        ? PopupUpdate.ask(
                                model.withoutPopup(StackPopups.Menu.class),
                                new StackPopups.StackOn(menu.branch().get(), menu.pullRequest(), menu.targets()))
                        : Next.of(model));
    }

    /**
     * Stacks the pull request of the branch on the one of {@code target}: after its base becomes the branch of
     * {@code target}, a stack of the two, or on the top of the stack of {@code target}. A branch without one is asked
     * about creating it on {@code target} first (ADR 148).
     */
    private static Next stackedOn(Model model, StackPopups.Target target) {
        return model.popup(StackPopups.StackOn.class)
                .map(popup -> popup.pullRequest()
                        .map(pr -> stackedOn(model, pr, target))
                        .getOrElse(() -> createFirst(
                                model.withoutPopup(StackPopups.StackOn.class),
                                popup.branch().name(),
                                target.branch())))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Stacks {@code pr} on the pull request of {@code target}, as {@link #stackedOn(Model, StackPopups.Target)} says.
     */
    private static Next stackedOn(Model model, PullRequest pr, StackPopups.Target target) {
        PullRequest on = target.pullRequest();
        List<StackChoice.BaseChange> changes = pr.baseBranch().contains(on.headBranch())
                ? List.empty()
                : List.of(new StackChoice.BaseChange(pr.number(), pr.baseBranch(), on.headBranch()));
        Option<StackId> stack = on.stack().map(PullRequestStack::number);
        StackCmd.MakeStack cmd = new StackCmd.MakeStack(
                changes, stack.isDefined() ? List.of(pr.number()) : List.of(on.number(), pr.number()), stack);
        return made(model.withoutPopup(StackPopups.StackOn.class), cmd, List.empty());
    }

    /**
     * Starts {@code cmd} at once when no base changes and no branch {@code diverged}, else asks first.
     */
    private static Next made(Model model, StackCmd.MakeStack cmd, List<StackPopups.Diverged> diverged) {
        return cmd.changes().isEmpty() && diverged.isEmpty()
                ? OperationUpdate.start(model.clearError(), cmd)
                : PopupUpdate.ask(model, new StackPopups.Confirm(cmd, diverged));
    }

    /**
     * The branches from the bottom to the top of {@code choice}, past the bottom, that do not have all the commits of
     * the one below them (ADR 73).
     */
    private static List<StackPopups.Diverged> diverged(Model model, StackChoice choice) {
        List<StackChoice.Row> path = choice.chosen();
        return path.zip(path.drop(1))
                .flatMap(pair -> model.repo()
                        .behindParent(pair._2().branch().name())
                        .map(behind -> new StackPopups.Diverged(
                                pair._2().branch().name(), pair._1().branch().name(), behind)));
    }

    private static Next picked(Model model, UnaryOperator<StackPopups.Pick> change) {
        return model.popup(StackPopups.Pick.class)
                .map(pick -> Next.of(model.openPopup(change.apply(pick))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Makes the stack chosen: at once when no base changes, else after asking. Nothing happens while what is chosen
     * makes no stack, which the popup says.
     */
    private static Next confirmed(Model model) {
        // Adding, a lowest branch without a pull request gets one first (ADR 148).
        Option<Next> creating = model.popup(StackPopups.Pick.class)
                .filter(pick -> pick.addTo().isDefined())
                .flatMap(pick -> pick.choice()
                        .toCreate()
                        .map(row -> createFirst(
                                model.withoutPopup(StackPopups.Pick.class),
                                row.branch().name(),
                                pick.choice().rows().head().branch())));
        return creating.getOrElse(() -> linked(model));
    }

    /**
     * Links the pull requests chosen, as {@link #confirmed} does.
     */
    private static Next linked(Model model) {
        return model.popup(StackPopups.Pick.class)
                .flatMap(pick -> pick.plan()
                        .toOption()
                        .map(plan -> new StackCmd.MakeStack(
                                plan.changes(), plan.pullRequests().map(PullRequest::number), pick.addTo())))
                .map(cmd -> made(
                        model.withoutPopup(StackPopups.Pick.class),
                        cmd,
                        diverged(
                                model, model.popup(StackPopups.Pick.class).get().choice())))
                .getOrElse(() -> Next.of(model));
    }
}
