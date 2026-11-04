package net.vanfleteren.jazygit.feature.branch.stack;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.feature.branch.stack.StackMsg.AddChosen;
import net.vanfleteren.jazygit.feature.branch.stack.StackMsg.BottomChosen;
import net.vanfleteren.jazygit.feature.branch.stack.StackMsg.Changed;
import net.vanfleteren.jazygit.feature.branch.stack.StackMsg.MakeChosen;
import net.vanfleteren.jazygit.feature.branch.stack.StackMsg.MenuRequested;
import net.vanfleteren.jazygit.feature.branch.stack.StackMsg.PickConfirmed;
import net.vanfleteren.jazygit.feature.branch.stack.StackMsg.StackOnChosen;
import net.vanfleteren.jazygit.feature.branch.stack.StackMsg.TargetChosen;
import net.vanfleteren.jazygit.feature.branch.stack.StackMsg.Tick;
import net.vanfleteren.jazygit.feature.branch.stack.StackMsg.Toggled;
import net.vanfleteren.jazygit.feature.branch.stack.StackMsg.TopChosen;
import net.vanfleteren.jazygit.feature.branch.stack.StackMsg.UnstackChosen;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.AheadUpdate;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.GitHubBases;
import net.vanfleteren.jazygit.state.ListCursor;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.OperationUpdate;
import net.vanfleteren.jazygit.state.PullRequestsUpdate;
import net.vanfleteren.jazygit.state.StackChange;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Opening and closing a stack in the branches pane (ADR 72), and making one (ADR 73). Whether a stack is open is view
 * state, as the cursors are, and is not kept.
 */
public final class StackUpdate {

    // How long the pull requests are read again after a change to the stacks, while GitHub does not list it yet.
    static final Duration SETTLING = Duration.ofSeconds(30);

    private StackUpdate() {}

    public static Next update(Model model, StackMsg msg) {
        return switch (msg) {
            case Toggled(int stack) ->
                Next.of(model.mapRepoView(repoView -> repoView.withClosedStacks(
                        repoView.closedStacks().contains(stack)
                                ? repoView.closedStacks().remove(stack)
                                : repoView.closedStacks().add(stack))));
            case MenuRequested(Option<BranchName> branch, Option<Integer> stack) -> menu(model, branch, stack);
            case MakeChosen() -> make(model);
            case AddChosen() -> add(model);
            case StackOnChosen() -> stackOn(model);
            case TargetChosen(StackPopups.Target target) -> stackedOn(model, target);
            case UnstackChosen() ->
                chosen(
                        model,
                        menu -> menu.canUnstack()
                                ? Update.ask(
                                        model.withoutPopup(StackPopups.Menu.class),
                                        new StackPopups.Unstack(menu.stack().get()))
                                : Next.of(model));
            case BottomChosen(int row) ->
                picked(
                        model,
                        pick -> pick.choice()
                                .withBottom(row)
                                .map(pick::withChoice)
                                .getOrElse(() -> pick.withRefused(
                                        pick.addTo().isDefined()
                                                ? "dialog.addToStack.bottomFixed"
                                                : "dialog.makeStack.bottomAbove")));
            case TopChosen(int row) ->
                picked(
                        model,
                        pick -> pick.choice()
                                .withTop(row)
                                .map(pick::withChoice)
                                .getOrElse(() -> pick.withRefused("dialog.makeStack.topBelow")));
            case PickConfirmed() -> confirmed(model);
            // Linked as asked, and rebased once GitHub lists the stack (ADR 74).
            case StackMsg.LinkAndRebaseChosen() ->
                model.popup(StackPopups.Confirm.class)
                        .filter(confirm -> !confirm.diverged().isEmpty())
                        .map(confirm -> Update.start(
                                model.withoutPopup(StackPopups.Confirm.class).clearError(),
                                confirm.cmd().rebasing()))
                        .getOrElse(() -> Next.of(model));
            case Changed(StackCmd cmd, List<ExecutedCommand> commands) -> {
                Next done = OperationUpdate.succeeded(model, cmd, commands);
                yield new Next(
                        done.model()
                                .mapGitHub(gitHub -> gitHub.withStackChange(
                                        Option.of(cmd.change(model.now().plus(SETTLING))))),
                        done.cmds());
            }
            case Tick() -> settling(model);
            case StackMsg.RebaseChosen() ->
                chosen(
                        model,
                        menu -> menu.stack()
                                .map(stack -> StackRebase.of(model, stack)
                                        .fold(
                                                refused -> Next.of(model.withoutPopup(StackPopups.Menu.class)
                                                        .fail(refused)),
                                                rebase -> Next.of(
                                                        model.withoutPopup(StackPopups.Menu.class)
                                                                .clearError(),
                                                        new StackRebaseCmd.ReadStack(rebase))))
                                .getOrElse(() -> Next.of(model)));
            case StackMsg.PushChosen() ->
                chosen(
                        model,
                        menu -> menu.stack()
                                .map(stack -> pushed(model.withoutPopup(StackPopups.Menu.class), stack))
                                .getOrElse(() -> Next.of(model)));
            // Pushed as well unless asked not to: GitHub sees the stack rebased only once it is (ADR 74).
            case StackMsg.StackRead(StackRebase rebase, var commits) ->
                Update.ask(model, new StackPopups.RebaseConfirm(new StackRebaseCmd.RebaseStack(rebase, commits, true)));
            case StackMsg.StackReadFailed(int stack, var failure) ->
                Next.of(model.fail(
                        new AppError.OperationFailed(StackRebaseCmd.REBASE_STACK, Option.of("#" + stack), failure)));
            case StackMsg.StackBehindRemote(int stack, BranchName branch) ->
                Next.of(model.fail(
                        new AppError.StackNotRebased(stack, branch.value(), AppError.StackRefusal.BEHIND_REMOTE)));
            case StackMsg.RebaseLocallyChosen() ->
                Update.confirm(
                        model,
                        StackPopups.RebaseConfirm.class,
                        popup -> Option.of(popup.cmd().locally()));
        };
    }

    /**
     * Pushes the open branches of {@code stack} that are checked out here, all of them or none (ADR 74).
     */
    private static Next pushed(Model model, PullRequestStack stack) {
        List<Branch> local = local(model);
        List<BranchName> branches = stack.entries()
                .filter(entry -> entry.state() == State.OPEN)
                .sortBy(PullRequestStack.Entry::position)
                .flatMap(entry ->
                        local.find(b -> model.gitHub().pullRequest(b).exists(pr -> pr.number() == entry.number())))
                .map(Branch::name);
        return branches.isEmpty()
                ? Next.of(model)
                : Update.start(model.clearError(), new StackRebaseCmd.PushStack(stack.number(), branches));
    }

    /**
     * Reads the pull requests again while they do not show the change made to the stacks yet, until they do or it is
     * given up on: GitHub can list them as they were for a while after it made the change (ADR 73).
     */
    private static Next settling(Model model) {
        return model.gitHub()
                .stackChange()
                .map(change -> {
                    Model settled = model.mapGitHub(gitHub -> gitHub.withStackChange(Option.none()));
                    if (model.gitHub().pullRequests().exists(change::shownIn)) {
                        return change instanceof StackChange.Stacked stacked && stacked.rebase()
                                ? rebaseListed(settled, stacked)
                                : Next.of(settled);
                    }
                    return model.now().isAfter(change.until())
                            ? Next.of(settled)
                            : PullRequestsUpdate.load(Next.of(model));
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Rebases the stack that GitHub now lists {@code stacked} in, as was asked when linking it (ADR 74): its commits
     * are read, then asked about; or it says why it is not rebased.
     */
    private static Next rebaseListed(Model model, StackChange.Stacked stacked) {
        return model.gitHub()
                .pullRequests()
                .flatMap(stacked::in)
                .map(stack -> StackRebase.of(model, stack)
                        .fold(
                                refused -> Next.of(model.fail(refused)),
                                rebase -> Next.of(model, new StackRebaseCmd.ReadStack(rebase))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * The menu of the stack of {@code branch}, or of {@code stack}; nothing for a branch that is not listed, or when
     * nothing of it applies.
     */
    private static Next menu(Model model, Option<BranchName> branch, Option<Integer> stack) {
        Option<Branch> highlighted = branch.flatMap(model.repo()::branch);
        if (branch.isDefined() && highlighted.isEmpty()) {
            return Next.of(model);
        }
        Option<PullRequestStack> stacked = highlighted
                .flatMap(model.gitHub()::pullRequest)
                .flatMap(PullRequest::stack)
                .orElse(() -> stack.flatMap(number -> stack(model, number)));
        List<Branch> local = local(model);
        Option<PullRequest> pullRequest = highlighted.flatMap(model.gitHub()::pullRequest);
        StackPopups.Menu menu = new StackPopups.Menu(
                highlighted,
                pullRequest,
                stacked,
                stacked.flatMap(on -> top(model, local, on)),
                highlighted.exists(b -> root(model).test(b.name())),
                highlighted.map(b -> targets(model, local, b)).getOrElse(List.empty()));
        // Nothing opens where nothing applies, as on a root.
        return Update.ask(model, Option.of(menu).filter(StackPopups.Menu::anyApplies));
    }

    /**
     * Stack {@code number} as a pull request of it with a local branch has it.
     */
    private static Option<PullRequestStack> stack(Model model, int number) {
        return model.gitHub()
                .pullRequests()
                .toList()
                .flatMap(open -> open.values())
                .flatMap(PullRequest::stack)
                .filter(stack -> stack.number() == number)
                .maxBy(stack -> stack.entries().size());
    }

    private static List<Branch> local(Model model) {
        return model.repo().branches().toOption().getOrElse(List.empty());
    }

    /**
     * The local branch of the top open pull request of {@code stack}, if it is checked out.
     */
    private static Option<Branch> top(Model model, List<Branch> local, PullRequestStack stack) {
        return stack.entries()
                .filter(entry -> entry.state() == State.OPEN)
                .maxBy(PullRequestStack.Entry::position)
                .flatMap(entry ->
                        local.find(b -> model.gitHub().pullRequest(b).exists(pr -> pr.number() == entry.number())));
    }

    /**
     * The branches whose pull request the one of {@code branch} can be stacked on: other branches that are no roots,
     * with an open pull request in no stack or on the top of one.
     */
    private static List<StackPopups.Target> targets(Model model, List<Branch> local, Branch branch) {
        Predicate<BranchName> root = root(model);
        return local.filter(b -> !b.name().equals(branch.name()) && !root.test(b.name()))
                .flatMap(b -> model.gitHub()
                        .pullRequest(b)
                        .filter(pr -> pr.stack().forAll(StackUpdate::isTop))
                        .map(pr -> new StackPopups.Target(b, pr)));
    }

    private static boolean isTop(PullRequestStack stack) {
        return stack.entries().forAll(entry -> entry.position() <= stack.position());
    }

    private static Next chosen(Model model, Function<StackPopups.Menu, Next> then) {
        return model.popup(StackPopups.Menu.class).map(then).getOrElse(() -> Next.of(model));
    }

    private static Predicate<BranchName> root(Model model) {
        return name -> GitHubBases.root(
                name,
                AheadUpdate.main(model),
                model.gitHub().protectedBranches().getOrElse(HashSet.empty()));
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
                            model.gitHub()::pullRequest);
                    return Next.of(model.withoutPopup(StackPopups.Menu.class)
                            .openPopup(new StackPopups.Pick(choice, Option.none(), Option.none()))
                            .withListCursor(ListCursor.START.at(choice.branch())));
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Opens the choice of the branches to put on the top of the stack of the menu, on its top.
     */
    private static Next add(Model model) {
        return model.popup(StackPopups.Menu.class)
                .filter(StackPopups.Menu::canAdd)
                .map(menu -> {
                    StackChoice choice = StackChoice.above(
                            menu.top().get(), local(model), model.repo().parents(), model.gitHub()::pullRequest);
                    return Next.of(model.withoutPopup(StackPopups.Menu.class)
                            .openPopup(new StackPopups.Pick(
                                    choice,
                                    Option.none(),
                                    Option.of(menu.stack().get().number())))
                            .withListCursor(ListCursor.START.at(0)));
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Opens the choice of the pull request to stack the one of the branch of the menu on.
     */
    private static Next stackOn(Model model) {
        return chosen(
                model,
                menu -> menu.canStackOn()
                        ? Update.ask(
                                model.withoutPopup(StackPopups.Menu.class),
                                new StackPopups.StackOn(
                                        menu.branch().get(), menu.pullRequest().get(), menu.targets()))
                        : Next.of(model));
    }

    /**
     * Stacks the pull request of the branch on the one of {@code target}: after its base becomes the branch of
     * {@code target}, a stack of the two, or on the top of the stack of {@code target}.
     */
    private static Next stackedOn(Model model, StackPopups.Target target) {
        return model.popup(StackPopups.StackOn.class)
                .map(popup -> {
                    PullRequest pr = popup.pullRequest();
                    PullRequest on = target.pullRequest();
                    List<StackChoice.BaseChange> changes = pr.baseBranch().equals(on.headBranch())
                            ? List.empty()
                            : List.of(new StackChoice.BaseChange(pr.number(), pr.baseBranch(), on.head()));
                    Option<Integer> stack = on.stack().map(PullRequestStack::number);
                    StackCmd.MakeStack cmd = new StackCmd.MakeStack(
                            changes,
                            stack.isDefined() ? List.of(pr.number()) : List.of(on.number(), pr.number()),
                            stack);
                    return made(model.withoutPopup(StackPopups.StackOn.class), cmd, List.empty());
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Starts {@code cmd} at once when no base changes and no branch {@code diverged}, else asks first.
     */
    private static Next made(Model model, StackCmd.MakeStack cmd, List<StackPopups.Diverged> diverged) {
        return cmd.changes().isEmpty() && diverged.isEmpty()
                ? Update.start(model.clearError(), cmd)
                : Update.ask(model, new StackPopups.Confirm(cmd, diverged));
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
