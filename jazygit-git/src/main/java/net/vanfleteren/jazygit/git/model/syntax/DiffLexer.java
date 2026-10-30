package net.vanfleteren.jazygit.git.model.syntax;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.Hunk;
import net.vanfleteren.jazygit.syntax.Language;
import net.vanfleteren.jazygit.syntax.Lexer;
import net.vanfleteren.jazygit.syntax.Lexer.Lexed;
import net.vanfleteren.jazygit.syntax.Lexer.Outside;
import net.vanfleteren.jazygit.syntax.Lexer.State;
import net.vanfleteren.jazygit.syntax.Token;

/**
 * The highlighter of ADR 53, which lexes a diff with jazygit-syntax's {@link Lexer}. A hunk is lexed as two texts,
 * the old one and the new one, each line going on from where the line before it in the same text left the scanner;
 * each hunk starts outside of everything.
 */
public record DiffLexer() implements Highlighter {

    @Override
    public List<List<Option<List<Token>>>> tokens(FileDiff file) {
        Option<Language> language =
                file.type() == ChangeType.CONFLICTED ? Option.none() : Language.of(file.path(), firstLine(file));
        return file.hunks()
                .map(hunk -> language.filter(ignored -> hunk.numbered())
                        .map(known -> hunk(known, hunk))
                        .getOrElse(() -> hunk.lines().map(line -> Option.none())));
    }

    /**
     * The first line of the new file, when the diff shows it.
     */
    private static Option<String> firstLine(FileDiff file) {
        return file.hunks()
                .headOption()
                .filter(Hunk::numbered)
                .flatMap(hunk -> hunk.lines()
                        .find(line -> line.kind() != DiffLine.Kind.NOTE && line.kind() != DiffLine.Kind.REMOVED))
                .filter(line -> line.newNumber().contains(1))
                .map(DiffLine::text);
    }

    /**
     * The tokens of the lines of {@code hunk}: a removed line's from the old text, an added or a context line's from
     * the new one. A note is not lexed, and leaves both where they were. A line longer than {@link Lexer#MAX_LINE} is
     * not lexed either, and the line after it in the same text starts outside of everything, as a hunk does.
     */
    private static List<Option<List<Token>>> hunk(Language language, Hunk hunk) {
        Outside start = new Outside();
        return hunk.lines()
                .foldLeft(
                        Tuple.of(List.<Option<List<Token>>>empty(), (State) start, (State) start),
                        (done, line) -> switch (line.kind()) {
                            case NOTE -> done.map1(lines -> lines.prepend(Option.none()));
                            case REMOVED -> {
                                Option<Lexed> old = Lexer.lexed(language, done._2, line.text());
                                yield Tuple.of(done._1.prepend(old.map(Lexed::tokens)), after(old), done._3);
                            }
                            case ADDED -> {
                                Option<Lexed> now = Lexer.lexed(language, done._3, line.text());
                                yield Tuple.of(done._1.prepend(now.map(Lexed::tokens)), done._2, after(now));
                            }
                            case CONTEXT -> {
                                Option<Lexed> now = Lexer.lexed(language, done._3, line.text());
                                // Most often both texts are outside of everything, and lexing it once is enough.
                                Option<Lexed> old =
                                        done._2.equals(done._3) ? now : Lexer.lexed(language, done._2, line.text());
                                yield Tuple.of(done._1.prepend(now.map(Lexed::tokens)), after(old), after(now));
                            }
                        })
                ._1
                .reverse();
    }

    /**
     * What the scanner is inside of after a line that was {@code lexed}: outside of everything after one that was not.
     */
    private static State after(Option<Lexed> lexed) {
        return lexed.map(Lexed::state).getOrElse(Outside::new);
    }
}
