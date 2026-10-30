package net.vanfleteren.jazygit.git.model.syntax;

import io.vavr.Tuple;
import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.Hunk;
import net.vanfleteren.jazygit.git.model.syntax.Grammar.Delimited;
import net.vanfleteren.jazygit.git.model.syntax.Grammar.Mode;
import net.vanfleteren.jazygit.git.model.syntax.Token.Kind;

/**
 * The highlighter of ADR 53: one scanner, set up for each {@link Language} by its {@link Grammar}. A hunk is lexed
 * as two texts, the old one and the new one, each line going on from where the line before it in the same text
 * left the scanner; each hunk starts outside of everything.
 */
public record Lexer() implements Highlighter {

    /**
     * How long a line may be to be lexed, in {@code char}s: a longer one is minified or made by a program, which nobody
     * reads in a terminal, and lexing it could take long (ADR 53).
     */
    public static final int MAX_LINE = 2_000;

    /**
     * The first characters of the comments and the strings of every language: only at one of them can one start.
     */
    private static final String OPENINGS = List.of(Language.values())
            .map(Language::grammar)
            .flatMap(grammar -> grammar.lineComments()
                    .appendAll(grammar.blockComments().map(Delimited::open))
                    .appendAll(grammar.strings().map(Delimited::open)))
            .map(open -> open.substring(0, 1))
            .distinct()
            .mkString();
    /**
     * How long the longest keyword of every language is: a longer word is none.
     */
    private static final int LONGEST_KEYWORD = List.of(Language.values())
            .flatMap(language -> language.grammar().keywords())
            .map(String::length)
            .max()
            .getOrElse(0);

    @Override
    public List<List<Option<List<Token>>>> tokens(FileDiff file) {
        Option<Grammar> grammar = file.type() == ChangeType.CONFLICTED
                ? Option.none()
                : Language.of(file.path(), firstLine(file)).map(Language::grammar);
        return file.hunks()
                .map(hunk -> grammar.filter(ignored -> hunk.numbered())
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
     * the new one. A note is not lexed, and leaves both where they were. A line longer than {@link #MAX_LINE} is not
     * lexed either, and the line after it in the same text starts outside of everything, as a hunk does.
     */
    static List<Option<List<Token>>> hunk(Grammar grammar, Hunk hunk) {
        Outside start = new Outside();
        return hunk.lines()
                .foldLeft(
                        Tuple.of(List.<Option<List<Token>>>empty(), (State) start, (State) start),
                        (done, line) -> switch (line.kind()) {
                            case NOTE -> done.map1(lines -> lines.prepend(Option.none()));
                            case REMOVED -> {
                                Option<Lexed> old = lexed(grammar, done._2, line.text());
                                yield Tuple.of(done._1.prepend(old.map(Lexed::tokens)), after(old), done._3);
                            }
                            case ADDED -> {
                                Option<Lexed> now = lexed(grammar, done._3, line.text());
                                yield Tuple.of(done._1.prepend(now.map(Lexed::tokens)), done._2, after(now));
                            }
                            case CONTEXT -> {
                                Option<Lexed> now = lexed(grammar, done._3, line.text());
                                // Most often both texts are outside of everything, and lexing it once is enough.
                                Option<Lexed> old =
                                        done._2.equals(done._3) ? now : lexed(grammar, done._2, line.text());
                                yield Tuple.of(done._1.prepend(now.map(Lexed::tokens)), after(old), after(now));
                            }
                        })
                ._1
                .reverse();
    }

    /**
     * {@code line} lexed from inside {@code state}, unless it is too long to be.
     */
    private static Option<Lexed> lexed(Grammar grammar, State state, String line) {
        return Option.when(line.length() <= MAX_LINE, () -> line(grammar, state, line));
    }

    /**
     * What the scanner is inside of after a line that was {@code lexed}: outside of everything after one that was not.
     */
    private static State after(Option<Lexed> lexed) {
        return lexed.map(Lexed::state).getOrElse(Outside::new);
    }

    /**
     * The tokens of {@code line}, scanned by {@code grammar} from inside {@code state}, and what the scanner is inside
     * of at its end.
     */
    static Lexed line(Grammar grammar, State state, String line) {
        Scan start =
                grammar.mode() == Mode.MARKDOWN ? Markdown.start(line, state) : new Scan(line, 0, state, List.empty());
        // Iterated rather than recursive, as a line can be longer than the stack is deep.
        Scan end = Iterator.iterate(start, scan -> step(grammar, scan))
                .find(scan -> scan.at() >= line.length())
                .get();
        return new Lexed(end.tokens().reverse(), end.state().atEndOfLine());
    }

    private static Scan step(Grammar grammar, Scan scan) {
        return switch (scan.state()) {
            case Inside inside -> scan.inside(scan.at(), scan.at(), inside);
            case InFence ignored -> scan.to(scan.line().length());
            case Outside ignored ->
                switch (grammar.mode()) {
                    case CODE -> code(grammar, scan);
                    case MARKUP -> Markup.text(grammar, scan);
                    case MARKDOWN -> Markdown.inline(grammar, scan);
                };
            case InTag ignored -> Markup.tag(grammar, scan);
        };
    }

    /**
     * One step through code: past a comment, a string, a number or a word, or else one character.
     */
    private static Scan code(Grammar grammar, Scan scan) {
        String line = scan.line();
        int at = scan.at();
        char c = line.charAt(at);
        if (OPENINGS.indexOf(c) >= 0) {
            Option<String> lineComment = grammar.lineComments().find(open -> scan.opens(open));
            if (lineComment.isDefined()) {
                return scan.token(at, line.length(), Kind.COMMENT);
            }
            Option<Scan> delimited = scan.delimited(grammar.blockComments(), Kind.COMMENT)
                    .orElse(() -> scan.delimited(grammar.strings(), Kind.STRING));
            if (delimited.isDefined()) {
                return delimited.get();
            }
        }
        if (Character.isDigit(c) && !scan.afterWord()) {
            return scan.token(at, numberEnd(line, at), Kind.NUMBER);
        } else if (Character.isLetter(c) || c == '_') {
            int end = first(line, at, i -> !wordPart(line.charAt(i)));
            return end - at <= LONGEST_KEYWORD && grammar.keyword(line.substring(at, end))
                    ? scan.token(at, end, Kind.KEYWORD)
                    : scan.to(end);
        }
        // Past what can start nothing in one step, as most of a line is spaces and punctuation.
        return scan.to(first(line, at + 1, i -> wordPart(line.charAt(i)) || OPENINGS.indexOf(line.charAt(i)) >= 0));
    }

    /**
     * Where the number at {@code from} ends: its digits, letters and underscores, like {@code 0x1F} or
     * {@code 1_000L}, and a point followed by a digit.
     */
    private static int numberEnd(String line, int from) {
        return first(
                line,
                from,
                i -> !wordPart(line.charAt(i))
                        && !(line.charAt(i) == '.' && i + 1 < line.length() && Character.isDigit(line.charAt(i + 1))));
    }

    /**
     * Where the first of {@code chars} is in {@code line} from {@code from} on, or its end.
     */
    private static int next(String line, int from, String chars) {
        return first(line, from, i -> chars.indexOf(line.charAt(i)) >= 0);
    }

    /**
     * The first index of {@code line} from {@code from} on that {@code found} holds for, or its end. A loop rather
     * than a stream, as it runs for each piece of each line of a diff, where a stream costs a third of the lexing.
     */
    private static int first(String line, int from, IntPredicate found) {
        for (int i = from; i < line.length(); i++) {
            if (found.test(i)) {
                return i;
            }
        }
        return line.length();
    }

    static boolean wordPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * The tokens of a line, and what the scanner is inside of after it.
     */
    record Lexed(List<Token> tokens, State state) {}

    /**
     * What the scanner is inside of between two pieces of a text.
     */
    sealed interface State permits Outside, Inside, InTag, InFence {

        /**
         * What it is inside of at the start of the next line: a string that cannot go on over lines ends with its
         * line.
         */
        default State atEndOfLine() {
            return this instanceof Inside inside && !inside.delimited().multiline()
                    ? inside.after().atEndOfLine()
                    : this;
        }
    }

    /**
     * Outside of everything.
     */
    record Outside() implements State {}

    /**
     * Inside a string or a comment of {@code kind}, which goes back to {@code after} when it is closed.
     */
    record Inside(Delimited delimited, Kind kind, State after) implements State {}

    /**
     * Inside a tag of XML or HTML, after its name.
     */
    record InTag() implements State {}

    /**
     * Inside a fenced code block of Markdown, which a line that starts with {@code fence} closes.
     */
    record InFence(String fence) implements State {}

    /**
     * Where the scanner is in a line: at {@code at}, inside {@code state}, with the tokens found so far, the last one
     * first.
     */
    record Scan(String line, int at, State state, List<Token> tokens) {

        Scan to(int next) {
            return new Scan(line, next, state, tokens);
        }

        Scan in(State next) {
            return new Scan(line, at, next, tokens);
        }

        /**
         * A token of {@code kind} from {@code start} to {@code end}, after which the scan goes on.
         */
        Scan token(int start, int end, Kind kind) {
            return new Scan(
                    line, end, state, end > start ? tokens.prepend(new Token(start, end - start, kind)) : tokens);
        }

        /**
         * Whether {@code open} starts at the scan. A quote starts nothing right after a letter or a digit, as in
         * {@code don't}; a comment that starts with {@code #} only starts one after a space, or at the start of the
         * line, as {@code $#} is no comment in a shell.
         */
        boolean opens(String open) {
            if (!line.startsWith(open, at)) {
                return false;
            } else if (open.startsWith("'")) {
                return !afterWord();
            } else if (open.startsWith("#")) {
                return at == 0 || Character.isWhitespace(line.charAt(at - 1));
            }
            return true;
        }

        boolean afterWord() {
            return at > 0 && wordPart(line.charAt(at - 1));
        }

        /**
         * The scan past the first of {@code delimited} that opens at the scan, as a token of {@code kind}.
         */
        Option<Scan> delimited(List<Delimited> delimited, Kind kind) {
            return delimited
                    .find(each -> opens(each.open()))
                    .map(each -> inside(at, at + each.open().length(), new Inside(each, kind, state)));
        }

        /**
         * The scan past the end of what it is {@code inside} of, from {@code from}, as one token that starts at
         * {@code start}; to the end of the line, still inside it, when it is not closed on this line.
         */
        Scan inside(int start, int from, Inside inside) {
            Delimited delimited = inside.delimited();
            // From each escape on to the next, until there is no escape before the first close; the close is only
            // looked for again once an escape has stepped past it, so a string of many escapes is read once.
            Option<Integer> end = Iterator.iterate(
                            Tuple.of(
                                    line.indexOf(delimited.close(), from),
                                    delimited.escapes() ? line.indexOf('\\', from) : -1),
                            found -> Tuple.of(
                                    found._1 >= found._2 + 2 ? found._1 : line.indexOf(delimited.close(), found._2 + 2),
                                    line.indexOf('\\', found._2 + 2)))
                    .find(found -> found._1 < 0 || found._2 < 0 || found._2 > found._1)
                    .map(found -> found._1)
                    .filter(close -> close >= 0)
                    .map(close -> close + delimited.close().length());
            return end.map(close -> in(inside.after()).token(start, close, inside.kind()))
                    .getOrElse(() -> in(inside).token(start, line.length(), inside.kind()));
        }
    }

    /**
     * The tags of XML and HTML.
     */
    private static final class Markup {

        private Markup() {}

        /**
         * One step through text outside of a tag: past a comment, or the name of a tag, which goes into it.
         */
        static Scan text(Grammar grammar, Scan scan) {
            String line = scan.line();
            int at = scan.at();
            Option<Scan> comment = scan.delimited(grammar.blockComments(), Kind.COMMENT);
            if (comment.isDefined()) {
                return comment.get();
            } else if (line.charAt(at) != '<') {
                return scan.to(next(line, at + 1, "<"));
            }
            int name = at + 1 < line.length() && "/?!".indexOf(line.charAt(at + 1)) >= 0 ? at + 2 : at + 1;
            int end = IntStream.range(name, line.length())
                    .filter(i -> !wordPart(line.charAt(i)) && ":.-".indexOf(line.charAt(i)) < 0)
                    .findFirst()
                    .orElse(line.length());
            return end > name && Character.isLetter(line.charAt(name))
                    ? scan.in(new InTag()).token(name, end, Kind.KEYWORD)
                    : scan.to(next(line, at + 1, "<"));
        }

        /**
         * One step through a tag: past the value of an attribute, or out of the tag at its end.
         */
        static Scan tag(Grammar grammar, Scan scan) {
            if (scan.line().charAt(scan.at()) == '>') {
                return scan.in(new Outside()).to(scan.at() + 1);
            }
            return scan.delimited(grammar.strings(), Kind.STRING)
                    .getOrElse(() -> scan.to(next(scan.line(), scan.at() + 1, ">\"'")));
        }
    }

    /**
     * The blocks of Markdown, which are told apart at the start of a line, and the code and the comments in a line.
     */
    private static final class Markdown {

        private Markdown() {}

        /**
         * The scan of {@code line} past its start, from inside {@code state}: a fenced code block's lines are a
         * string, a fence included; a heading is a keyword; the markers of a quote and of a list item are too. A line
         * indented as code has none of them.
         */
        static Scan start(String line, State state) {
            Scan scan = new Scan(line, 0, state, List.empty());
            String stripped = line.stripLeading();
            int indent = line.length() - stripped.length();
            if (state instanceof InFence(String fence)) {
                Scan code = scan.token(0, line.length(), Kind.STRING);
                return stripped.startsWith(fence) ? code.in(new Outside()) : code;
            } else if (!(state instanceof Outside) || indent > 3) {
                return scan;
            }
            Option<String> fence = fence(stripped);
            if (fence.isDefined()) {
                return scan.in(new InFence(fence.get())).token(0, line.length(), Kind.STRING);
            } else if (heading(stripped)) {
                return scan.token(0, line.length(), Kind.KEYWORD);
            }
            return markers(scan.to(indent));
        }

        /**
         * The fence that opens a code block, if {@code stripped} starts with one: three or more backticks or tildes.
         */
        private static Option<String> fence(String stripped) {
            return Option.when(stripped.startsWith("```") || stripped.startsWith("~~~"), () -> {
                char mark = stripped.charAt(0);
                return stripped.substring(
                        0,
                        IntStream.range(0, stripped.length())
                                .filter(i -> stripped.charAt(i) != mark)
                                .findFirst()
                                .orElse(stripped.length()));
            });
        }

        private static boolean heading(String stripped) {
            int level = IntStream.range(0, stripped.length())
                    .filter(i -> stripped.charAt(i) != '#')
                    .findFirst()
                    .orElse(stripped.length());
            return level >= 1 && level <= 6 && (level == stripped.length() || stripped.charAt(level) == ' ');
        }

        /**
         * The scan past the markers of quotes and of a list item at the start of a line, each a keyword.
         */
        private static Scan markers(Scan scan) {
            String line = scan.line();
            int at = scan.at();
            if (at < line.length() && line.charAt(at) == '>') {
                return markers(scan.token(at, at + 1, Kind.KEYWORD).to(spaced(line, at + 1)));
            }
            int digits = IntStream.range(at, line.length())
                    .filter(i -> !Character.isDigit(line.charAt(i)))
                    .findFirst()
                    .orElse(line.length());
            int end =
                    digits > at && digits - at <= 9 && digits < line.length() && ".)".indexOf(line.charAt(digits)) >= 0
                            ? digits + 1
                            : at < line.length() && "-*+".indexOf(line.charAt(at)) >= 0 ? at + 1 : at;
            return end > at && (end == line.length() || line.charAt(end) == ' ')
                    ? scan.token(at, end, Kind.KEYWORD).to(spaced(line, end))
                    : scan;
        }

        private static int spaced(String line, int from) {
            return IntStream.range(from, line.length())
                    .filter(i -> line.charAt(i) != ' ')
                    .findFirst()
                    .orElse(line.length());
        }

        /**
         * One step through a line: past inline code, or a comment, or else one character.
         */
        static Scan inline(Grammar grammar, Scan scan) {
            String line = scan.line();
            int at = scan.at();
            Option<Scan> comment = scan.delimited(grammar.blockComments(), Kind.COMMENT);
            if (comment.isDefined()) {
                return comment.get();
            } else if (line.charAt(at) != '`') {
                return scan.to(next(line, at + 1, "`<"));
            }
            int run = IntStream.range(at, line.length())
                    .filter(i -> line.charAt(i) != '`')
                    .findFirst()
                    .orElse(line.length());
            String ticks = line.substring(at, run);
            Option<Integer> close = closingRun(line, run, ticks);
            return close.map(end -> scan.token(at, end, Kind.STRING)).getOrElse(() -> scan.to(run));
        }

        /**
         * The end of the first run of exactly {@code ticks} from {@code from} on.
         */
        private static Option<Integer> closingRun(String line, int from, String ticks) {
            return Iterator.iterate(line.indexOf(ticks, from), i -> line.indexOf(ticks, i + 1))
                    .takeWhile(i -> i >= 0)
                    .find(i -> (i == 0 || line.charAt(i - 1) != '`')
                            && (i + ticks.length() >= line.length() || line.charAt(i + ticks.length()) != '`'))
                    .map(i -> i + ticks.length());
        }
    }
}
