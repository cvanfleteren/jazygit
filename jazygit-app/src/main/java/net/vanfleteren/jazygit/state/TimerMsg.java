package net.vanfleteren.jazygit.state;

import java.time.Duration;

/**
 * Time went by: the timers of the app, apart from those of a feature, send these.
 */
public sealed interface TimerMsg extends Msg {

    /**
     * Periodic prompt to check the repository for changes made outside the app.
     */
    record Tick() implements TimerMsg, Msg.Periodic {

        /**
         * One status every 15 s rather than every second: 4 git processes a minute instead of 60.
         */
        public static final Duration WHILE_AWAY = Duration.ofSeconds(15);

        @Override
        public Update.Next apply(Model model) {
            return Update.tick(model);
        }

        @Override
        public Duration whileAway() {
            return WHILE_AWAY;
        }
    }

    /**
     * A second went by, told apart from the refresh: every message tells the model the time it is
     * ({@link Update#step}), and this one wakes it up when nothing else happens, so that the ages shown move on.
     */
    record Clock() implements TimerMsg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model);
        }
    }
}
