package net.vanfleteren.jazygit.features.changes.reflog;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.MetadataStamp;
import net.vanfleteren.jazygit.core.repo.Kept;
import net.vanfleteren.jazygit.core.repo.Reflog;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * Reading the reflog of HEAD the commits pane shows in a tab (ADR 186).
 */
public sealed interface ReflogMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return ReflogUpdate.update(model, this);
    }

    /**
     * {@code cmd} read {@code reflog}, and what keeps those of its commits no ref reaches, {@code kept}; none when
     * that could not be found (ADR 188).
     */
    record Read(ReadReflog cmd, Reflog reflog, Option<Kept> kept, Option<MetadataStamp> stamp) implements ReflogMsg {}

    /**
     * {@code cmd} could not read the reflog, for {@code failure}.
     */
    record Failed(ReadReflog cmd, Failure failure) implements ReflogMsg {}

    /**
     * The entry at {@code place} was highlighted: the next page is read once it is in the last page read.
     */
    record Highlighted(int place) implements ReflogMsg {}

    /**
     * Time to look whether the reflog changed outside the app, while its tab is shown.
     */
    record Tick() implements ReflogMsg {}

    /**
     * What the reflog of HEAD is read from looked like, {@code stamp}; none when that cannot be told.
     */
    record Stamped(Option<MetadataStamp> stamp) implements ReflogMsg {}
}
