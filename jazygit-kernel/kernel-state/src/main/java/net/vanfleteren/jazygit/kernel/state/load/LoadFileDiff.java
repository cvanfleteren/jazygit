package net.vanfleteren.jazygit.kernel.state.load;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.DiffOptions;
import net.vanfleteren.jazygit.core.git.model.Diffs;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.MetadataStamp;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.view.Preview;
import net.vanfleteren.jazygit.kernel.state.view.PreviewView;
import net.vanfleteren.jazygit.kernel.state.view.RepoView;

public record LoadFileDiff(List<FileEntry> files, DiffOptions options) implements DiffLoad<Diffs> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("state.work.diff"));
    }

    @Override
    public Option<Slot> slot() {
        return Option.of(Slot.FILE_DIFF);
    }

    /**
     * The diff of the files, while they are the ones highlighted.
     */
    @Override
    public Target<Diffs> target() {
        return Target.inView(
                RepoView.PREVIEW.andThen(Preview.lens(PreviewView::fileDiff, PreviewView::withFileDiff, files)));
    }

    /**
     * Reads the diff, after a stamp of what it is read from: a change while it is read then shows in the next
     * stamp, and the diff is read again (ADR 58).
     */
    @Override
    public Msg run(Env env) {
        Option<MetadataStamp> stamp = env.git().status().diffStamp(files);
        return env.git()
                .diffs()
                .uncommitted(files, options)
                .mapLeft(Failure::of)
                .fold(this::failed, diff -> new LoadMsg.FileDiffLoaded(files, options, diff, stamp));
    }
}
