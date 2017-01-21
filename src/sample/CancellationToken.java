package sample;

/**
 * @author naiemk
 */
public class CancellationToken {
    boolean cancelled;

    public CancellationToken(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }
}
