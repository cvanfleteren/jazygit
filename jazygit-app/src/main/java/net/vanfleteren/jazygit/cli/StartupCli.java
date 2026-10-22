package net.vanfleteren.jazygit.cli;

import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.io.PrintWriter;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.UnmatchedArgumentException;

/**
 * The command line jazygit is started with.
 */
public final class StartupCli {

    private StartupCli() {}

    /**
     * How {@code args} ask jazygit to start, or the exit when they ask for the help or the version, which go to
     * {@code out}, or when they are wrong, which goes to {@code err} with the usage.
     */
    public static Either<Exit, StartupOptions> parse(String[] args, PrintWriter out, PrintWriter err) {
        CommandLine cli = new CommandLine(StartupArgs.class);
        return Try.of(() -> cli.parseArgs(args))
                .toEither()
                .mapLeft(problem -> rejected(cli, problem, err))
                .flatMap(parsed -> cli.isUsageHelpRequested()
                        ? Either.left(help(cli, out))
                        : cli.isVersionHelpRequested()
                                ? Either.left(version(cli, out))
                                : Either.right(options(cli.getCommand())));
    }

    private static Exit rejected(CommandLine cli, Throwable problem, PrintWriter err) {
        if (!(problem instanceof ParameterException wrong)) {
            throw new IllegalStateException(problem);
        }
        err.println(wrong.getMessage());
        if (!UnmatchedArgumentException.printSuggestions(wrong, err)) {
            cli.usage(err);
        }
        err.flush();
        return new Exit(cli.getCommandSpec().exitCodeOnInvalidInput());
    }

    private static Exit help(CommandLine cli, PrintWriter out) {
        cli.usage(out);
        out.flush();
        return new Exit(cli.getCommandSpec().exitCodeOnUsageHelp());
    }

    private static Exit version(CommandLine cli, PrintWriter out) {
        cli.printVersionHelp(out);
        out.flush();
        return new Exit(cli.getCommandSpec().exitCodeOnVersionHelp());
    }

    private static StartupOptions options(StartupArgs args) {
        return new StartupOptions(Option.ofOptional(args.path()), Option.ofOptional(args.theme()), args.debug());
    }

    /**
     * jazygit ends right away, after printing the help, the version or what was wrong with the arguments.
     *
     * @param code what the process exits with
     */
    public record Exit(int code) {}
}
