package net.vanfleteren.jazygit.state.view;

import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.git.model.Commit;

/**
 * What the commits pane and the logs show, apart from the previews read for the commit highlighted in it.
 *
 * @param searchLine   the text of the line above the commits pane to type a search in, while it is open
 * @param commitSearch the search of the log of HEAD that was run last, which the commits pane lists in place of the
 *                     log until it is cleared
 * @param cherryPick   the commit copied to be cherry-picked onto the branch checked out later, if one is
 */
@With
public record CommitsView(Option<String> searchLine, Option<CommitSearch> commitSearch, Option<Commit> cherryPick) {

    public static final CommitsView INITIAL = new CommitsView(Option.none(), Option.none(), Option.none());
}
