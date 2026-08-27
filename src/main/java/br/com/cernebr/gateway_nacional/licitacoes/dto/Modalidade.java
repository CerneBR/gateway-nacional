package br.com.cernebr.gateway_nacional.licitacoes.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Locale;
import java.util.Optional;

/**
 * Modalidades canônicas de contratação. Espelha a Lei 14.133/2021 e os
 * rótulos comuns nos portais privados (BLL, BNC, Licitanet) — sempre que
 * possível remapeamos para um vocabulário único antes de devolver ao cliente.
 *
 * <p>O slug é usado como filtro na rota de listagem
 * ({@code ?modalidade=pregao_eletronico}). Modalidades não mapeadas caem em
 * {@link #DESCONHECIDA} preservando a string original em
 * {@link LicitacaoDetalheDTO#modalidadeOriginal()}.</p>
 */
@Schema(name = "Modalidade",
        description = "Modalidade canônica de contratação pública (NLLC 14.133/2021 + equivalentes nos portais privados).")
public enum Modalidade {

    PREGAO_ELETRONICO("pregao_eletronico"),
    PREGAO_PRESENCIAL("pregao_presencial"),
    CONCORRENCIA("concorrencia"),
    DISPENSA("dispensa"),
    INEXIGIBILIDADE("inexigibilidade"),
    LEILAO("leilao"),
    CONCURSO("concurso"),
    DIALOGO_COMPETITIVO("dialogo_competitivo"),
    DESCONHECIDA("desconhecida");

    private final String slug;

    Modalidade(String slug) {
        this.slug = slug;
    }

    /**
     * Valor emitido no JSON. Sem {@code @JsonValue} o Jackson serializaria o
     * nome da constante ({@code "PREGAO_ELETRONICO"}), divergindo do slug que
     * o Swagger documenta e que o filtro {@code ?modalidade=} aceita.
     */
    @JsonValue
    @Schema(description = "Slug canônico — usado em ?modalidade=…", example = "pregao_eletronico")
    public String slug() {
        return slug;
    }

    public static Optional<Modalidade> fromSlug(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (Modalidade m : values()) {
            if (m.slug.equals(normalized)) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }

    /**
     * Mapeia rótulos brutos comuns nos portais ("Pregão Eletrônico — SRP",
     * "PE-15/2026", "DISPENSA ELETRÔNICA") para o vocabulário canônico.
     * Heurística por prefixo: nunca lança — devolve {@link #DESCONHECIDA}
     * quando não há match.
     */
    /** Desserialização tolerante — aceita o slug canônico ou o nome da constante. */
    @JsonCreator
    static Modalidade fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return fromSlug(raw).orElseGet(() -> {
            for (Modalidade m : values()) {
                if (m.name().equalsIgnoreCase(raw.trim())) {
                    return m;
                }
            }
            return DESCONHECIDA;
        });
    }

    public static Modalidade infer(String raw) {
        if (raw == null) return DESCONHECIDA;
        String n = raw.trim().toLowerCase(Locale.ROOT);
        if (n.contains("pregão") || n.contains("pregao") || n.startsWith("pe-") || n.startsWith("pe ")) {
            if (n.contains("presencial")) return PREGAO_PRESENCIAL;
            return PREGAO_ELETRONICO;
        }
        if (n.contains("concorrência") || n.contains("concorrencia")) return CONCORRENCIA;
        if (n.contains("dispensa")) return DISPENSA;
        if (n.contains("inexigib")) return INEXIGIBILIDADE;
        if (n.contains("leilão") || n.contains("leilao")) return LEILAO;
        if (n.contains("concurso")) return CONCURSO;
        if (n.contains("diálogo") || n.contains("dialogo")) return DIALOGO_COMPETITIVO;
        return DESCONHECIDA;
    }
}
