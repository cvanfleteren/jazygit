package net.vanfleteren.jazygit.features.github.remarks;

import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.core.gh.model.ReviewThread;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.diff.Collapsed;
import net.vanfleteren.jazygit.lib.image.Picture;

/**
 * What happened to the review threads of the pull request whose files are listed (ADR 55, 171).
 */
public sealed interface RemarksMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return RemarksUpdate.update(model, this);
    }

    /**
     * The review threads of pull request {@code number} were read (ADR 55), after what was sent for the threads
     * {@code settled} (ADR 60), by a read started at {@code version} of them (ADR 64).
     */
    record Loaded(PrNumber number, ReviewRemarks remarks, Set<ReviewThread.Id> settled, int version)
            implements RemarksMsg {}

    /**
     * Reading the review threads of pull request {@code number} failed, after what was sent for the threads
     * {@code settled}, by a read started at {@code version} of them (ADR 64).
     */
    record Failed(PrNumber number, Failure failure, Set<ReviewThread.Id> settled, int version) implements RemarksMsg {}

    /**
     * The image at {@code url}, in a review thread of pull request {@code number}, was read as {@code picture}, or
     * could not be (ADR 84).
     */
    record PictureLoaded(PrNumber number, String url, Option<Picture> picture) implements RemarksMsg {}

    /**
     * The user asked to read the review threads of the pull request again.
     */
    record Refreshed() implements RemarksMsg {}

    /**
     * The review threads and comments of pull request {@code number} collapsed or opened against their default in an
     * earlier session were read, each with whether it was collapsed by default then (ADR 63).
     */
    record CollapsedLoaded(PrNumber number, Map<Collapsed.Key, Boolean> kept) implements RemarksMsg {}

    /**
     * The review threads and comments collapsed or opened were kept for the next session, or that failed: nothing
     * changes.
     */
    record CollapsedKept() implements RemarksMsg {}
}
