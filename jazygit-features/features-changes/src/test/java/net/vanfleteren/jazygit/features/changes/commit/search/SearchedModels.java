package net.vanfleteren.jazygit.features.changes.commit.search;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.git.model.Commit;
import net.vanfleteren.jazygit.core.git.model.CommitQuery;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * Models that list what a search of the log found, for the tests of what acts on a commit.
 */
public final class SearchedModels {

    private SearchedModels() {}

    /**
     * {@code model} listing what the search {@code text} found: {@code found}.
     */
    public static Model searched(Model model, String text, List<Commit> found) {
        model = Update.update(model, new CommitSearchMsg.Opened()).model();
        model = Update.update(model, new CommitSearchMsg.Edited(text)).model();
        model = Update.update(model, new CommitSearchMsg.Submitted()).model();
        return Update.update(model, new SearchCommits.Found(CommitQuery.parse(text), found))
                .model();
    }
}
