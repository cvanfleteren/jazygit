package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * Reads the changes of the commit {@code sha}, for the preview whose slot is {@code preview}: the commit detail of
 * the commits pane, of a divergence or of the commits of a branch. Each takes its own slot, so that highlighting
 * a commit in one does not skip the read another one waits for.
 */
public record LoadCommitDetail(Sha sha, DiffOptions options, Slot preview) implements DiffLoad, Targeted<DiffText> {

    /**
     * Reads the changes of the commit {@code sha} highlighted in the commits pane.
     */
    public LoadCommitDetail(Sha sha, DiffOptions options) {
        this(sha, options, Slot.COMMIT_DETAIL);
    }

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.commitDetail", sha.shortSha()));
    }

    @Override
    public Option<Slot> slot() {
        return Option.of(preview);
    }

    /**
     * The detail of the commit in the preview of {@code preview}, while it is the one highlighted there. Every
     * other preview of it shows what was read too.
     */
    @Override
    public Target<DiffText> target() {
        Lens<RepoView, Loadable<DiffText>> commits =
                Preview.lens(RepoView::commitDetail, RepoView::withCommitDetail, sha);
        Lens<RepoView, Loadable<DiffText>> divergence = Lens.some(RepoView::divergence, RepoView::withDivergence)
                .andThen(Preview.lens(DivergenceView::detail, DivergenceView::withDetail, sha));
        Lens<RepoView, Loadable<DiffText>> branchCommits = Lens.some(
                        RepoView::branchCommits, RepoView::withBranchCommits)
                .andThen(Preview.lens(BranchCommitsView::detail, BranchCommitsView::withDetail, sha));
        return Target.inView(
                switch (preview) {
                    case DIVERGENCE_DETAIL -> divergence.alongWith(commits).alongWith(branchCommits);
                    case BRANCH_COMMITS_DETAIL ->
                        branchCommits.alongWith(commits).alongWith(divergence);
                    default -> commits.alongWith(divergence).alongWith(branchCommits);
                });
    }

    @Override
    public Msg run(Env env) {
        return env.git()
                .diffs()
                .commitChanges(sha, options)
                .mapLeft(Failure::of)
                .fold(this::failed, changes -> new LoadMsg.CommitDetailLoaded(sha, options, changes, preview));
    }
}
