package net.vanfleteren.jazygit.state.view;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * What the user does with the search of the content panel (ADR 126). Where it goes to a match is told with
 * {@link ViewMsg.ContentScrolled}, as the area works that out.
 */
public sealed interface ContentSearchMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return Update.Next.of(model.withContentSearch(next(model.contentSearch())));
    }

    /**
     * The search after this, {@code current} before.
     */
    Option<ContentSearch> next(Option<ContentSearch> current);

    /**
     * The user opened the line above the area to type the query in.
     */
    record Opened() implements ContentSearchMsg {

        @Override
        public Option<ContentSearch> next(Option<ContentSearch> current) {
            return Option.of(ContentSearch.opened(current));
        }
    }

    /**
     * The user changed the text of the open line to {@code text}.
     */
    record Edited(String text) implements ContentSearchMsg {

        @Override
        public Option<ContentSearch> next(Option<ContentSearch> current) {
            return current.map(search -> search.typing() ? new ContentSearch(text, search.before()) : search);
        }
    }

    /**
     * The user closed the line, putting back the search from before it was opened.
     */
    record Cancelled() implements ContentSearchMsg {

        @Override
        public Option<ContentSearch> next(Option<ContentSearch> current) {
            return current.flatMap(search -> search.before()
                    .map(before -> Option.when(!before.isEmpty(), new ContentSearch(before, Option.none())))
                    .getOrElse(Option.of(search)));
        }
    }

    /**
     * The user closed the line, searching for what it holds; nothing when it holds no text.
     */
    record Submitted() implements ContentSearchMsg {

        @Override
        public Option<ContentSearch> next(Option<ContentSearch> current) {
            return current.filter(search -> !search.text().isEmpty())
                    .map(search -> new ContentSearch(search.text(), Option.none()));
        }
    }

    /**
     * The user cleared the search.
     */
    record Cleared() implements ContentSearchMsg {

        @Override
        public Option<ContentSearch> next(Option<ContentSearch> current) {
            return Option.none();
        }
    }
}
