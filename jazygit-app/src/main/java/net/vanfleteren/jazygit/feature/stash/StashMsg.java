package net.vanfleteren.jazygit.feature.stash;

import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface StashMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return StashUpdate.update(model, this);
    }

    /**
     * The user asked to stash the changes; the name is still to be entered.
     */
    record Requested() implements StashMsg {}

    /**
     * The user asked to unstash {@code stash}.
     */
    record PopRequested(Stash stash) implements StashMsg {}

    /**
     * The user asked to delete {@code stash}; confirmation is still to be given.
     */
    record DropRequested(Stash stash) implements StashMsg {}
}
