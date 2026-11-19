package net.vanfleteren.jazygit.gh.model;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * The builds on some commits of a branch on GitHub, read in one call (ADR 131).
 *
 * @param builds  the builds, of the newest commit first
 * @param commits how many commits were read, with or without builds
 * @param next    where the commits after these are read from; none when these are the oldest
 */
public record BuildsPage(List<Build> builds, int commits, Option<String> next) {}
