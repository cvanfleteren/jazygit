package net.vanfleteren.jazygit.content.diff;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.content.diff.RenderedTree.Place;
import net.vanfleteren.jazygit.json.NotJson;
import net.vanfleteren.jazygit.state.diff.Tree;
import net.vanfleteren.jazygit.ui.i18n.Messages;
import net.vanfleteren.jazygit.xml.NotXml;
import net.vanfleteren.jazygit.yaml.NotYaml;

/**
 * Why a version of a JSON, a YAML or an XML file is not rendered (ADR 122, 123, 151): what the line over its source
 * says, in jazygit's words, and where in it reading stopped, when that is known.
 */
final class TreeUnread {

    private TreeUnread() {}

    /**
     * Where reading a file stopped, when it says so.
     */
    static Option<Place> place(Tree.Unread unread) {
        return switch (unread) {
            case Tree.Unread.NoJson(NotJson.Broken(int line, int column, var found)) ->
                Option.of(new Place(line, column));
            case Tree.Unread.NoJson(NotJson.MoreAfter(int line)) -> Option.of(new Place(line, 1));
            case Tree.Unread.NoYaml(NotYaml.Broken(int line, int column, var problem)) ->
                Option.of(new Place(line, column));
            case Tree.Unread.NoXml(NotXml.Broken(int line, int column, var problem)) ->
                Option.of(new Place(line, column));
            default -> Option.none();
        };
    }

    /**
     * What the line over a file that could not be read says.
     */
    static String why(Tree.Unread unread) {
        return switch (unread) {
            case Tree.Unread.NoJson(NotJson not) -> why(not);
            case Tree.Unread.NoYaml(NotYaml not) -> why(not);
            case Tree.Unread.NoXml(NotXml not) -> why(not);
            case Tree.Unread.NoTree none -> Messages.get("content.json.nothing");
        };
    }

    /**
     * What the line over JSON that could not be read says.
     */
    static String why(NotJson not) {
        return switch (not) {
            case NotJson.Nothing nothing -> Messages.get("content.json.nothing");
            case NotJson.MoreAfter(int line) -> Messages.get("content.json.moreAfter", String.valueOf(line));
            case NotJson.TooDeep(int depth) -> Messages.get("content.json.tooDeep", String.valueOf(depth));
            case NotJson.Broken(int line, int column, var found) ->
                Messages.get(
                        "content.json.unexpected",
                        String.valueOf(line),
                        String.valueOf(column),
                        found.map(TreeUnread::token).getOrElse(() -> Messages.get("content.json.end")));
        };
    }

    /**
     * What the line over YAML that could not be read says: where SnakeYAML stopped, and what it missed, in its words
     * (ADR 123).
     */
    static String why(NotYaml not) {
        return switch (not) {
            case NotYaml.Nothing nothing -> Messages.get("content.yaml.nothing");
            case NotYaml.TooDeep(int depth) -> Messages.get("content.yaml.tooDeep", String.valueOf(depth));
            case NotYaml.Broken(int line, int column, String problem) ->
                Messages.get("content.yaml.broken", String.valueOf(line), String.valueOf(column), problem);
        };
    }

    /**
     * What the line over XML that is not well formed says: where reading stopped, and why, in jazygit's words
     * (ADR 151).
     */
    static String why(NotXml not) {
        return switch (not) {
            case NotXml.Nothing nothing -> Messages.get("content.xml.nothing");
            case NotXml.TooDeep(int depth) -> Messages.get("content.xml.tooDeep", String.valueOf(depth));
            case NotXml.Broken(int line, int column, NotXml.Problem problem) ->
                Messages.get("content.xml.broken", String.valueOf(line), String.valueOf(column), problem(problem));
        };
    }

    private static String problem(NotXml.Problem problem) {
        return switch (problem) {
            case NotXml.Problem.Expected(String what)
            when what.equals(NotXml.Problem.NAME) -> Messages.get("content.xml.expectedName");
            case NotXml.Problem.Expected(String what)
            when what.equals(NotXml.Problem.QUOTED) -> Messages.get("content.xml.expectedQuoted");
            case NotXml.Problem.Expected(String what) -> Messages.get("content.xml.expected", what);
            case NotXml.Problem.Closes(String open, String close) -> Messages.get("content.xml.closes", close, open);
            case NotXml.Problem.ClosesNothing(String close) -> Messages.get("content.xml.closesNothing", close);
            case NotXml.Problem.EndsInside(String name) -> Messages.get("content.xml.endsInside", name);
            case NotXml.Problem.Twice(String name) -> Messages.get("content.xml.twice", name);
            case NotXml.Problem.MoreAfter more -> Messages.get("content.xml.moreAfter");
            case NotXml.Problem.TextOutside outside -> Messages.get("content.xml.textOutside");
            case NotXml.Problem.LessThan less -> Messages.get("content.xml.lessThan");
            case NotXml.Problem.Ampersand ampersand -> Messages.get("content.xml.ampersand");
            case NotXml.Problem.DashesInComment dashes -> Messages.get("content.xml.dashesInComment");
            case NotXml.Problem.NoSpace none -> Messages.get("content.xml.noSpace");
            case NotXml.Problem.CdataEnd end -> Messages.get("content.xml.cdataEnd");
            case NotXml.Problem.Forbidden(int codePoint) ->
                Messages.get("content.xml.forbidden", String.format("U+%04X", codePoint));
            case NotXml.Problem.Declaration declaration -> Messages.get("content.xml.declaration");
        };
    }

    /**
     * How the line over JSON that could not be read names the {@code token} found: a string as one, a line break as
     * one, a character that draws nothing or draws badly, as a tab or a control character, by its code point, and
     * anything else in quotes as it is written.
     */
    private static String token(String token) {
        if (token.startsWith("\"")) {
            return Messages.get("content.json.string");
        }
        if (token.equals("\n")) {
            return Messages.get("content.json.lineBreak");
        }
        return token.codePoints().anyMatch(TreeUnread::unseen)
                ? Messages.get("content.json.character", String.format("U+%04X", token.codePointAt(0)))
                : "'" + token + "'";
    }

    /**
     * Whether {@code codePoint} draws nothing a reader sees, or draws badly in a terminal: white space, a space that
     * does not break, a control character or a format character, as a byte order mark.
     */
    private static boolean unseen(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || Character.isISOControl(codePoint)
                || Character.getType(codePoint) == Character.FORMAT;
    }
}
