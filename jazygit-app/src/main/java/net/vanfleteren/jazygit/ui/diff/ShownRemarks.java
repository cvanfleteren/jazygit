package net.vanfleteren.jazygit.ui.diff;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.image.Picture;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.diff.Collapsed;

/**
 * The review threads of a pull request as they are shown with a diff of some of its files (ADR 55).
 *
 * @param threads the threads read
 * @param now     the time it is, to the minute, to tell how long ago a comment was written: the diff is only
 *                styled again once a minute for it
 * @param failed  why reading them failed, when it did and none were read before
 * @param onLines whether they were read at the commits of the diff, so their lines number its lines: else the
 *                threads on a line are listed at the end of their file
 * @param collapsed which threads and comments are collapsed (ADR 62)
 * @param sending the ids of the threads a reply or a resolving is sent for (ADR 60)
 * @param deleting the ids of the comments being deleted (ADR 64)
 * @param pictures the pictures of the images of the threads that were read, by where they are (ADR 84)
 */
public record ShownRemarks(
        List<ReviewThread> threads,
        Instant now,
        Option<Failure> failed,
        boolean onLines,
        Collapsed collapsed,
        Set<ReviewThread.Id> sending,
        Set<ReviewThread.Comment.Id> deleting,
        Map<String, Picture> pictures) {

    public ShownRemarks(
            List<ReviewThread> threads,
            Instant now,
            Option<Failure> failed,
            boolean onLines,
            Collapsed collapsed,
            Set<ReviewThread.Id> sending,
            Set<ReviewThread.Comment.Id> deleting) {
        this(threads, now, failed, onLines, collapsed, sending, deleting, HashMap.empty());
    }
}
