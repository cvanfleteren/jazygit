package net.vanfleteren.jazygit.content;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.content.ContentView.Tone;
import net.vanfleteren.jazygit.ui.content.Body;
import net.vanfleteren.jazygit.ui.content.DrawContext;
import net.vanfleteren.jazygit.ui.content.Drawn;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * What the files pane says in place of a diff or a file's content, drawn as {@code tone} says (ADR 117).
 */
record NoteBody(String text, Tone tone) implements Body {

    @Override
    public Drawn draw(DrawContext context) {
        String cssClass = switch (tone) {
            case DIM -> Theme.Classes.DIM;
            case ERROR -> Theme.Classes.ERROR;
        };
        return Drawn.plain(List.of(text.split("\n", -1))
                .map(line -> Line.from(Span.styled(line, context.styles().of(cssClass)))));
    }
}
