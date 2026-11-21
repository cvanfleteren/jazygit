package net.vanfleteren.jazygit.features.github.remarks;

import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.repo.PullRequestCommits;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Part;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.diff.StatedRemarks;
import net.vanfleteren.jazygit.kernel.state.view.CommitFilesView;

/**
 * GitHub's part of the review threads of the pull request whose files are listed (ADR 55, 171): it keeps what it read
 * of them, and states them for the kernel to place and draw.
 *
 * @param open the review threads of the pull request whose files were opened last, if they were of one
 */
public record RemarksState(Option<PrRemarks> open) {

    public static final RemarksState NONE = new RemarksState(Option.none());

    /**
     * The part, which states the remarks of the files of the pull request it read the threads of.
     */
    public static final Part<RemarksState> PART =
            Part.of("github.remarks", NONE).remarking(RemarksState::remarks);

    /**
     * What these state for {@code changes}: the threads they read, when those are the files of their pull request.
     */
    static Option<StatedRemarks> remarks(RemarksState state, Changes changes) {
        return changes instanceof Changes.OfPullRequest(PullRequestCommits commits)
                ? state.open
                        .filter(read -> read.number().value() == commits.number())
                        .map(read -> read.stated(commits))
                : Option.none();
    }

    /**
     * The files of the pull request listed in {@code model}, with the review threads read of it, if those are listed.
     */
    public static Option<PrFiles> files(Model model) {
        return model.repoView().files().commitFiles().flatMap(view -> files(model, view));
    }

    /**
     * The files {@code view} lists in {@code model}, with the review threads read of their pull request, if they are
     * of one.
     */
    static Option<PrFiles> files(Model model, CommitFilesView view) {
        return view.of() instanceof Changes.OfPullRequest(PullRequestCommits commits)
                ? model.part(PART)
                        .open
                        .filter(read -> read.number().value() == commits.number())
                        .map(read -> new PrFiles(view, commits, read))
                : Option.none();
    }

    /**
     * {@code model} with the review threads of pull request {@code number} as {@code f} makes them, while they are
     * kept.
     */
    public static Model mapPullRequest(Model model, PrNumber number, Function<PrRemarks, PrRemarks> f) {
        return model.mapPart(
                PART,
                state -> new RemarksState(state.open.map(read -> read.number().equals(number) ? f.apply(read) : read)));
    }

    /**
     * {@code model} with the review threads of the pull request whose files were opened last as {@code f} makes them.
     */
    public static Model mapOpen(Model model, Function<PrRemarks, PrRemarks> f) {
        return model.mapPart(PART, state -> new RemarksState(state.open.map(f)));
    }

    /**
     * {@code model} with the files of the pull request listed as {@code f} makes them, while they are listed: what
     * the kernel keeps of them, as which threads are collapsed.
     */
    public static Model mapView(Model model, Function<CommitFilesView, CommitFilesView> f) {
        return model.mapRepoView(repoView -> repoView.mapFiles(files -> files.withCommitFiles(
                files.commitFiles().map(view -> view.of() instanceof Changes.OfPullRequest ? f.apply(view) : view))));
    }
}
