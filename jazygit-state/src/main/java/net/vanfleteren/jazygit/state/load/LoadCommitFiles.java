package net.vanfleteren.jazygit.state.load;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.lens.Lens;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.state.view.CommitFilesView;

/**
 * Reads the files a commit, a stash, a pull request or a branch changed, for the tree of them the files pane lists. Those
 * of a pull request are read in the background, as its commits may have to be fetched first (ADR 55).
 */
public record LoadCommitFiles(Changes of) implements Targeted<List<CommitFile>> {

    /**
     * The load of the files {@code view} lists.
     */
    public static LoadCommitFiles of(CommitFilesView view) {
        return new LoadCommitFiles(view.of());
    }

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("state.work.commitFiles", of.sha().shortSha()));
    }

    /**
     * Only the files of the view open are kept: a newer read is of another one, and cancels this one (ADR 141).
     */
    @Override
    public Option<Cmd.Slot> slot() {
        return Option.of(Cmd.Slot.COMMIT_FILES);
    }

    @Override
    public boolean background() {
        return of instanceof Changes.OfPullRequest;
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
        return changed(env, of)
                .mapLeft(Failure::of)
                .fold(this::failed, files -> new LoadMsg.CommitFilesLoaded(of, files));
    }

    /**
     * The files {@code of} changed.
     */
    static Either<GitError, List<CommitFile>> changed(Env env, Changes of) {
        return switch (of) {
            case Changes.OfCommit(Sha sha) -> env.git().diffs().commitFiles(sha);
            case Changes.OfTip(var _, Sha sha) -> env.git().diffs().commitFiles(sha);
            case Changes.OfStash(Sha sha) -> env.git().stashes().files(sha);
            case Changes.OfBranch(var branch, var base, Sha sha) ->
                env.git().diffs().rangeFiles(base.tip(), sha);
            case Changes.OfPullRequest(PullRequestCommits commits) ->
                env.git().remotes().pullRequestFiles(commits);
        };
    }
}
