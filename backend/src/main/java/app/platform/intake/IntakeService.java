package app.platform.intake;

/**
 * Interprets what the person wrote. The deterministic rules always run; a language model, when configured,
 * only helps fill what the rules could not read. Any model failure silently falls back to the rules.
 */
public final class IntakeService {

    private final NeedsReader reader;

    public IntakeService(NeedsReader reader) {
        this.reader = reader;
    }

    public NeedsInterpreter.Interpretation interpret(String text) {
        return reader.read(text)
                .map(reading -> NeedsInterpreter.combine(text, reading))
                .orElseGet(() -> NeedsInterpreter.interpret(text));
    }
}
