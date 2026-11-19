package net.vanfleteren.jazygit.state.view;

import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.git.model.Commit;

/**
 * What the commits pane and the logs show, apart from the previews read for the commit highlighted in it, and what
 * the features keep in parts of their own, as the search of the log (ADR 158).
 *
 * @param cherryPick the commit copied to be cherry-picked onto the branch checked out later, if one is
 */
@With
public record CommitsView(Option<Commit> cherryPick) {

    public static final CommitsView INITIAL = new CommitsView(Option.none());
}
