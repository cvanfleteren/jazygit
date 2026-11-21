package net.vanfleteren.jazygit.features.github.stack;

import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Duration;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.core.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.StackLayer;
import net.vanfleteren.jazygit.features.branches.branch.BranchError;
import net.vanfleteren.jazygit.features.branches.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.features.branches.branch.checkout.CheckoutRequest;
import net.vanfleteren.jazygit.features.branches.branch.checkout.CheckoutUpdate;
import net.vanfleteren.jazygit.features.github.prs.PrOverview;
import net.vanfleteren.jazygit.features.github.prs.PullRequestReads;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.AddChosen;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.BottomChosen;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.Changed;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.MakeChosen;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.MenuRequested;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.PickConfirmed;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.StackOnChosen;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.TargetChosen;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.Tick;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.Toggled;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.TopChosen;
import net.vanfleteren.jazygit.features.github.stack.StackMsg.UnstackChosen;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.features.github.state.StackChange;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.BranchBases;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.AheadUpdate;
import net.vanfleteren.jazygit.kernel.state.operation.Notice;
import net.vanfleteren.jazygit.kernel.state.operation.OperationMsg;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.ListCursor;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Opening and closing a stack in the branches pane (ADR 72), showing the stacks or not (ADR 87), and making one
 * (ADR 73). Whether a stack is open, and whether the stacks show, is view state, as the cursors are, and is not kept.
 */
public final class StackUpdate {

    // How long the pull requests are read again after a change to the stacks, while GitHub does not list it yet.
    static final Duration SETTLING = Duration.ofSeconds(30);

    private StackUpdate() {}

    public static Next update(Model model, StackMsg msg) {
        return switch (msg) {
            case Toggled(int stack) -> toggled(model, stack);
            case StackMsg.ViewToggled() -> viewToggled(model);
            case MenuRequested(Option<BranchName> branch, Option<Integer> stack) -> menu(model, branch, stack);
            case MakeChosen() -> make(model);
            case AddChosen() -> add(model);
            case StackMsg.BuiltOnRead(int stack, BranchName top, var eligible, var containing) ->
                builtOnRead(model, stack, top, eligible, containing);
            case StackOnChosen() -> stackOn(model);
            case TargetChosen(StackPopups.Target target) -> stackedOn(model, target);
            case UnstackChosen() -> unstack(model);
            case BottomChosen(int row) -> bottomChosen(model, row);
            case TopChosen(int row) -> topChosen(model, row);
            case PickConfirmed() -> confirmed(model);
            case StackMsg.LinkAndRebaseChosen() -> linkAndRebase(model);
            case Changed(StackCmd cmd, List<ExecutedCommand> commands) -> changed(model, cmd, commands);
            case StackMsg.ChangeFailed(StackCmd cmd, Failure failure) -> changeFailed(model, cmd, failure);
            case Tick() -> settling(model);
            case StackMsg.RebaseChosen() -> rebaseChosen(model);
            case StackMsg.PushChosen() -> pushChosen(model);
            case StackMsg.StackRead(StackRebase rebase, var commits) -> stackRead(model, rebase, commits);
            case StackMsg.StackReadFailed(int stack, var failure) -> stackReadFailed(model, stack, failure);
            case StackMsg.StackBehindRemote(int stack, BranchName branch, var remote) ->
                behindRemote(model, stack, branch, remote);
            case StackMsg.RebaseLocallyChosen() -> rebaseLocally(model);
            case StackMsg.PullRequestMenuRequested(PullRequestSummary pullRequest) ->
                pullRequestMenu(model, pullRequest);
            case StackMsg.CheckoutChosen() -> checkoutChosen(model);
            case StackMsg.StackFetched(var cmd, var commands, StackCheckout checkout) ->
                fetched(model, cmd, commands, checkout);
            case StackMsg.CheckoutConfirmed(StackCheckout.Landing landing) -> checkoutConfirmed(model, landing);
            case StackMsg.CaughtUp(var cmd, var commands) -> caughtUp(model, cmd, commands);
        };
    }

    /**
     * Opens stack {@code stack} in the branches pane, or closes it.
     */
    private static Next toggled(Model model, int stack) {
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
     * Linked as asked, and rebased once GitHub lists the stack (ADR 74).
     */
    private static Next linkAndRebase(Model model) {
        return model.popup(StackPopups.Confirm.class)
                .filter(confirm -> !confirm.diverged().isEmpty())
                .map(confirm -> OperationUpdate.start(
                        model.withoutPopup(StackPopups.Confirm.class).clearError(),
                        confirm.cmd().rebasing()))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Once {@code cmd} changed the stacks: the pull requests are read again until they show the change.
     */
    private static Next changed(Model model, StackCmd cmd, List<ExecutedCommand> commands) {
        return OperationUpdate.succeeded(model, cmd, commands)
                .map(done -> done.mapPart(
                        GitHubState.PART,
                        gitHub -> gitHub.withStackChange(
                                Option.of(cmd.change(model.now().plus(SETTLING))))));
    }

    /**
     * What it changed before it failed shows: the pull requests are read again.
     */
    private static Next changeFailed(Model model, StackCmd cmd, Failure failure) {
        Next failed = OperationUpdate.update(model, new OperationMsg.Failed(cmd, failure));
        return failure.commands().isEmpty() ? failed : PullRequestReads.loadAnyway(failed);
    }

    /**
     * Reads the commits of the stack of the menu, to ask about rebasing it; or says why it is not rebased.
     */
    private static Next rebaseChosen(Model model) {
        return chosen(
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
    }

    /**
     * Pushes the stack of the menu.
     */
    private static Next pushChosen(Model model) {
        return chosen(
                model,
                menu -> menu.stack()
                        .map(stack -> pushed(model.withoutPopup(StackPopups.Menu.class), stack))
                        .getOrElse(() -> Next.of(model)));
    }

    /**
     * Pushed as well unless asked not to: GitHub sees the stack rebased only once it is (ADR 74). The menu is closed by
     * now, so a dialog that is open came up since, as one the user types in while a linked stack settles: it stays,
     * and the user is told to rebase from the menu.
     */
    private static Next stackRead(Model model, StackRebase rebase, List<StackLayer.Commits> commits) {
        return model.popup().isDefined()
                ? Next.of(model.inform(new Notice.StackNotAsked(rebase.stack())))
                : PopupUpdate.ask(
                        model, new StackPopups.RebaseConfirm(new StackRebaseCmd.RebaseStack(rebase, commits, true)));
    }

    /**
     * Says the commits of stack {@code stack} could not be read.
     */
    private static Next stackReadFailed(Model model, int stack, Failure failure) {
        return Next.of(
                model.fail(new AppError.OperationFailed(StackRebaseCmd.REBASE_STACK, Option.of("#" + stack), failure)));
    }

    /**
     * Says {@code branch} is not rebased, as it lacks commits of {@code remote}. A pull brings in what is there only
     * when the branch follows it.
     */
    private static Next behindRemote(Model model, int stack, BranchName branch, RemoteBranch remote) {
        return Next.of(model.fail(new BranchError.StackNotRebased(
                stack,
                branch.value(),
                model.repo().branch(branch).flatMap(Branch::remoteUpstream).contains(remote)
                        ? BranchError.StackRefusal.BEHIND_REMOTE
                        : BranchError.StackRefusal.BEHIND_UNFOLLOWED)));
    }

    /**
     * Rebases the stack asked about without pushing it.
     */
    private static Next rebaseLocally(Model model) {
        return PopupUpdate.confirm(
                model,
                StackPopups.RebaseConfirm.class,
                popup -> Option.of(popup.cmd().locally()));
    }

    /**
     * Fetches the branches of the stack of the menu, to check it out, where that applies.
     */
    private static Next checkoutChosen(Model model) {
        return chosen(
                model,
                menu -> menu.canCheckOut()
                        ? OperationUpdate.start(
                                model.withoutPopup(StackPopups.Menu.class).clearError(),
                                new StackCheckoutCmd.Fetch(
                                        menu.stack().get(),
                                        StackCheckout.remote(model, menu.stack().get()),
                                        menu.on()))
                        : Next.of(model));
    }

    /**
     * Asked about once fetched; not in the place of a dialog opened meanwhile.
     */
    private static Next fetched(
            Model model, StackCheckoutCmd.Fetch cmd, List<ExecutedCommand> commands, StackCheckout checkout) {
        return OperationUpdate.succeeded(model, cmd, commands)
                .map(done -> done.openPopupIfFree(new StackPopups.Checkout(checkout)));
    }

    /**
     * Checks out the stack asked about, landing where {@code landing} says, if it can land there.
     */
    private static Next checkoutConfirmed(Model model, StackCheckout.Landing landing) {
        return model.popup(StackPopups.Checkout.class)
                .map(StackPopups.Checkout::checkout)
                .filter(StackCheckout::startable)
                .filter(checkout -> landing == StackCheckout.Landing.STAY
                        || checkout.target(landing).isDefined())
                .map(checkout -> OperationUpdate.start(
                        model.withoutPopup(StackPopups.Checkout.class).clearError(),
                        new StackCheckoutCmd.CatchUp(checkout, landing)))
                .getOrElse(() -> Next.of(model));
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
                                : Next.of(model.fail(new BranchError.OtherRepository(pullRequest.repository() + "#"
                                        + pullRequest.number().value()))));
    }

    /**
     * Once the branches of a stack are made and fast-forwarded: says what was done, then checks out the branch the
     * user chose, as any checkout of a local branch goes (ADR 68), where the user chose one. One the model knew of
     * before goes as asked for from the branches pane, which switches to the worktree that has it checked out, if
     * another has.
     */
    private static Next caughtUp(Model model, StackCheckoutCmd.CatchUp cmd, List<ExecutedCommand> commands) {
        StackCheckout checkout = cmd.checkout();
        return OperationUpdate.succeeded(model, cmd, commands)
                .then(done -> checkout.target(cmd.landing())
                        .map(branch -> model.repo().branch(branch).isDefined()
                                ? CheckoutUpdate.update(done, new CheckoutMsg.Requested(branch))
                                : CheckoutUpdate.place(done, new CheckoutRequest.Local(branch)))
                        .getOrElse(() -> Next.of(done)))
                // Said once the checkout is started, which clears what was said; not in the place of why it was
                // refused.
                .map(switched -> switched.outcome().error().isDefined()
                        ? switched
                        : switched.inform(new Notice.StackCheckedOut(
                                checkout.stack(),
                                checkout.count(StackCheckout.Kind.MADE),
                                checkout.count(StackCheckout.Kind.FAST_FORWARDED),
                                checkout.kept())));
    }

    /**
     * Pushes the open branches of {@code stack}, all of them or none (ADR 74); or says why not, as when one is not
     * checked out here.
     */
    private static Next pushed(Model model, PullRequestStack stack) {
        return StackPush.of(model, stack)
                .fold(
                        refused -> Next.of(model.fail(
                                new BranchError.StackNotPushed(stack.number(), refused.branch(), refused.why()))),
                        push -> OperationUpdate.start(model.clearError(), new StackRebaseCmd.PushStack(push)));
    }

    /**
     * Reads the pull requests again while they do not show the change made to the stacks yet, until they do or it is
     * given up on: GitHub can list them as they were for a while after it made the change (ADR 73).
     */
    private static Next settling(Model model) {
        GitHubState gitHub = model.part(GitHubState.PART);
        return gitHub.stackChange()
                .map(change -> {
                    Model settled = model.withPart(GitHubState.PART, gitHub.withStackChange(Option.none()));
                    if (gitHub.pullRequests().exists(change::shownIn)) {
                        return change instanceof StackChange.Stacked stacked && stacked.rebase()
                                ? rebaseListed(settled, stacked)
                                : Next.of(settled);
                    }
                    if (!model.now().isAfter(change.until())) {
                        return PullRequestReads.load(Next.of(model));
                    }
                    // A rebase asked for when linking is not done: the user is told, to do it from the menu.
                    return change instanceof StackChange.Stacked stacked && stacked.rebase()
                            ? Next.of(settled.inform(new StackNotice.NotListed(
                                    stacked.pullRequests().head())))
                            : Next.of(settled);
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Rebases the stack that GitHub now lists {@code stacked} in, as was asked when linking it (ADR 74): its commits
     * are read, then asked about; or it says why it is not rebased.
     */
    private static Next rebaseListed(Model model, StackChange.Stacked stacked) {
        return model.part(GitHubState.PART)
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
    private static Option<PullRequestStack> stack(Model model, int number) {
        return model.part(GitHubState.PART)
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

    private static Next chosen(Model model, Function<StackPopups.Menu, Next> then) {
        return model.popup(StackPopups.Menu.class).map(then).getOrElse(() -> Next.of(model));
    }

    private static Predicate<BranchName> root(Model model) {
        return name -> BranchBases.in(model).root(name, AheadUpdate.main(model));
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
                    int stack = menu.stack().get().number();
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
            int stack,
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

    private static Next addPick(Model model, Branch top, int stack, Map<BranchName, BranchName> parents) {
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
        Option<Integer> stack = on.stack().map(PullRequestStack::number);
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
