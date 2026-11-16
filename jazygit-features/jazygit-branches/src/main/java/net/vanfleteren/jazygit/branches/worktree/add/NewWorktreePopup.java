package net.vanfleteren.jazygit.branches.worktree.add;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.popup.ConfirmablePopup;
import net.vanfleteren.jazygit.state.popup.TextPopup;

/**
 * Asking where the new worktree that checks out {@code source} goes.
 *
 * @param location  where it goes, as far as it was typed; it starts out where a template leads, or next to the main
 *                  worktree (ADR 134)
 * @param switching whether the app switches to it once it is added, without asking, as when it was chosen to check
 *                  something out in (ADR 68)
 * @param templated whether it started out where a template of the git configuration leads; if not, the dialog says
 *                  which key would set one
 */
public record NewWorktreePopup(WorktreeSource source, String location, boolean switching, boolean templated)
        implements TextPopup, ConfirmablePopup {

    /**
     * Asking where it goes, starting out where it does without a template.
     */
    public NewWorktreePopup(WorktreeSource source, String location, boolean switching) {
        this(source, location, switching, false);
    }

    /**
     * Asking where it goes, starting out where it does without a template; once added, switching to it is offered.
     */
    public NewWorktreePopup(WorktreeSource source, String location) {
        this(source, location, false);
    }

    /**
     * The key of the git configuration that would set where it starts out: the one for pull requests, for one.
     */
    public String templateKey() {
        return source instanceof WorktreeSource.PullRequest
                ? ReadWorktreeTemplate.PULL_REQUEST_KEY
                : ReadWorktreeTemplate.KEY;
    }

    @Override
    public String text() {
        return location;
    }

    @Override
    public NewWorktreePopup withText(String text) {
        return new NewWorktreePopup(source, text, switching, templated);
    }

    @Override
    public Option<AddWorktreeCmd> confirm() {
        // A blank location keeps the dialog open.
        return Option.of(location.strip())
                .filter(text -> !text.isEmpty())
                .map(text -> new AddWorktreeCmd(source, text, switching));
    }
}
