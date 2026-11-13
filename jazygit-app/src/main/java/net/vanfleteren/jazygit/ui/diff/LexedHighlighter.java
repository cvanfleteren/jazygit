package net.vanfleteren.jazygit.ui.diff;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.widgets.syntax.SyntaxHighlighter;
import dev.tamboui.widgets.syntax.SyntaxTheme;
import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.WordDiff.Segment;
import net.vanfleteren.jazygit.syntax.Language;
import net.vanfleteren.jazygit.syntax.Lexer;
import net.vanfleteren.jazygit.syntax.Token;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * The code blocks of a version rendered (ADR 103) coloured as the lines of a diff are (ADR 53): lexed by jazygit's
 * lexer, for the language the info string of the block names, each token in the colour the theme gives its kind. A
 * language the lexer does not know is plain.
 */
final class LexedHighlighter implements SyntaxHighlighter {

    private final Theme.SpanStyles styles;

    LexedHighlighter(Theme.SpanStyles styles) {
        this.styles = styles;
    }

    @Override
    public java.util.List<Line> highlight(String code, String language, Style base, SyntaxTheme theme) {
        List<String> lines = List.of(code.split("\n", -1));
        return named(language)
                .map(known -> lines.foldLeft(
                                Tuple.<List<Line>, Lexer.State>of(List.empty(), new Lexer.Outside()),
                                (done, line) -> Lexer.lexed(known, done._2(), line)
                                        .map(lexed -> Tuple.of(
                                                done._1().append(coloured(line, lexed.tokens(), base)), lexed.state()))
                                        .getOrElse(() -> Tuple.of(done._1().append(plain(line, base)), done._2())))
                        ._1())
                .getOrElse(() -> lines.map(line -> plain(line, base)))
                .toJavaList();
    }

    /**
     * The language an info string calls {@code name}: as the extension of a file, as {@code java}, or as the program a
     * shebang runs, as {@code python}.
     */
    static Option<Language> named(String name) {
        return Option.of(name)
                .map(String::strip)
                .filter(given -> !given.isEmpty())
                .flatMap(given -> Language.of("block." + given, Option.of("#!" + given)));
    }

    private Line coloured(String line, List<Token> tokens, Style base) {
        return Line.from(RichDiff.pieces(List.of(new Segment(line, false)), tokens)
                .map(piece -> piece.kind()
                        .map(kind -> Span.styled(piece.text(), base.patch(styles.of(RichDiff.syntaxClass(kind)))))
                        .getOrElse(() -> Span.styled(piece.text(), base)))
                .toJavaList());
    }

    private static Line plain(String line, Style base) {
        return Line.from(Span.styled(line, base));
    }
}
