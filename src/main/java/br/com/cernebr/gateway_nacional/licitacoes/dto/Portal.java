package br.com.cernebr.gateway_nacional.licitacoes.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Locale;
import java.util.Optional;

/**
 * Portais de licitações cobertos pelo Gateway.
 *
 * <p><b>Sobre o nome PNCP:</b> a fonte federal não é o comprasnet.gov.br
 * legado — é o <b>Portal Nacional de Compras Públicas</b>, que a Lei
 * 14.133/2021 tornou repositório obrigatório de toda contratação pública e
 * que já agrega ComprasNet federal, BBMNet e portais estaduais integrados
 * (ver {@code ComprasNetClient}, que consome {@code pncp.gov.br}). O slug
 * canônico é portanto {@code pncp}; {@code comprasnet} segue aceito na
 * ENTRADA como alias legado, mas a SAÍDA sempre traz {@code pncp}.</p>
 */
@Schema(name = "Portal",
        description = "Portais de licitações cobertos pelo Gateway GovTech. Aceita 'comprasnet' na entrada como alias legado de 'pncp'.")
public enum Portal {
    PNCP("pncp", "PNCP — Portal Nacional de Compras Públicas (inclui ComprasNet federal)"),
    BLL("bll", "Bolsa de Licitações e Leilões"),
    BNC("bnc", "Bolsa Nacional de Compras"),
    LICITANET("licitanet", "Licitanet");

    /** Slugs antigos ainda aceitos na entrada — chave: alias, valor: constante. */
    private static final java.util.Map<String, Portal> ALIASES =
            java.util.Map.of("comprasnet", PNCP);

    private final String slug;
    private final String descricao;

    Portal(String slug, String descricao) {
        this.slug = slug;
        this.descricao = descricao;
    }

    /**
     * Valor emitido no JSON. Sem este {@code @JsonValue} o Jackson serializaria
     * o nome da constante ({@code "PNCP"}) e o corpo divergiria do contrato
     * publicado no Swagger e nas rotas, que usam o slug minúsculo.
     */
    @JsonValue
    @Schema(description = "Identificador estável usado nas rotas REST.", example = "pncp")
    public String slug() {
        return slug;
    }

    @Schema(description = "Nome humano do portal — exibido em respostas.", example = "PNCP — Portal Nacional de Compras Públicas (inclui ComprasNet federal)")
    public String descricao() {
        return descricao;
    }

    public static Optional<Portal> fromSlug(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        Portal alias = ALIASES.get(normalized);
        if (alias != null) {
            return Optional.of(alias);
        }
        for (Portal p : values()) {
            if (p.slug.equals(normalized) || p.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /** Desserialização tolerante — aceita slug canônico, alias legado ou nome da constante. */
    @JsonCreator
    static Portal fromJson(String raw) {
        return fromSlug(raw).orElse(null);
    }
}
