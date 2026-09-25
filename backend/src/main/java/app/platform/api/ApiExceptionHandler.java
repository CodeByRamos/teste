package app.platform.api;

import app.platform.recommendation.NoFeasibleBuildException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * Maps failures to RFC 9457 problem documents with plain-language messages. Internal details are logged,
 * never returned.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail invalidInput(Exception exception) {
        log.debug("Rejected request: {}", exception.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Dados inválidos",
                "Alguma informação enviada está incompleta ou fora do formato esperado. Revise e tente de novo.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Não foi possível montar a configuração", exception.getMessage());
    }

    @ExceptionHandler(NoFeasibleBuildException.class)
    ProblemDetail noBuild(NoFeasibleBuildException exception) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Nenhuma configuração encontrada", exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        log.error("Unexpected error", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Algo deu errado",
                "Tivemos um problema inesperado. Tente novamente em instantes.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
