package app.platform.intake;

import app.platform.recommendation.TargetResolution;
import app.platform.recommendation.UseCase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IntakeServiceTest {

    private static IntakeService with(ModelReading reading) {
        return new IntakeService(text -> Optional.of(reading));
    }

    @Test
    void withoutAModelTheRulesAnswerAlone() {
        NeedsInterpreter.Interpretation result = new IntakeService(NeedsReader.NONE).interpret("PC de 5 mil pra jogar valorant");

        assertThat(result.modelAssisted()).isFalse();
        assertThat(result.budgetBrl()).isEqualByComparingTo("5000");
    }

    @Test
    void theModelFillsWhatTheRulesCouldNotRead() {
        // "cinco mil" in words and "Valorant" are beyond the rules; the model reads them.
        ModelReading reading = new ModelReading(new BigDecimal("5000"), Set.of(UseCase.GAMING_COMPETITIVE), TargetResolution.QHD, false, false);

        NeedsInterpreter.Interpretation result = with(reading).interpret("uns cinco mil conto pra jogar valorant no meu monitor novo");

        assertThat(result.modelAssisted()).isTrue();
        assertThat(result.budgetBrl()).isEqualByComparingTo("5000");
        // The rules had only guessed heavy games from "jogar"; the model's specific reading replaces the guess.
        assertThat(result.useCases()).containsExactly(UseCase.GAMING_COMPETITIVE);
        assertThat(result.questions()).noneMatch(question -> question.contains("Que tipo de jogo"));
        assertThat(result.resolution()).isEqualTo(TargetResolution.QHD);
        // What the person reads is written by our code, from structured fields.
        assertThat(result.understood()).contains("Orçamento de até R$ 5.000", "Uso: jogos competitivos");
    }

    @Test
    void anInventedBudgetIsNotAcceptedWhenTheTextHasNoNumber() {
        ModelReading reading = new ModelReading(new BigDecimal("3000"), Set.of(UseCase.OFFICE_STUDY), null, false, false);

        NeedsInterpreter.Interpretation result = with(reading).interpret("quero um pc barato pra estudar");

        assertThat(result.budgetBrl()).isNull();
        assertThat(result.questions()).anyMatch(question -> question.contains("Quanto você pretende investir"));
    }

    @Test
    void theRulesWinWhenBothReadTheSameThing() {
        ModelReading reading = new ModelReading(new BigDecimal("9000"), Set.of(), TargetResolution.UHD_4K, false, false);

        NeedsInterpreter.Interpretation result = with(reading).interpret("PC de 6 mil para jogar em 1440p");

        assertThat(result.budgetBrl()).isEqualByComparingTo("6000");
        assertThat(result.resolution()).isEqualTo(TargetResolution.QHD);
    }
}
