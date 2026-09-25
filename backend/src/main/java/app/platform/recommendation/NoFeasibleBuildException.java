package app.platform.recommendation;

/** No compatible build exists for the request, regardless of budget. The message is user-facing. */
public class NoFeasibleBuildException extends RuntimeException {

    public NoFeasibleBuildException(String message) {
        super(message);
    }
}
