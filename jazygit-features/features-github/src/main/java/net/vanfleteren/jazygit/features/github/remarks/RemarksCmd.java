package net.vanfleteren.jazygit.features.github.remarks;

import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.ReviewThread;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.features.github.state.GitHubStores;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.diff.Collapsed;
import net.vanfleteren.jazygit.lib.image.Picture;
import org.slf4j.LoggerFactory;

/**
 * What GitHub reads of the files of a pull request besides them: its review threads, their images, and which of them
 * were collapsed in an earlier session (ADR 55, 63, 84).
 */
public sealed interface RemarksCmd extends Cmd {

    /**
     * Reads the review threads of pull request {@code number}, with the commits its changes are diffed between
     * (ADR 55). It runs in the background, as it talks to GitHub. It is read for the threads {@code settled} too:
     * those something was sent for, which it is read after (ADR 60). It is read at {@code version} of the threads,
     * which it gives back: one changed since, as by a comment deleted, is newer than what it read (ADR 64).
     */
    record LoadReviewRemarks(PrNumber number, Set<ReviewThread.Id> settled, int version) implements RemarksCmd {

        /**
         * The read of the review threads of pull request {@code number} after what was sent for the threads
         * {@code settled}, at the version GitHub's part in {@code model} has them at, if it keeps them (ADR 64).
         */
        public static LoadReviewRemarks after(Model model, PrNumber number, Set<ReviewThread.Id> settled) {
            return new LoadReviewRemarks(
                    number,
                    settled,
                    model.part(RemarksState.PART)
                            .open()
                            .filter(read -> read.number().equals(number))
                            .map(PrRemarks::version)
                            .getOrElse(0));
        }

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("github.work.reviewRemarks", String.valueOf(number.value())));
        }

        @Override
        public boolean background() {
            return true;
        }

        @Override
        public Msg run(Env env) {
            return GitHubServices.of(env)
                    .reviews()
                    .remarks(number)
                    .mapLeft(GitHubFailure::of)
                    .fold(this::failed, remarks -> new RemarksMsg.Loaded(number, remarks, settled, version));
        }

        @Override
        public Msg failed(Failure failure) {
            return new RemarksMsg.Failed(number, failure, settled, version);
        }
    }

    /**
     * Reads the image at {@code url}, in a review thread of pull request {@code number}, as a picture to show it
     * (ADR 84). It runs in the background, as it talks to GitHub. An image that cannot be read or shown is none, and
     * stays the text that says it is there: it is no failure to tell.
     */
    record LoadPicture(PrNumber number, String url) implements RemarksCmd {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("github.work.picture", String.valueOf(number.value())));
        }

        @Override
        public boolean background() {
            return true;
        }

        @Override
        public Msg run(Env env) {
            return GitHubServices.of(env)
                    .pullRequests()
                    .image(url)
                    .mapLeft(GitHubFailure::of)
                    .fold(
                            this::failed,
                            bytes -> new RemarksMsg.PictureLoaded(
                                    number, url, Picture.of(bytes).toOption()));
        }

        @Override
        public Msg failed(Failure failure) {
            return new RemarksMsg.PictureLoaded(number, url, Option.none());
        }
    }

    /**
     * Reads the review threads and comments of pull request {@code number} of {@code repository}, as
     * {@code owner/name}, that were collapsed or opened against their default in an earlier session (ADR 63).
     */
    record LoadCollapsed(String repository, PrNumber number) implements RemarksCmd {

        @Override
        public Msg run(Env env) {
            return new RemarksMsg.CollapsedLoaded(
                    number,
                    env.service(GitHubStores.COLLAPSED)
                            .load(repository, number.value())
                            .mapKeys(Collapsed.Key::new));
        }

        // Nothing kept is as nothing flipped.
        @Override
        public Msg failed(Failure failure) {
            return new RemarksMsg.CollapsedKept();
        }
    }

    /**
     * Keeps {@code flipped} as the review threads and comments of pull request {@code number} of {@code repository},
     * as {@code owner/name}, collapsed or opened against their default, flipped at {@code at}, for the next session
     * (ADR 63). That they could not be kept is only written to the debug log: they are only a convenience.
     */
    record SaveCollapsed(String repository, PrNumber number, Map<Collapsed.Key, Boolean> flipped, Instant at)
            implements RemarksCmd {

        @Override
        public Msg run(Env env) {
            env.service(GitHubStores.COLLAPSED)
                    .save(repository, number.value(), flipped.mapKeys(Collapsed.Key::value), at)
                    .onFailure(failed -> LoggerFactory.getLogger(SaveCollapsed.class)
                            .debug("The review threads collapsed or opened could not be kept", failed));
            return new RemarksMsg.CollapsedKept();
        }

        @Override
        public Msg failed(Failure failure) {
            return new RemarksMsg.CollapsedKept();
        }
    }
}
