package net.vanfleteren.jazygit.process;

import io.vavr.collection.Iterator;

/**
 * The most of a program's output that is read: {@code lines} lines and {@code chars} characters (ADR 79). Only
 * {@code \n} ends a line: in git's output a {@code \r} is part of one.
 */
public record Limit(int lines, int chars) {

    /**
     * {@code text} whole when it is within this limit; else cut off after the last whole line that keeps it within,
     * which is empty when even its first line is too long.
     */
    public Limited cut(String text) {
        long ends = text.chars().filter(c -> c == '\n').count();
        if (!over(text.length(), ends, text.isEmpty() || text.endsWith("\n"))) {
            return new Limited(text, false);
        }
        int end = Iterator.iterate(text.indexOf('\n'), at -> text.indexOf('\n', at + 1))
                .takeWhile(at -> at >= 0)
                .map(at -> at + 1)
                .take(lines)
                .takeWhile(at -> at <= chars)
                .foldLeft(0, (last, at) -> at);
        return new Limited(text.substring(0, end), true);
    }

    /**
     * Whether text of {@code length} characters with {@code ends} line ends, which is {@code ended} when it ends with
     * one or is empty, is longer than this limit. Once it is, more text after it is too.
     */
    boolean over(int length, long ends, boolean ended) {
        return length > chars || ends + (ended ? 0 : 1) > lines;
    }
}
