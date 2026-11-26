package net.vanfleteren.jazygit.features.github.comments;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.lib.image.Picture;

/**
 * Reading the conversation on a pull request.
 */
public sealed interface CommentsCmd extends Cmd {

    /**
     * Reads the description, comments and reviews of pull request {@code number}.
     */
    record LoadComments(PrNumber number) implements CommentsCmd {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("github.work.comments", String.valueOf(number.value())));
        }

        @Override
        public Msg run(Env env) {
            return GitHubServices.of(env)
                    .reviews()
                    .conversation(number)
                    .mapLeft(GitHubFailure::of)
                    .fold(this::failed, comments -> new CommentsMsg.Loaded(number, comments));
        }

        @Override
        public Msg failed(Failure failure) {
            return new CommentsMsg.LoadFailed(number, failure);
        }
    }

    /**
     * Reads the image at {@code url}, in the conversation on pull request {@code number}, as a picture to show it
     * (ADR 190). It runs in the background, as it talks to GitHub. An image that cannot be read or shown is none, and
     * stays the text that says it is there: it is no failure to tell.
     */
    record LoadPicture(PrNumber number, String url) implements CommentsCmd {

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
                            bytes -> new CommentsMsg.PictureLoaded(
                                    number, url, Picture.of(bytes).toOption()));
        }

        @Override
        public Msg failed(Failure failure) {
            return new CommentsMsg.PictureLoaded(number, url, Option.none());
        }
    }
}
