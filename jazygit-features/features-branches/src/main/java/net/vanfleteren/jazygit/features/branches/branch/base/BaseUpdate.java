package net.vanfleteren.jazygit.features.branches.branch.base;

import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.Comparator;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.core.git.model.BaseCandidate;
import net.vanfleteren.jazygit.core.git.model.BaseCandidates;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.features.branches.branch.BranchError;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.BranchBases;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.AheadUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.ListCursor;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Setting the branch a branch is built on, its parent, in {@code branch.<name>.gh-merge-base} (ADR 143): the
 * branches it could be built on are read, ranked by how likely each is, and offered to pick from.
 */
public final class BaseUpdate {

    private BaseUpdate() {}

    public static Next update(Model model, BaseMsg msg) {
        return switch (msg) {
            case BaseMsg.Requested(BranchName branch) -> requested(model, branch);
            case BaseMsg.CandidatesRead(BranchName branch, BaseCandidates found) -> read(model, branch, found);
            case BaseMsg.CandidatesFailed(BranchName branch, var failure) ->
                Next.of(model.fail(
                        new AppError.OperationFailed(SetBaseCmd.SET_BASE, Option.of(branch.value()), failure)));
            case BaseMsg.Chosen(Option<BranchName> base) -> chosen(model, base);
        };
    }

    /**
     * Reads what {@code branch} could be built on, unless it cannot have a base it is told: the main branch and a
     * protected one are roots (ADR 72), and the base a feature states, as GitHub's of a branch with an open pull
     * request, comes before what is recorded (ADR 143, 169).
     */
    private static Next requested(Model model, BranchName name) {
        return model.idleBranch(name)
                .map(branch -> {
                    if (root(model).test(name)) {
                        return Next.of(model.fail(new BranchError.RootHasNoBase(name)));
                    }
                    return BranchBases.in(model)
                            .of(branch)
                            .map(stated ->
                                    Next.of(model.fail(new BranchError.BaseStated(name, stated.base(), stated.by()))))
                            .getOrElse(() -> Next.of(model.clearError(), new ReadBaseCandidates(name, branch.tip())));
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Offers {@code found} to pick from, as {@link #rank} orders them, unless the branch is gone meanwhile: on the base
     * recorded for the branch, else on the likeliest one. A branch built on this one, through its parents, is not
     * offered, also when it no longer has the branch's tip, as the branch moved on since: it would make a cycle.
     */
    private static Next read(Model model, BranchName branch, BaseCandidates found) {
        if (model.repo().branch(branch).isEmpty()) {
            return Next.of(model);
        }
        Map<BranchName, BranchName> parents = model.repo().parents();
        List<BaseCandidate> candidates =
                found.candidates().filter(candidate -> !builtOn(parents, candidate.name(), branch));
        Option<BranchName> current = parents.get(branch);
        Option<BranchName> main = AheadUpdate.main(model);
        Option<BranchName> createdFrom = found.createdFrom()
                .filter(start -> candidates.exists(candidate -> candidate.name().equals(start)));
        BasePopup popup = new BasePopup(branch, rank(candidates, createdFrom, root(model)), current, createdFrom, main);
        int at = current.map(base -> popup.choices().indexOf(Option.of(base)))
                .filter(index -> index >= 0)
                .getOrElse(0);
        return Next.of(model.clearError().openPopup(popup).withListCursor(ListCursor.START.at(at)));
    }

    /**
     * {@code candidates}, the likeliest base first: the one the reflog says the branch was made from; then the one it
     * forked from last, which the branch is the fewest commits ahead of; of those, the one it is the fewest behind, the
     * candidate with the fewest commits of its own, which the branch is most likely built on rather than beside; then a
     * root last, as a branch made from another is more likely built on that one than on the main branch they both
     * forked from.
     *
     * @param root whether a branch is a root: the main branch or a protected one
     */
    static List<BaseCandidate> rank(
            List<BaseCandidate> candidates, Option<BranchName> createdFrom, Predicate<BranchName> root) {
        return candidates.sorted(
                Comparator.comparing((BaseCandidate candidate) -> !createdFrom.contains(candidate.name()))
                        .thenComparingInt(BaseCandidate::ahead)
                        .thenComparingInt(BaseCandidate::behind)
                        .thenComparing(candidate -> root.test(candidate.name()))
                        .thenComparing(candidate -> candidate.name().value()));
    }

    /**
     * Records {@code base} for the branch of the popup, unless it is the branch the base is already. None is always
     * recorded, which removes what is: the base the model has leaves out one recorded that is gone, which would come
     * back as the base with a branch of that name; removing what is not recorded does nothing.
     */
    private static Next chosen(Model model, Option<BranchName> base) {
        return model.popup(BasePopup.class)
                .filter(popup -> base.isDefined() && popup.current().equals(base))
                .map(popup -> Next.of(model.withoutPopup(BasePopup.class)))
                .getOrElse(() -> PopupUpdate.confirm(
                        model, BasePopup.class, popup -> Option.of(new SetBaseCmd(popup.branch(), base))));
    }

    /**
     * Whether {@code candidate} is built on {@code branch}, through its parent, or its parent's, and so on, as
     * {@code parents} has them (ADR 72); a cycle among them ends the walk.
     */
    static boolean builtOn(Map<BranchName, BranchName> parents, BranchName candidate, BranchName branch) {
        return Iterator.iterate(parents.get(candidate), parent -> parent.flatMap(parents::get))
                .take(parents.size())
                .takeWhile(Option::isDefined)
                .exists(parent -> parent.contains(branch));
    }

    // What is stated is composed once, not for each branch a sort compares.
    private static Predicate<BranchName> root(Model model) {
        BranchBases bases = BranchBases.in(model);
        Option<BranchName> main = AheadUpdate.main(model);
        return name -> bases.root(name, main);
    }
}
