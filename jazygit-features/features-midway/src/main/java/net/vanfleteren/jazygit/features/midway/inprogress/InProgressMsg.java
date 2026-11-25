package net.vanfleteren.jazygit.features.midway.inprogress;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.git.model.InProgressAction;
import net.vanfleteren.jazygit.core.git.model.MarkedFile;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

public sealed interface InProgressMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return InProgressUpdate.update(model, this);
    }

    /**
     * The user asked what to do with the operation the repository is in the middle of.
     */
    record Requested() implements InProgressMsg {}

    /**
     * The user chose what to do with it.
     */
    record Chosen(InProgressAction action) implements InProgressMsg {}

    /**
     * The files resolved since it stopped were read for conflict markers: {@code marked} are those with some left.
     */
    record MarkersRead(List<MarkedFile> marked) implements InProgressMsg {}
}
