package br.com.cernebr.gateway_nacional.licitacoes.custos.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Benchmark de custo homem/hora por município (IBGE) e ocupação (CBO).
 *
 * <p><b>ATENÇÃO — hoje isto é um PLACEHOLDER.</b> Os valores são gerados
 * sinteticamente a partir do hash de {@code ibge+cbo} (ver
 * {@code CustosMaoDeObraService}); não há mineração de contratos por trás.
 * Os campos {@code fonte} e {@code aviso} declaram isso explicitamente para
 * que nenhum consumidor precifique uma proposta em cima de número inventado.</p>
 */
@Schema(name = "CustoMaoDeObraDTO",
        description = "Benchmark de custo homem/hora. ATENÇÃO: implementação atual devolve dado SINTÉTICO — ver campo 'aviso'.")
public record CustoMaoDeObraDTO(
    @Schema(description = "Código IBGE do município consultado.", example = "3550308")
    String codigoIbge,
    @Schema(description = "Código CBO da ocupação consultada.", example = "782305")
    String codigoCbo,
    @Schema(description = "Valor homem/hora médio em BRL. SINTÉTICO na implementação atual.")
    BigDecimal valorHomemHoraMedio,
    @Schema(description = "Valor homem/hora máximo em BRL. SINTÉTICO na implementação atual.")
    BigDecimal valorHomemHoraMaximo,
    @Schema(description = "Competência de referência declarada.", example = "2026-05")
    String dataReferencia,
    @Schema(description = "Origem do dado. 'SINTETICO' enquanto o endpoint for placeholder.",
            example = "SINTETICO")
    String fonte,
    @Schema(description = "Aviso legível sobre a natureza do dado. Ausente quando a fonte for real.",
            example = "Valores gerados sinteticamente — placeholder, não usar para precificação.")
    String aviso
) {}
