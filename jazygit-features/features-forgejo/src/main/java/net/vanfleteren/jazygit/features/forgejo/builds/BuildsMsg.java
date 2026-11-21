package net.vanfleteren.jazygit.features.forgejo.builds;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * Reading how the builds of the branches on Forgejo went (ADR 174).
 */
public sealed interface BuildsMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return BuildsUpdate.update(model, this);
    }

    /**
     * Time to read the commits that are due.
     */
    record Tick() implements BuildsMsg {}

    /**
     * {@code tips} were read: how the builds went of those that could be, {@code states}.
     */
    record Read(List<Tip> tips, Map<Sha, CommitStatus.State> states) implements BuildsMsg {}
}
