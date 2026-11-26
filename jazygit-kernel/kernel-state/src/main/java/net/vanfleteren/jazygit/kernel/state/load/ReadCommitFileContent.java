package net.vanfleteren.jazygit.kernel.state.load;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.FileContent;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.view.CommitFilesView;
import net.vanfleteren.jazygit.kernel.state.view.Preview;
import net.vanfleteren.jazygit.kernel.state.view.UnchangedNode;

/**
 * Reads what the file at {@code path} holds in the commit the files of {@code of} end at, to show it while it is
 * highlighted among all files of that commit without changes (ADR 128).
 */
public record ReadCommitFileContent(Changes of, RepoPath path) implements Targeted<FileContent> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("state.work.fileContent", path));
    }

    @Override
    public Option<Slot> slot() {
        return Option.of(Slot.COMMIT_FILE_DIFF);
    }

    /**
     * What the file holds, while it is the one highlighted in the view of {@code of}.
     */
    @Override
    public Target<FileContent> target() {
        return Target.inView(CommitFilesView.open(of)
                .andThen(Preview.lens(ReadCommitFileContent::content, ReadCommitFileContent::withContent, path)));
    }

    private static Option<Preview<RepoPath, FileContent>> content(CommitFilesView view) {
        return view.unchanged()
                .flatMap(node -> node instanceof UnchangedNode.File(var content) ? Option.of(content) : Option.none());
    }

    private static CommitFilesView withContent(CommitFilesView view, Option<Preview<RepoPath, FileContent>> content) {
        return view.withUnchanged(
                content.<UnchangedNode>map(UnchangedNode.File::new).orElse(view.unchanged()));
    }

    @Override
    public Msg run(Env env) {
        return env.git()
                .diffs()
                .treeContent(of.sha(), path)
                .mapLeft(Failure::of)
                .fold(this::failed, content -> new LoadMsg.CommitFileContentRead(of, path, content));
    }
}
