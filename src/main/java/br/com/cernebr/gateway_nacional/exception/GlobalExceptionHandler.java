package br.com.cernebr.gateway_nacional.exception;

import br.com.cernebr.gateway_nacional.cadastral.isbn.exception.IsbnInvalidoException;
import br.com.cernebr.gateway_nacional.financeiro.boletos.exception.BoletoInvalidoException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Centralized exception handler. Converts internal exceptions into RFC 7807
 * ProblemDetail responses, ensuring stack traces and infrastructure details
 * never leak to the client. Sensitive context is logged server-side with a
 * correlation id that is also returned to the caller for support tracing.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final URI TYPE_VALIDATION       = URI.create("https://api.gateway-nacional.com.br/errors/validation");
    private static final URI TYPE_RESOURCE_UNAVAIL = URI.create("https://api.gateway-nacional.com.br/errors/resource-unavailable");
    private static final URI TYPE_RESOURCE_NOTFOUND = URI.create("https://api.gateway-nacional.com.br/errors/resource-not-found");
    private static final URI TYPE_INTERNAL         = URI.create("https://api.gateway-nacional.com.br/errors/internal");
    private static final URI TYPE_METHOD_NOT_ALLOWED = URI.create("https://api.gateway-nacional.com.br/errors/method-not-allowed");

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                HttpServletRequest request) {
        String allowed = ex.getSupportedHttpMethods() != null
                ? ex.getSupportedHttpMethods().toString()
                : "desconhecido";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.METHOD_NOT_ALLOWED,
                "Método '" + ex.getMethod() + "' não permitido para este endpoint. Use: " + allowed
        );
        problem.setTitle("Método não permitido");
        problem.setType(TYPE_METHOD_NOT_ALLOWED);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", OffsetDateTime.now());
        problem.setProperty("allowedMethods", allowed);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldErrorPayload)
                .toList();

        log.warn("Validation failure on {} {}: {} field(s) invalid",
                request.getMethod(), request.getRequestURI(), fieldErrors.size());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "A requisição contém campos inválidos. Verifique os erros e tente novamente."
        );
        problem.setTitle("Requisição inválida");
        problem.setType(TYPE_VALIDATION);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", OffsetDateTime.now());
        problem.setProperty("errors", fieldErrors);

        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleMethodValidation(HandlerMethodValidationException ex,
                                                                HttpServletRequest request) {
        List<Map<String, String>> fieldErrors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> Map.of(
                                "field", result.getMethodParameter().getParameterName() != null
                                        ? result.getMethodParameter().getParameterName()
                                        : "param",
                                "message", error.getDefaultMessage() != null
                                        ? error.getDefaultMessage()
                                        : "Valor inválido."
                        )))
                .toList();

        log.warn("Method validation failure on {} {}: {} param(s) invalid",
                request.getMethod(), request.getRequestURI(), fieldErrors.size());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "A requisição contém parâmetros inválidos. Verifique os erros e tente novamente."
        );
        problem.setTitle("Requisição inválida");
        problem.setType(TYPE_VALIDATION);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", OffsetDateTime.now());
        problem.setProperty("errors", fieldErrors);

        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * Handles bean-validation failures triggered by {@code @Validated} on
     * controller method parameters (e.g., {@code @PathVariable @Pattern},
     * {@code @RequestParam @NotBlank @Size}). Spring lobs these as
     * {@link ConstraintViolationException} when the violation surfaces on
     * a method argument rather than a {@code @RequestBody}; without an
     * explicit handler, the catch-all maps them to a misleading 500.
     *
     * <p>Returning 400 with the offending field path and message keeps the
     * contract symmetric with {@link MethodArgumentNotValidException}
     * (which handles {@code @Valid @RequestBody}) and gives the consumer
     * actionable feedback.</p>
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
                                                                   HttpServletRequest request) {
        List<Map<String, String>> fieldErrors = ex.getConstraintViolations().stream()
                .map(this::toConstraintViolationPayload)
                .toList();

        log.warn("Constraint violation on {} {}: {} field(s) invalid",
                request.getMethod(), request.getRequestURI(), fieldErrors.size());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "A requisição contém parâmetros inválidos. Verifique os erros e tente novamente."
        );
        problem.setTitle("Requisição inválida");
        problem.setType(TYPE_VALIDATION);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", OffsetDateTime.now());
        problem.setProperty("errors", fieldErrors);

        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ResourceUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleResourceUnavailable(ResourceUnavailableException ex,
                                                                   HttpServletRequest request) {
        String traceId = newTraceId();

        log.error("[traceId={}] Upstream provider '{}' unavailable on {} {}: {}",
                traceId, ex.getProviderName(), request.getMethod(), request.getRequestURI(),
                ex.getMessage(), ex);

        // Surface the provider's own message as the ProblemDetail "detail" —
        // every {@link ResourceUnavailableException} message in this codebase
        // is human-targeted and actionable (e.g., "DATASUS recusou a conexão
        // interna — estabelecimento sem APS"). Hiding it behind a generic
        // "tente novamente" denies the consumer the information needed to
        // decide between "retry" and "fix the query". A static fallback is
        // still used when {@code ex.getMessage()} is empty or null.
        String specificDetail = ex.getMessage();
        if (specificDetail == null || specificDetail.isBlank()) {
            specificDetail = "O provedor de dados está temporariamente indisponível. Tente novamente em alguns instantes.";
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                specificDetail
        );
        problem.setTitle("Serviço indisponível");
        problem.setType(TYPE_RESOURCE_UNAVAIL);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", OffsetDateTime.now());
        problem.setProperty("traceId", traceId);
        problem.setProperty("provider", ex.getProviderName());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFound(ResourceNotFoundException ex,
                                                                HttpServletRequest request) {
        log.info("Resource not found ({}): {} {} → {}",
                ex.getResourceType(), request.getMethod(), request.getRequestURI(), ex.getMessage());

        // Same propagation rule as the 503 handler: surface the specific
        // message ("NCM 9999.99.99 não consta no catálogo Mercosul") so the
        // consumer learns *what* is missing, not just that *something* is.
        String detail = ex.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = "Recurso não encontrado.";
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, detail);
        problem.setTitle("Recurso não encontrado");
        problem.setType(TYPE_RESOURCE_NOTFOUND);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", OffsetDateTime.now());
        problem.setProperty("resourceType", ex.getResourceType());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    /**
     * Boletos com DAC inválido (módulo 10/11 FEBRABAN) ou comprimento fora
     * do layout. Trata como problema do input do cliente — 400 — e devolve
     * a mensagem do parser, que já vem com a posição/campo do dígito que
     * falhou ("Campo 2: DV mod-10 inválido (esperado 7, recebido 3).").
     */
    @ExceptionHandler(BoletoInvalidoException.class)
    public ResponseEntity<ProblemDetail> handleBoletoInvalido(BoletoInvalidoException ex,
                                                              HttpServletRequest request) {
        log.info("Boleto inválido em {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Linha digitável inválida");
        problem.setType(TYPE_VALIDATION);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", OffsetDateTime.now());

        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(IsbnInvalidoException.class)
    public ResponseEntity<ProblemDetail> handleIsbnInvalido(IsbnInvalidoException ex,
                                                            HttpServletRequest request) {
        log.info("ISBN inválido em {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("ISBN inválido");
        problem.setType(TYPE_VALIDATION);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", OffsetDateTime.now());

        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(br.com.cernebr.gateway_nacional.saude.sigtap.etl.SigtapEtlException.class)
    public ResponseEntity<ProblemDetail> handleSigtapEtl(
            br.com.cernebr.gateway_nacional.saude.sigtap.etl.SigtapEtlException ex,
            HttpServletRequest request) {
        String traceId = newTraceId();

        log.error("[traceId={}] SIGTAP ETL failed on {} {}: {}",
                traceId, request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);

        String detail = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "Falha no pipeline de ingestão do SIGTAP. Verifique conectividade com o FTP do DataSUS.";

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, detail);
        problem.setTitle("Falha na sincronização SIGTAP");
        problem.setType(TYPE_RESOURCE_UNAVAIL);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", OffsetDateTime.now());
        problem.setProperty("traceId", traceId);
        problem.setProperty("provider", "DataSUS/FTP");

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    /**
     * Parâmetro obrigatório ausente na query string. Sem este handler o
     * consumidor que esquece {@code ?linha=...} recebe "erro interno, a
     * equipe técnica foi notificada" — mentindo sobre a origem da falha e
     * disparando alerta falso na observabilidade.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParam(MissingServletRequestParameterException ex,
                                                            HttpServletRequest request) {
        log.info("Parâmetro obrigatório ausente em {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getParameterName());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Parâmetro obrigatório ausente: '" + ex.getParameterName()
                        + "' (tipo " + ex.getParameterType() + ")."
        );
        problem.setTitle("Parâmetro obrigatório ausente");
        problem.setType(TYPE_VALIDATION);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", OffsetDateTime.now());
        problem.setProperty("parameter", ex.getParameterName());

        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * Valor no formato errado — tipicamente data fora do padrão ISO
     * ({@code 20240102} em vez de {@code 2024-01-02}) ou número não
     * parseável. É erro do cliente, não do gateway.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                             HttpServletRequest request) {
        Class<?> required = ex.getRequiredType();
        String tipo = required != null ? required.getSimpleName() : "desconhecido";

        log.info("Tipo inválido em {} {}: parâmetro '{}' recebeu '{}' (esperado {})",
                request.getMethod(), request.getRequestURI(), ex.getName(), ex.getValue(), tipo);

        // Datas são de longe o caso mais comum — dá o formato esperado em vez
        // de só dizer "LocalDate", que não ajuda quem está integrando.
        String dica = switch (tipo) {
            case "LocalDate" -> " Use o formato ISO-8601: aaaa-MM-dd (ex.: 2024-01-02).";
            case "LocalDateTime" -> " Use o formato ISO-8601: aaaa-MM-ddTHH:mm:ss.";
            case "Integer", "Long", "int", "long" -> " Informe um número inteiro.";
            case "BigDecimal", "Double", "double" -> " Informe um número decimal com ponto (ex.: 10.50).";
            default -> "";
        };

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Valor inválido para '" + ex.getName() + "': '" + ex.getValue()
                        + "' não é um(a) " + tipo + " válido(a)." + dica
        );
        problem.setTitle("Formato de parâmetro inválido");
        problem.setType(TYPE_VALIDATION);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", OffsetDateTime.now());
        problem.setProperty("parameter", ex.getName());
        problem.setProperty("expectedType", tipo);

        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * Validação de domínio feita manualmente nos services (paginação fora de
     * faixa, IBGE ausente, dígito verificador inválido...). Em todo o projeto
     * {@code IllegalArgumentException} é lançada exclusivamente para entrada
     * malformada do cliente — nunca para estado interno inconsistente — então
     * 400 é a tradução correta.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex,
                                                                HttpServletRequest request) {
        log.info("Argumento inválido em {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        String detail = ex.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = "Parâmetro inválido.";
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Parâmetro inválido");
        problem.setType(TYPE_VALIDATION);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", OffsetDateTime.now());

        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * Rota inexistente. Sem este handler a exceção cai no catch-all
     * {@code Exception.class} abaixo e vira 500 — mascarando um simples
     * erro de digitação do consumidor como falha do gateway, poluindo o
     * log de erros e contradizendo o contrato 404 publicado no portal.
     *
     * <p>Registrado explicitamente porque um {@code @RestControllerAdvice}
     * com handler para {@code Exception} tem precedência sobre o
     * tratamento padrão que o Spring daria à {@link NoResourceFoundException}.</p>
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ProblemDetail> handleNoHandler(Exception ex, HttpServletRequest request) {
        log.info("Rota não mapeada: {} {}", request.getMethod(), request.getRequestURI());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Rota não encontrada: " + request.getMethod() + " " + request.getRequestURI()
                        + ". Consulte a documentação em https://cernebr.dev.br/docs."
        );
        problem.setTitle("Rota não encontrada");
        problem.setType(TYPE_RESOURCE_NOTFOUND);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", OffsetDateTime.now());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = newTraceId();

        log.error("[traceId={}] Unhandled exception on {} {}",
                traceId, request.getMethod(), request.getRequestURI(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro interno inesperado. A equipe técnica foi notificada."
        );
        problem.setTitle("Erro interno");
        problem.setType(TYPE_INTERNAL);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", OffsetDateTime.now());
        problem.setProperty("traceId", traceId);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private Map<String, String> toFieldErrorPayload(FieldError error) {
        return Map.of(
                "field", error.getField(),
                "message", error.getDefaultMessage() != null
                        ? error.getDefaultMessage()
                        : "Valor inválido."
        );
    }

    /**
     * Maps a {@link ConstraintViolation} to the same {field, message} shape
     * the other validation handlers emit. The "field" is taken from the
     * leaf node of the property path (e.g., for "findByCodigo.codigo" we
     * surface only "codigo" — the method name is server-side noise that
     * leaks the controller signature unnecessarily).
     */
    private Map<String, String> toConstraintViolationPayload(ConstraintViolation<?> violation) {
        String fullPath = violation.getPropertyPath().toString();
        int lastDot = fullPath.lastIndexOf('.');
        String leafField = lastDot >= 0 ? fullPath.substring(lastDot + 1) : fullPath;
        return Map.of(
                "field", leafField,
                "message", violation.getMessage() != null ? violation.getMessage() : "Valor inválido."
        );
    }

    private String newTraceId() {
        return UUID.randomUUID().toString();
    }
}
