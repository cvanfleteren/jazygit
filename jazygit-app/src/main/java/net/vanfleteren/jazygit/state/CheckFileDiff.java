package net.vanfleteren.jazygit.state;

/**
 * Looks at the files the diff {@code load} is read from, so that it is read again only once they changed: that
 * takes a git process or more, this none (ADR 58).
 */
public record CheckFileDiff(LoadFileDiff load) implements Load {

    @Override
    public Msg run(Env env) {
        return new LoadMsg.FileDiffChecked(load, env.git().status().diffStamp(load.files()));
    }
}
