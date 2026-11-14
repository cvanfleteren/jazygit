package net.vanfleteren.jazygit.state.load;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.FileContent;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.state.view.CommitFilesView;
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.state.view.UnchangedNode;

/**
 * Reads what the file at {@code path} holds in the commit the files of {@code of} end at, to show it while it is
 * highlighted among all files of that commit without changes (ADR 128).
 */
public record ReadCommitFileContent(Changes of, String path) implements Targeted<FileContent> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.fileContent", path));
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

    private static Option<Preview<String, FileContent>> content(CommitFilesView view) {
        return view.unchanged()
                .flatMap(node -> node instanceof UnchangedNode.File(var content) ? Option.of(content) : Option.none());
    }

    private static CommitFilesView withContent(CommitFilesView view, Option<Preview<String, FileContent>> content) {
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
