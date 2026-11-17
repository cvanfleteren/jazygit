package net.vanfleteren.jazygit.branches.branch.base;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.Comparator;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.branches.branch.BranchError;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.git.model.BaseCandidate;
import net.vanfleteren.jazygit.git.model.BaseCandidates;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.GitHubBases;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.load.AheadUpdate;
import net.vanfleteren.jazygit.state.popup.ListCursor;
import net.vanfleteren.jazygit.state.popup.PopupUpdate;

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
     * protected one are roots (ADR 72), and the base of a branch with an open pull request is what GitHub says, which
     * comes before what is recorded.
     */
    private static Next requested(Model model, BranchName name) {
        return model.idleBranch(name)
                .map(branch -> {
                    if (root(model).test(name)) {
                        return Next.of(model.fail(new BranchError.RootHasNoBase(name)));
                    }
                    Option<PullRequest> pr = model.gitHub().pullRequest(branch);
                    return GitHubBases.of(model.gitHub())
                            .fromPullRequest(branch)
                            .flatMap(base -> pr.map(open -> Next.of(
                                    model.fail(new BranchError.BaseFromPullRequest(name, open.number(), base)))))
                            .getOrElse(() -> Next.of(model.clearError(), new ReadBaseCandidates(name, branch.tip())));
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Offers {@code found} to pick from, as {@link #rank} orders them, unless the branch is gone meanwhile: on the base
     * recorded for the branch, else on the likeliest one.
     */
    private static Next read(Model model, BranchName branch, BaseCandidates found) {
        if (model.repo().branch(branch).isEmpty()) {
            return Next.of(model);
        }
        Option<BranchName> current = model.repo().parents().get(branch);
        Option<BranchName> main = AheadUpdate.main(model);
        Option<BranchName> createdFrom = found.createdFrom()
                .filter(start ->
                        found.candidates().exists(candidate -> candidate.name().equals(start)));
        BasePopup popup =
                new BasePopup(branch, rank(found.candidates(), createdFrom, root(model)), current, createdFrom, main);
        int at = current.map(base -> popup.choices().indexOf(Option.of(base)))
                .filter(index -> index >= 0)
                .getOrElse(0);
        return Next.of(model.clearError().openPopup(popup).withListCursor(ListCursor.START.at(at)));
    }

    /**
     * {@code candidates}, the likeliest base first: the one the reflog says the branch was made from; then the one it
     * forked from last, the one with the fewest of the branch's commits since; of those, the one with the fewest
     * commits of its own, which the branch is most likely built on rather than beside; then a root last, as a branch
     * made from another is more likely built on that one than on the main branch they both forked from.
     *
     * @param root whether a branch is a root: the main branch or a protected one
     */
    static List<BaseCandidate> rank(
            List<BaseCandidate> candidates, Option<BranchName> createdFrom, Predicate<BranchName> root) {
        return candidates.sorted(
                Comparator.comparing((BaseCandidate candidate) -> !createdFrom.contains(candidate.name()))
                        .thenComparingInt(BaseCandidate::own)
                        .thenComparingInt(BaseCandidate::theirs)
                        .thenComparing(candidate -> root.test(candidate.name()))
                        .thenComparing(candidate -> candidate.name().value()));
    }

    /**
     * Records {@code base} for the branch of the popup, unless it is the base the branch has already.
     */
    private static Next chosen(Model model, Option<BranchName> base) {
        return model.popup(BasePopup.class)
                .filter(popup -> popup.current().equals(base))
                .map(popup -> Next.of(model.withoutPopup(BasePopup.class)))
                .getOrElse(() -> PopupUpdate.confirm(
                        model, BasePopup.class, popup -> Option.of(new SetBaseCmd(popup.branch(), base))));
    }

    private static Predicate<BranchName> root(Model model) {
        return name -> GitHubBases.root(
                name,
                AheadUpdate.main(model),
                model.gitHub().protectedBranches().getOrElse(HashSet.empty()));
    }
}
