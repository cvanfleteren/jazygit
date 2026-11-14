package net.vanfleteren.jazygit.state;

import java.time.Duration;
import net.vanfleteren.jazygit.settings.Settings;

/**
 * Time went by: the timers of the app, apart from those of a feature, send these.
 */
public sealed interface TimerMsg extends Msg {

    /**
     * Periodic prompt to check the repository for changes made outside the app. With {@code git.auto-refresh} off,
     * it reads nothing while the terminal has the focus, but is still read once when the focus comes back (ADR 125).
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

        @Override
        public boolean on(Settings settings) {
            return settings.autoRefresh();
        }

        @Override
        public boolean readOnReturn() {
            return true;
        }
    }

    /**
     * A time the screen shows changes, told apart from the refresh: every message tells the model the time it is
     * ({@link Update#step}), and this one wakes it up at the moment the first time shown changes, which the app then
     * draws a frame for (ADR 124).
     */
    record Clock() implements TimerMsg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model);
        }
    }
}
