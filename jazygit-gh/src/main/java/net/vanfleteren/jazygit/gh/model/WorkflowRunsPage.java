package net.vanfleteren.jazygit.gh.model;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * Some runs of a workflow on every branch, read in one call, as builds (ADR 131).
 *
 * @param builds the runs of an event that builds the code, the newest first
 * @param runs   how many runs were read, of any event
 * @param next   where the older runs are read from; none when these are the oldest
 */
public record WorkflowRunsPage(List<Build> builds, int runs, Option<String> next) {}
