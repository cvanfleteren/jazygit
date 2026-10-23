package net.vanfleteren.jazygit.feature.start;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.TextPopup;

/**
 * Asking for the path of another folder to open, as {@code outside} offered.
 *
 * @param outside  what Escape goes back to; a relative path is relative to its folder
 * @param text     the path as far as it was typed
 * @param notFound the folder the path last confirmed named, when there is none; editing the path clears it
 * @param problem  why the folder the path last confirmed named could not be opened, if it could not; editing the
 *                 path clears it
 */
public record OpenFolderPopup(NoRepositoryPopup outside, String text, Option<String> notFound, Option<Failure> problem)
        implements TextPopup {

    /**
     * Asking for a path, from an empty one.
     */
    public OpenFolderPopup(NoRepositoryPopup outside) {
        this(outside, "", Option.none(), Option.none());
    }

    @Override
    public OpenFolderPopup withText(String text) {
        return new OpenFolderPopup(outside, text, Option.none(), Option.none());
    }

    /**
     * Saying there is no folder {@code directory}, which the path names.
     */
    public OpenFolderPopup withNotFound(String directory) {
        return new OpenFolderPopup(outside, text, Option.of(directory), Option.none());
    }

    /**
     * Saying that the folder the path names could not be opened, for {@code failure}.
     */
    public OpenFolderPopup failing(Failure failure) {
        return new OpenFolderPopup(outside, text, Option.none(), Option.of(failure));
    }
}
