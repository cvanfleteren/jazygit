package net.vanfleteren.jazygit.feature.github.prototype;

import io.vavr.control.Either;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;

/**
 * PROTOTYPE: reading the comments of a pull request.
 */
public sealed interface CommentsPrototypeCmd extends Cmd {

    record LoadComments(int number) implements CommentsPrototypeCmd {

        @Override
        public Msg run(Env env) {
            return env.gitHub()
                    .prototypeComments(number)
                    .fold(
                            this::failedWith,
                            comments -> new CommentsPrototypeMsg.Loaded(number, Either.right(comments)));
        }

        @Override
        public Msg failed(Failure failure) {
            return new CommentsPrototypeMsg.Loaded(number, Either.left(failure));
        }
    }
}
