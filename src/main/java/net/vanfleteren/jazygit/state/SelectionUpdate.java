package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Cmd.LoadBranchLog;
import net.vanfleteren.jazygit.state.Cmd.LoadCommitDetail;
import net.vanfleteren.jazygit.state.Cmd.LoadFileDiff;
import net.vanfleteren.jazygit.state.SelectionMsg.BranchSelected;
import net.vanfleteren.jazygit.state.SelectionMsg.CommitSelected;
import net.vanfleteren.jazygit.state.SelectionMsg.FilesSelected;
import net.vanfleteren.jazygit.state.Update.Next;

import java.util.function.Function;

/**
 * The panes' highlights, and the data loaded for them.
 */
public final class SelectionUpdate {

    private SelectionUpdate() {
    }

    public static Next update(Model model, SelectionMsg msg) {
        return switch (msg) {
            case BranchSelected(var branch) ->
                    selected(model, branch, model.branchLog(), model::withBranchLog, LoadBranchLog::new);
            case CommitSelected(var sha) ->
                    selected(model, sha, model.commitDetail(), model::withCommitDetail, LoadCommitDetail::new);
            case FilesSelected(var files) ->
                    selected(model, files, model.fileDiff(), model::withFileDiff, LoadFileDiff::new);
        };
    }

    /**
     * Loads the preview of {@code key}, unless it is the one already shown.
     *
     * @param shown the preview that is shown
     * @param with  the model with another preview shown
     * @param load  the load of the preview of a key
     */
    private static <K, V> Next selected(Model model, K key, Option<Preview<K, V>> shown,
                                        Function<Option<Preview<K, V>>, Model> with, Function<K, Cmd.Load> load) {
        return shown.exists(preview -> preview.key().equals(key))
                ? Next.of(model)
                : Next.of(with.apply(Option.of(Preview.loading(key, shown))), load.apply(key));
    }
}
