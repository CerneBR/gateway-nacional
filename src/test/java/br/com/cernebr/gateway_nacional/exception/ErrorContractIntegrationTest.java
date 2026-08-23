package br.com.cernebr.gateway_nacional.exception;

import br.com.cernebr.gateway_nacional.config.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Blinda o contrato de erros RFC 7807 contra a regressão mais insidiosa deste
 * projeto: o {@code @ExceptionHandler(Exception.class)} do
 * {@link GlobalExceptionHandler} tem precedência sobre o tratamento padrão do
 * Spring, então <em>qualquer</em> exceção de framework não mapeada
 * explicitamente vira HTTP 500.
 *
 * <p>O sintoma é traiçoeiro porque o gateway continua "funcionando": só que
 * erro de digitação do consumidor passa a ser reportado como falha interna do
 * servidor — poluindo alertas de observabilidade com ruído e mentindo para
 * quem está integrando sobre de quem é a culpa.</p>
 *
 * <p>Nenhum destes casos toca provider externo — todos falham no binding ou na
 * validação de entrada, antes de qualquer I/O — então o teste é determinístico
 * e não depende de stub no WireMock.</p>
 */
class ErrorContractIntegrationTest extends AbstractIntegrationTest {

    private static final String TYPE_VALIDATION =
            "https://api.gateway-nacional.com.br/errors/validation";
    private static final String TYPE_NOT_FOUND =
            "https://api.gateway-nacional.com.br/errors/resource-not-found";

    @Test
    @DisplayName("Rota inexistente deve devolver 404 — nunca 500")
    void unknownRouteShouldReturn404() throws Exception {
        mockMvc.perform(get("/rota/que/nao/existe/jamais"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.type").value(TYPE_NOT_FOUND));
    }

    @Test
    @DisplayName("Path quase-certo (prefixo errado) deve devolver 404, não 500")
    void almostCorrectPathShouldReturn404() throws Exception {
        // /api/v1/cfop/{codigo} não existe — o correto é /api/v1/fiscal/cfop/{codigo}.
        // Exatamente o erro que a documentação do portal induzia antes da correção.
        mockMvc.perform(get("/api/v1/cfop/5102"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("Parâmetro obrigatório ausente deve devolver 400 nomeando o parâmetro")
    void missingRequiredParamShouldReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/financeiro/boletos/parse"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.type").value(TYPE_VALIDATION))
                .andExpect(jsonPath("$.parameter").value("linha"));
    }

    @Test
    @DisplayName("Data em formato não-ISO deve devolver 400 informando o formato esperado")
    void malformedDateShouldReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/calendario/proximo-dia-util")
                        .param("data", "20240102")
                        .param("siglaUf", "SP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.type").value(TYPE_VALIDATION))
                .andExpect(jsonPath("$.parameter").value("data"))
                .andExpect(jsonPath("$.expectedType").value("LocalDate"));
    }

    @Test
    @DisplayName("IllegalArgumentException de validação em service deve virar 400, não 500")
    void serviceValidationShouldReturn400() throws Exception {
        // CvmFundosService rejeita page < 1 antes de qualquer chamada externa.
        mockMvc.perform(get("/api/v1/financeiro/cvm/fundos")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.type").value(TYPE_VALIDATION));
    }

    @Test
    @DisplayName("Verbo errado em rota existente deve devolver 405 com os métodos aceitos")
    void wrongHttpMethodShouldReturn405() throws Exception {
        mockMvc.perform(post("/api/v1/status"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.allowedMethods").exists());
    }
}
