package net.vanfleteren.jazygit.state;

/**
 * The id of a scrollable area of the content panel, under which what the area shows is kept: where it is scrolled
 * to, the lines expanded in it (ADR 77) and the version of a file it shows (ADR 102). It is also the id of the element
 * that draws the area, which can have the focus.
 *
 * @param value the id
 */
public record AreaId(String value) {}
