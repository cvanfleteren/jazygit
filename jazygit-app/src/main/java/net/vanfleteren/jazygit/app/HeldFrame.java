package net.vanfleteren.jazygit.app;

import java.time.Instant;
import net.vanfleteren.jazygit.kernel.state.Jobs;
import net.vanfleteren.jazygit.kernel.state.Model;

/**
 * The screen of the repository shown before another was opened in its place, drawn while the one opened reads what its
 * panes show: it would show them loading for a frame or two otherwise. It is held as a command waits before it shows
 * (ADR 33), for {@link Jobs#SHOWN_AFTER} at most; a repository that takes longer shows them loading.
 *
 * @param shown the last model of the repository shown before
 * @param until when the repository opened is drawn, read or not
 */
record HeldFrame(Model shown, Instant until) {

    /**
     * The screen of {@code shown}, held from {@code now}, as another repository is opened in its place.
     */
    static HeldFrame of(Model shown, Instant now) {
        return new HeldFrame(shown, now.plus(Jobs.SHOWN_AFTER));
    }

    /**
     * Whether the screen before is still drawn at {@code now}, while {@code opened} is the model of the repository
     * opened: until its panes were read, or it is {@link #until}.
     */
    boolean holds(Model opened, Instant now) {
        return !opened.repo().firstRead() && now.isBefore(until);
    }

    /**
     * What is drawn at {@code now} while {@code opened} is the model of the repository opened.
     */
    Model drawn(Model opened, Instant now) {
        return holds(opened, now) ? shown : opened;
    }
}
