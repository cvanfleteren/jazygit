package net.vanfleteren.jazygit.features.changes.reflog;

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
     * {@code cmd} read {@code reflog}.
     */
    record Read(ReadReflog cmd, Reflog reflog) implements ReflogMsg {}

    /**
     * {@code cmd} could not read the reflog, for {@code failure}.
     */
    record Failed(ReadReflog cmd, Failure failure) implements ReflogMsg {}

    /**
     * The entry at {@code place} was highlighted, the oldest so far: the next page is read once it is in the last page
     * read.
     */
    record Reached(int place) implements ReflogMsg {}
}
