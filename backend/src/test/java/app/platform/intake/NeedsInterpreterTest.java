package app.platform.intake;

import app.platform.recommendation.TargetResolution;
import app.platform.recommendation.UseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class NeedsInterpreterTest {

    @Test
    void readsBudgetAndUsesFromFreeText() {
        var result = NeedsInterpreter.interpret("Quero um PC de até 6 mil para jogar, programar e editar vídeo.");

        assertThat(result.budgetBrl()).isEqualByComparingTo("6000");
        assertThat(result.useCases()).containsExactlyInAnyOrder(UseCase.GAMING_AAA, UseCase.PROGRAMMING, UseCase.VIDEO_EDITING);
        assertThat(result.questions()).anyMatch(q -> q.contains("tipo de jogo"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Tenho R$ 5.000                          | 5000",
            "orçamento de 4500 reais                 | 4500",
            "uns 3,5 mil                             | 3500",
            "até 8k                                  | 8000",
            "gastar 7000                             | 7000"})
    void recognizesBudgetFormats(String text, String expected) {
        assertThat(NeedsInterpreter.interpret(text).budgetBrl()).isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    void doesNotMistakeModelNumbersForBudget() {
        var result = NeedsInterpreter.interpret("Já tenho uma RTX 4060 e 32 GB de RAM, quero jogar em 1440p");

        assertThat(result.budgetBrl()).isNull();
        assertThat(result.mentionsOwnedParts()).isTrue();
        assertThat(result.resolution()).isEqualTo(TargetResolution.QHD);
        assertThat(result.questions()).anyMatch(q -> q.contains("investir"));
    }

    @Test
    void recognizesCompetitiveGamesAndDocker() {
        var result = NeedsInterpreter.interpret("quero jogar valorant e cs2 e usar docker");

        assertThat(result.useCases()).containsExactlyInAnyOrder(UseCase.GAMING_COMPETITIVE, UseCase.CONTAINERS_VMS);
    }

    @Test
    void saysSoWhenAGameHasNoOfficialPcRequirements() {
        var result = NeedsInterpreter.interpret("Quero jogar GTA VI com 7 mil");

        assertThat(result.useCases()).contains(UseCase.GAMING_AAA);
        assertThat(result.understood()).anyMatch(line -> line.contains("requisitos oficiais"));
    }
}
