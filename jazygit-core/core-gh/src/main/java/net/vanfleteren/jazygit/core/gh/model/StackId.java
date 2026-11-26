package net.vanfleteren.jazygit.core.gh.model;

/**
 * The number GitHub gives a stack of pull requests in its repository, by which its Stacks API names it (ADR 73): not
 * the number of any of its pull requests.
 */
public record StackId(int value) {}
