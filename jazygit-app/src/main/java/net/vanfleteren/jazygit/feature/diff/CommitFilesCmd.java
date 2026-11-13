package net.vanfleteren.jazygit.feature.diff;

import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.image.Picture;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.diff.Collapsed;
import net.vanfleteren.jazygit.state.view.CommitFilesView;
import org.slf4j.LoggerFactory;

/**
 * What the files of a pull request read besides them.
 */
public sealed interface CommitFilesCmd extends Cmd {

    /**
     * Reads the review threads of pull request {@code number}, with the commits its changes are diffed between
     * (ADR 55). It runs in the background, as it talks to GitHub. It is read for the threads {@code settled} too:
     * those something was sent for, which it is read after (ADR 60). It is read at {@code version} of the threads,
     * which it gives back: one changed since, as by a comment deleted, is newer than what it read (ADR 64).
     */
    record LoadReviewRemarks(PrNumber number, Set<ReviewThread.Id> settled, int version) implements CommitFilesCmd {

        /**
         * The read of the review threads of pull request {@code number} after what was sent for the threads
         * {@code settled}, at the version the files listed in {@code model} have them at, if they are of that pull
         * request (ADR 64).
         */
        public static LoadReviewRemarks after(Model model, PrNumber number, Set<ReviewThread.Id> settled) {
            return new LoadReviewRemarks(
                    number,
                    settled,
                    model.repoView()
                            .commitFiles()
                            .filter(view -> view.pullRequest().contains(number))
                            .map(CommitFilesView::version)
                            .getOrElse(0));
        }

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.reviewRemarks", String.valueOf(number.value())));
        }

        @Override
        public boolean background() {
            return true;
        }

        @Override
        public Msg run(Env env) {
            return env.gitHub()
                    .reviews()
                    .remarks(number)
                    .mapLeft(Failure::of)
                    .fold(this::failed, remarks -> new CommitFilesMsg.RemarksLoaded(number, remarks, settled, version));
        }

        @Override
        public Msg failed(Failure failure) {
            return new CommitFilesMsg.RemarksFailed(number, failure, settled, version);
        }
    }

    /**
     * Reads the image at {@code url}, in a review thread of pull request {@code number}, as a picture to show it
     * (ADR 84). It runs in the background, as it talks to GitHub. An image that cannot be read or shown is none, and
     * stays the text that says it is there: it is no failure to tell.
     */
    record LoadPicture(PrNumber number, String url) implements CommitFilesCmd {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.picture", String.valueOf(number.value())));
        }

        @Override
        public boolean background() {
            return true;
        }

        @Override
        public Msg run(Env env) {
            return env.gitHub()
                    .pullRequests()
                    .image(url)
                    .mapLeft(Failure::of)
                    .fold(
                            this::failed,
                            bytes -> new CommitFilesMsg.PictureLoaded(
                                    number, url, Picture.of(bytes).toOption()));
        }

        @Override
        public Msg failed(Failure failure) {
            return new CommitFilesMsg.PictureLoaded(number, url, Option.none());
        }
    }

    /**
     * Reads the review threads and comments of pull request {@code number} of {@code repository}, as
     * {@code owner/name}, that were collapsed or opened against their default in an earlier session (ADR 63).
     */
    record LoadCollapsed(String repository, PrNumber number) implements CommitFilesCmd {

        @Override
        public Msg run(Env env) {
            return new CommitFilesMsg.CollapsedLoaded(
                    number, env.collapsed().load(repository, number.value()).mapKeys(Collapsed.Key::new));
        }

        // Nothing kept is as nothing flipped.
        @Override
        public Msg failed(Failure failure) {
            return new CommitFilesMsg.CollapsedKept();
        }
    }

    /**
     * Keeps {@code flipped} as the review threads and comments of pull request {@code number} of {@code repository},
     * as {@code owner/name}, collapsed or opened against their default, flipped at {@code at}, for the next session
     * (ADR 63). That they could not be kept is only written to the debug log: they are only a convenience.
     */
    record SaveCollapsed(String repository, PrNumber number, Map<Collapsed.Key, Boolean> flipped, Instant at)
            implements CommitFilesCmd {

        @Override
        public Msg run(Env env) {
            env.collapsed()
                    .save(repository, number.value(), flipped.mapKeys(Collapsed.Key::value), at)
                    .onFailure(failed -> LoggerFactory.getLogger(SaveCollapsed.class)
                            .debug("The review threads collapsed or opened could not be kept", failed));
            return new CommitFilesMsg.CollapsedKept();
        }

        @Override
        public Msg failed(Failure failure) {
            return new CommitFilesMsg.CollapsedKept();
        }
    }
}
