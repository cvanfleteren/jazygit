package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import java.time.Duration;

/**
 * Everything that can happen to the {@link Model}, grouped by feature. A message knows how to
 * apply itself: each feature has its own sub-interface, which delegates to the transitions of that
 * feature, so adding a feature needs no change here or in {@link Update}. What stays here belongs to no feature: how
 * the app quits, how a command reports it is done, and what a timer's message is.
 */
public interface Msg {

    /**
     * The next model, and the IO to perform for it, given that this happened to {@code model}.
     */
    Update.Next apply(Model model);

    /**
     * The user asked to quit. The app stops, unless the model holds something that would be lost: a commit message
     * that was typed.
     */
    record Quit() implements Msg {

        @Override
        public Update.Next apply(Model model) {
            return model.popup().exists(Popup::holdsInput)
                    ? Update.Next.of(model)
                    : Update.Next.of(model).quitting();
        }
    }

    /**
     * A message a timer sends to read the repository, the remotes or GitHub again. While the terminal does not have
     * the focus, it is handled at most once every {@link #whileAway()}, kept otherwise, and handled once the terminal
     * has it again (ADR 58, ADR 67).
     */
    interface Periodic extends Msg {

        /**
         * How long this message waits between two times it is handled while the terminal does not have the focus.
         */
        Duration whileAway();
    }

    /**
     * The command {@code id} is done (ADR 33), and what it reported, if anything: one that a newer one for its slot
     * cancelled may report nothing.
     */
    record Done(long id, Option<Msg> result) implements Msg {

        @Override
        public Update.Next apply(Model model) {
            Model done = model.mapJobs(jobs -> jobs.withDone(id, model.now()));
            return result.map(msg -> msg.apply(done)).getOrElse(() -> Update.Next.of(done));
        }
    }
}
