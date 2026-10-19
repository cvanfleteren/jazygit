package net.vanfleteren.jazygit.feature.branch.movecommits;

import net.vanfleteren.jazygit.state.TextPopup;

/**
 * Asking for the name of the new branch the commits of {@code request} move to.
 *
 * @param name the name as far as it was typed
 */
public record MoveNamePopup(MoveRequest request, String name) implements TextPopup {

    public MoveNamePopup(MoveRequest request) {
        this(request, "");
    }

    @Override
    public String text() {
        return name;
    }

    @Override
    public MoveNamePopup withText(String text) {
        return new MoveNamePopup(request, text);
    }
}
