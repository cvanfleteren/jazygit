package net.vanfleteren.jazygit.feature.repos;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface RecentReposMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return RecentReposUpdate.update(model, this);
    }

    /**
     * The user asked to switch to a recent repository.
     */
    record Requested() implements RecentReposMsg {}

    /**
     * The recent repositories that can be switched to, the last one opened first.
     */
    record Listed(List<RecentRepo> repos) implements RecentReposMsg {}

    record Cancelled() implements RecentReposMsg {}

    record Chosen(String directory) implements RecentReposMsg {}

    /**
     * The app shows the repository at {@code directory} now.
     */
    record Switched(String directory) implements RecentReposMsg {}

    record NotSwitched(String directory, Failure failure) implements RecentReposMsg {}
}
