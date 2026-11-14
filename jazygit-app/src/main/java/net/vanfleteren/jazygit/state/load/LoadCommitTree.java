package net.vanfleteren.jazygit.state.load;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.lens.Lens;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.state.view.CommitFilesView;

/**
 * Reads all files of the commit the files of {@code of} end at, with those it changed, for the tree of them the files
 * pane lists while all are asked for (ADR 128). The changed ones are read again with them, so that the two are merged
 * here rather than on the UI thread; those of a pull request in the background, as its files are.
 */
public record LoadCommitTree(Changes of) implements Targeted<List<CommitFile>> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.commitTree", of.sha().shortSha()));
    }

    @Override
    public boolean background() {
        return of instanceof Changes.OfPullRequest;
    }

    /**
     * All files of the view of {@code of}, while it is the one open.
     */
    @Override
    public Target<List<CommitFile>> target() {
        return Target.inView(
                CommitFilesView.open(of).andThen(Lens.some(CommitFilesView::tree, CommitFilesView::withTree)));
    }

    @Override
    public Msg run(Env env) {
        return LoadCommitFiles.changed(env, of)
                .flatMap(changed ->
                        env.git().diffs().treeFiles(of.sha()).map(paths -> CommitFilesView.merged(changed, paths)))
                .mapLeft(Failure::of)
                .fold(this::failed, files -> new LoadMsg.CommitTreeLoaded(of, files));
    }
}
