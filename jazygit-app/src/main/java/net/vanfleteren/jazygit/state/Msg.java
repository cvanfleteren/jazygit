package net.vanfleteren.jazygit.state;

import java.time.Instant;

/**
 * Everything that can happen to the {@link Model}, grouped by feature. A message knows how to
 * apply itself: each feature has its own sub-interface, which delegates to the transitions of that
 * feature, so adding a feature needs no change here or in {@link Update}.
 */
public interface Msg {

    /**
     * The next model, and the IO to perform for it, given that this happened to {@code model}.
     */
    Update.Next apply(Model model);

    /**
     * The user asked to quit. Quitting is up to the app, which stops before the model sees this.
     */
    record Quit() implements Msg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model);
        }
    }

    /**
     * Periodic prompt to check the repository for changes made outside the app.
     *
     * @param now the time it is
     */
    record Tick(Instant now) implements Msg {

        @Override
        public Update.Next apply(Model model) {
            return Update.refresh(model.withNow(now));
        }
    }

    /**
     * The user moved in the list of the open popup, or changed its filter.
     */
    record ListCursorChanged(ListCursor cursor) implements Msg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model.popup().isDefined() ? model.withListCursor(cursor) : model);
        }
    }

    /**
     * The element with the id {@code id} of the screen shown, outside the popups, got the focus.
     *
     * @param content whether it is an area of the content panel, rather than a pane
     */
    record Focused(String id, boolean content) implements Msg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model.withShownPane(id, content));
        }
    }

    /**
     * The user scrolled the content area {@code area}.
     */
    record ContentScrolled(String area, Scrolled to) implements Msg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model.withScrolled(model.scrolled().put(area, to)));
        }
    }
}
