package br.com.cernebr.gateway_nacional.licitacoes.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Envelope cacheável da listagem agregada.
 *
 * <p><b>Por que envelope (e não {@code List<LicitacaoResumoDTO>} direto):</b>
 * o {@code RefreshAheadCache} envolve o valor num {@code CachedEntry<T>},
 * e o {@code GenericJackson2JsonRedisSerializer} usado pelo Spring Data
 * Redis falha em deserializar coleções na raiz (default-typing não cobre
 * {@code List} root). Embrulhar num record concreto contorna a limitação —
 * o cache permanece operacional e o consumidor recebe a lista pelo método
 * acessor.</p>
 *
 * <p><b>Paginação:</b> o entry cacheado guarda o agregado <em>inteiro</em>
 * (ver {@code LicitacoesService#listarAtivas}); busca textual e recorte de
 * página são aplicados na saída do cache. Assim uma única chave Redis serve
 * todas as páginas e todos os termos de busca do mesmo recorte
 * portal/uf/modalidade — trocar de página não custa round-trip aos portais.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "LicitacoesAtivasPage",
        description = "Envelope da listagem agregada de licitações ativas — inclui metadados de coleta e de paginação.")
public record LicitacoesAtivasPage(
        @Schema(description = "Licitações da página corrente.")
        List<LicitacaoResumoDTO> resultados,
        @Schema(description = "Total de licitações que casam com os filtros (ANTES do recorte de página) — use para o contador da UI.",
                example = "47")
        int total,
        @Schema(description = "Índice da página corrente (0-based).", example = "0")
        int pagina,
        @Schema(description = "Tamanho de página solicitado.", example = "20")
        int tamanho,
        @Schema(description = "Quantidade de páginas disponíveis para o filtro corrente.", example = "3")
        int totalPaginas,
        @Schema(description = "Portais que responderam com sucesso.", example = "[\"comprasnet\", \"bll\"]")
        List<String> portaisRespondidos,
        @Schema(description = "Portais que falharam (CB aberto, timeout). Listagem segue mesmo com parcial; consumidor decide se aceita degradação.",
                example = "[\"bnc\"]")
        List<String> portaisFalhos,
        @Schema(description = "Instante de coleta upstream (UTC).", example = "2026-05-11T13:45:00Z")
        Instant coletadoEm
) {

    /**
     * Monta o envelope de uma página já recortada. {@code total} é a contagem
     * do conjunto filtrado completo, não o tamanho de {@code resultados}.
     */
    public static LicitacoesAtivasPage of(List<LicitacaoResumoDTO> resultados,
                                          int total,
                                          int pagina,
                                          int tamanho,
                                          List<String> portaisRespondidos,
                                          List<String> portaisFalhos,
                                          Instant coletadoEm) {
        int totalPaginas = tamanho > 0 ? (int) Math.ceil((double) total / tamanho) : 0;
        return new LicitacoesAtivasPage(resultados, total, pagina, tamanho, totalPaginas,
                portaisRespondidos, portaisFalhos, coletadoEm);
    }
}
