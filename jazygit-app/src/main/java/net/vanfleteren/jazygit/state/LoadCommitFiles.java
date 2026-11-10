package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * Reads the files a commit, a stash, a pull request or a branch changed, for the tree of them the files pane lists. Those
 * of a pull request are read in the background, as its commits may have to be fetched first (ADR 55).
 */
public record LoadCommitFiles(CommitFilesView.Changes of) implements Targeted<List<CommitFile>> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.commitFiles", of.sha().shortSha()));
    }

    @Override
    public boolean background() {
        return of instanceof CommitFilesView.Changes.OfPullRequest;
    }

    /**
     * The files of the view of {@code of}, while it is the one open.
     */
    @Override
    public Target<List<CommitFile>> target() {
        return Target.inView(
                CommitFilesView.open(of).andThen(Lens.of(CommitFilesView::files, CommitFilesView::withFiles)));
    }

    @Override
    public Msg run(Env env) {
        return (switch (of) {
                    case CommitFilesView.Changes.OfCommit(Sha sha) ->
                        env.git().diffs().commitFiles(sha);
                    case CommitFilesView.Changes.OfStash(Sha sha) ->
                        env.git().stashes().files(sha);
                    case CommitFilesView.Changes.OfBranch(var branch, var base, Sha sha) ->
                        env.git().diffs().rangeFiles(base.tip(), sha);
                    case CommitFilesView.Changes.OfPullRequest(PullRequestCommits commits) ->
                        env.git().remotes().pullRequestFiles(commits);
                })
                .mapLeft(Failure::of)
                .fold(this::failed, files -> new LoadMsg.CommitFilesLoaded(of, files));
    }
}
