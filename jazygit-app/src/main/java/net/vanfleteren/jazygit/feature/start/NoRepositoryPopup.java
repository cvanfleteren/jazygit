package net.vanfleteren.jazygit.feature.start;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.repos.RecentRepo;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking what to do instead, as the folder jazygit was started in, or was asked to open, is in no repository.
 *
 * @param directory the folder, absolute
 * @param exists    whether it is a folder at all, which can then be made a repository
 * @param recent    the recent repositories that can be opened instead, the last one opened first
 * @param problem   why what was chosen last failed, if it did
 */
public record NoRepositoryPopup(String directory, boolean exists, List<RecentRepo> recent, Option<Failure> problem)
        implements Popup {

    /**
     * About {@code directory}, which is in no repository either, with the problem before gone.
     */
    public NoRepositoryPopup about(String directory, boolean exists) {
        return new NoRepositoryPopup(directory, exists, recent, Option.none());
    }

    /**
     * Saying that what was chosen failed for {@code failure}.
     */
    public NoRepositoryPopup failing(Failure failure) {
        return new NoRepositoryPopup(directory, exists, recent, Option.of(failure));
    }
}
