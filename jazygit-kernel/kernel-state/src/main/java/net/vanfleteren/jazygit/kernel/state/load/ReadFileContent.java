package net.vanfleteren.jazygit.kernel.state.load;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.FileContent;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.view.Preview;
import net.vanfleteren.jazygit.kernel.state.view.RepoView;
import net.vanfleteren.jazygit.kernel.state.view.UnchangedNode;

/**
 * Reads what the file at {@code path} in the working tree holds on disk, to show it while it is highlighted in the
 * files pane without changes (ADR 108).
 */
public record ReadFileContent(String path) implements Targeted<FileContent> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("state.work.fileContent", path));
    }

    @Override
    public Option<Slot> slot() {
        return Option.of(Slot.FILE_DIFF);
    }

    /**
     * What the file holds, while it is the one highlighted.
     */
    @Override
    public Target<FileContent> target() {
        return Target.inView(Preview.lens(ReadFileContent::content, ReadFileContent::withContent, path));
    }

    private static Option<Preview<String, FileContent>> content(RepoView view) {
        return view.files()
                .unchanged()
                .flatMap(node -> node instanceof UnchangedNode.File(var content) ? Option.of(content) : Option.none());
    }

    private static RepoView withContent(RepoView view, Option<Preview<String, FileContent>> content) {
        return view.mapFiles(files -> files.withUnchanged(
                content.<UnchangedNode>map(UnchangedNode.File::new).orElse(files.unchanged())));
    }

    @Override
    public Msg run(Env env) {
        return env.git()
                .status()
                .content(path)
                .mapLeft(Failure::of)
                .fold(this::failed, content -> new LoadMsg.FileContentRead(path, content));
    }
}
