package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;

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
     * Periodic prompt to check the repository for changes made outside the app.
     */
    record Tick() implements Msg {

        @Override
        public Update.Next apply(Model model) {
            return Update.tick(model);
        }
    }

    /**
     * A second went by, told apart from the refresh: every message tells the model the time it is
     * ({@link Update#step}), and this one wakes it up when nothing else happens, so that the ages shown move on.
     */
    record Clock() implements Msg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model);
        }
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

    /**
     * The user changed the text of the open {@link TextPopup} to {@code text}.
     */
    record TextEdited(String text) implements Msg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model.popup(TextPopup.class)
                    .map(popup -> model.openPopup(popup.withText(text)))
                    .getOrElse(model));
        }
    }

    /**
     * The user closed the open popup of type {@code type}, by Escape, without doing what it offers (ADR 38). A popup
     * of another type, that opened in the meantime, stays open.
     */
    record PopupDismissed(Class<? extends Popup> type) implements Msg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model.withoutPopup(type));
        }
    }

    /**
     * The user confirmed the open popup of type {@code type}: it closes, and the operation it asks about starts
     * (ADR 38). When no such popup is open, or it asks about none as it stands, nothing happens.
     */
    record PopupConfirmed(Class<? extends ConfirmablePopup> type) implements Msg {

        @Override
        public Update.Next apply(Model model) {
            return Update.confirm(model, type, ConfirmablePopup::confirm);
        }
    }
}
