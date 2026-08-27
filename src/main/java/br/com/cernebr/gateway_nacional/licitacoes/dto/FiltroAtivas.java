package br.com.cernebr.gateway_nacional.licitacoes.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Recorte aplicado sobre o agregado já cacheado de licitações ativas.
 *
 * <p><b>Por que esses filtros não entram na chave de cache:</b> portal/uf/
 * modalidade delimitam o que o gateway vai BUSCAR nos portais; os campos deste
 * record delimitam o que o consumidor quer VER do que já foi buscado. Colocá-los
 * na chave multiplicaria os entries no Redis por cada combinação digitada e
 * faria cada refinamento custar round-trip aos 4 portais — proibitivo para
 * BLL/BNC, que bloqueiam IP sob martelagem.</p>
 */
public record FiltroAtivas(
        String q,
        OffsetDateTime aberturaDe,
        OffsetDateTime aberturaAte,
        BigDecimal valorMin,
        BigDecimal valorMax,
        String orgao,
        String municipio,
        String sort,
        String order,
        Integer page,
        Integer size
) {

    /**
     * Desempate estável — sem ele, dois registros de mesma chave de ordenação
     * poderiam trocar de posição entre requisições e a paginação repetiria ou
     * perderia linhas na virada de página.
     */
    public static final Comparator<LicitacaoResumoDTO> DESEMPATE =
            Comparator.comparing(FiltroAtivas::portalSlugOuVazio)
                    .thenComparing(FiltroAtivas::identificadorOuVazio);

    /** Ordem do snapshot cacheado: abertura mais próxima primeiro, nulos ao fim. */
    public static final Comparator<LicitacaoResumoDTO> ORDEM_PADRAO =
            Comparator.<LicitacaoResumoDTO, OffsetDateTime>comparing(
                            LicitacaoResumoDTO::dataAbertura,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(DESEMPATE);

    public List<String> tokensBusca() {
        return TextoBusca.tokenizar(q);
    }

    /**
     * Comparator do campo pedido em {@code sort}, com direção de {@code order}.
     * Campos aceitos: {@code abertura} (default), {@code encerramento},
     * {@code valor}, {@code orgao}. Nulos vão sempre ao fim — ver {@link #direcao}.
     */
    public Comparator<LicitacaoResumoDTO> comparator() {
        boolean desc = order != null && "desc".equalsIgnoreCase(order.trim());
        String campo = sort == null ? "" : sort.trim().toLowerCase(Locale.ROOT);
        Comparator<LicitacaoResumoDTO> base = switch (campo) {
            case "encerramento" -> porData(LicitacaoResumoDTO::dataEncerramento, desc);
            case "valor" -> porValor(desc);
            case "orgao" -> porTexto(desc);
            default -> porData(LicitacaoResumoDTO::dataAbertura, desc);
        };
        return base.thenComparing(DESEMPATE);
    }

    private static Comparator<LicitacaoResumoDTO> porData(
            java.util.function.Function<LicitacaoResumoDTO, OffsetDateTime> chave, boolean desc) {
        return Comparator.comparing(chave, Comparator.nullsLast(FiltroAtivas.<OffsetDateTime>direcao(desc)));
    }

    private static Comparator<LicitacaoResumoDTO> porValor(boolean desc) {
        return Comparator.comparing(LicitacaoResumoDTO::valorEstimado,
                Comparator.nullsLast(FiltroAtivas.<BigDecimal>direcao(desc)));
    }

    private static Comparator<LicitacaoResumoDTO> porTexto(boolean desc) {
        Comparator<LicitacaoResumoDTO> asc =
                Comparator.comparing(FiltroAtivas::orgaoNomeNormalizado);
        return desc ? asc.reversed() : asc;
    }

    /**
     * Direção aplicada à CHAVE, nunca ao envelope {@code nullsLast}. Reverter o
     * comparador inteiro jogaria os ausentes para a frente e "maior valor
     * primeiro" devolveria uma página cheia de licitações sem valor publicado.
     */
    private static <T extends Comparable<? super T>> Comparator<T> direcao(boolean desc) {
        return desc ? Comparator.reverseOrder() : Comparator.naturalOrder();
    }

    private static String portalSlugOuVazio(LicitacaoResumoDTO r) {
        return r.portal() == null ? "" : r.portal().slug();
    }

    private static String identificadorOuVazio(LicitacaoResumoDTO r) {
        return r.identificador() == null ? "" : r.identificador();
    }

    private static String orgaoNomeNormalizado(LicitacaoResumoDTO r) {
        return r.orgao() == null ? "" : TextoBusca.normalizar(
                r.orgao().nome() == null ? "" : r.orgao().nome());
    }
}
