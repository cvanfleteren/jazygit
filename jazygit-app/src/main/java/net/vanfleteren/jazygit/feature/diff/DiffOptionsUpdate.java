package net.vanfleteren.jazygit.feature.diff;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.DivergenceView;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Preview;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * How the diffs are read. Changing it reads the diffs that are shown again, which keep showing as they were
 * until they are read; what was read before the change is dropped when it arrives.
 */
public final class DiffOptionsUpdate {

    private DiffOptionsUpdate() {}

    public static Next update(Model model, DiffOptionsMsg msg) {
        return switch (msg) {
            // The key works everywhere, but not in a dialog, whose diff does not change.
            case DiffOptionsMsg.WhitespaceToggled() ->
                model.popup().isDefined()
                        ? Next.of(model)
                        : changed(
                                model,
                                model.diffOptions()
                                        .withIgnoreWhitespace(
                                                !model.diffOptions().ignoreWhitespace()));
        };
    }

    private static Next changed(Model model, DiffOptions options) {
        Option<DivergenceView> divergence = model.divergence().map(view -> view.withDetail(reloading(view.detail())));
        List<Cmd> loads = List.<Cmd>empty()
                .appendAll(model.fileDiff().map(diff -> new Cmd.LoadFileDiff(diff.key(), options)))
                .appendAll(model.commitDetail()
                        .toList()
                        .appendAll(model.divergence().flatMap(DivergenceView::detail))
                        .map(detail -> new Cmd.LoadCommitDetail(detail.key(), options)))
                .distinct();
        return new Next(
                model.withDiffOptions(options)
                        .withFileDiff(reloading(model.fileDiff()))
                        .withCommitDetail(reloading(model.commitDetail()))
                        .withDivergence(divergence),
                loads);
    }

    /**
     * {@code preview} loading again, showing what it shows until it is read.
     */
    private static <K, V> Option<Preview<K, V>> reloading(Option<Preview<K, V>> preview) {
        return preview.map(shown -> Preview.loading(shown.key(), Option.of(shown)));
    }
}
