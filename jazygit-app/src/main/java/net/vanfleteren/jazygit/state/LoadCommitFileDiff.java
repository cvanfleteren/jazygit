package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * Reads what a commit, a stash or a pull request changed in the files of the node highlighted in the tree of its
 * files.
 */
public record LoadCommitFileDiff(CommitFilesView.Changes of, List<CommitFile> files, DiffOptions options)
        implements DiffLoad, Targeted<DiffText> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.diff"));
    }

    @Override
    public Option<Slot> slot() {
        return Option.of(Slot.COMMIT_FILE_DIFF);
    }

    /**
     * The diff in the view of {@code of}, while it is the one open, and {@code files} the node highlighted in it.
     */
    @Override
    public Target<DiffText> target() {
        return Target.inView(CommitFilesView.open(of)
                .andThen(Preview.lens(CommitFilesView::diff, CommitFilesView::withDiff, files)));
    }

    @Override
    public Msg run(Env env) {
        return (switch (of) {
                    case CommitFilesView.Changes.OfCommit(Sha sha) ->
                        env.git().diffs().commitFileChanges(sha, files, options);
                    case CommitFilesView.Changes.OfStash(Sha sha) ->
                        env.git().stashes().fileChanges(sha, files, options);
                    case CommitFilesView.Changes.OfBranch(var branch, var base, Sha sha) ->
                        env.git().diffs().rangeFileChanges(base.tip(), sha, files, options);
                    case CommitFilesView.Changes.OfPullRequest(PullRequestCommits commits) ->
                        env.git().diffs().pullRequestFileChanges(commits, files, options);
                })
                .mapLeft(Failure::of)
                .fold(this::failed, diff -> new LoadMsg.CommitFileDiffLoaded(of, files, options, diff));
    }
}
