package net.vanfleteren.jazygit.kernel.state.load;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.CommitFile;
import net.vanfleteren.jazygit.core.git.model.DiffOptions;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.core.git.model.Sha;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.view.CommitFilesView;
import net.vanfleteren.jazygit.kernel.state.view.Preview;

/**
 * Reads what a commit, a stash or a pull request changed in the files of the node highlighted in the tree of its
 * files.
 */
public record LoadCommitFileDiff(Changes of, List<CommitFile> files, DiffOptions options)
        implements DiffLoad<DiffText> {

    /**
     * The load of the diff of {@code files} of {@code view}, read with {@code options}.
     */
    public static LoadCommitFileDiff of(CommitFilesView view, List<CommitFile> files, DiffOptions options) {
        return new LoadCommitFileDiff(view.of(), files, options);
    }

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("state.work.diff"));
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
                    case Changes.OfCommit(Sha sha) -> env.git().diffs().commitFileChanges(sha, files, options);
                    case Changes.OfTip(var _, Sha sha) -> env.git().diffs().commitFileChanges(sha, files, options);
                    case Changes.OfStash(Sha sha) -> env.git().stashes().fileChanges(sha, files, options);
                    case Changes.OfBranch(var branch, var base, Sha sha) ->
                        env.git().diffs().rangeFileChanges(base.tip(), sha, files, options);
                    case Changes.OfPullRequest(PullRequestCommits commits) ->
                        env.git().diffs().pullRequestFileChanges(commits, files, options);
                })
                .mapLeft(Failure::of)
                .fold(this::failed, diff -> new LoadMsg.CommitFileDiffLoaded(of, files, options, diff));
    }
}
