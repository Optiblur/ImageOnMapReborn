package pl.iomreborn.image;

/** A user-facing error; the key points to a translated message in the lang files. */
public final class RenderException extends Exception {

    private final String key;
    private final transient Object[] args;

    public RenderException(String key, Object... args) {
        super(key, null, false, false);
        this.key = key;
        this.args = args;
    }

    public String getKey() {
        return key;
    }

    public Object[] getArgs() {
        return args;
    }
}
