package br.com.cernebr.gateway_nacional.licitacoes.custos.service;

import br.com.cernebr.gateway_nacional.licitacoes.custos.dto.CustoMaoDeObraDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

/**
 * Benchmark de custo homem/hora — <b>PLACEHOLDER</b>.
 *
 * <p>Não existe mineração de contratos por trás deste endpoint. Os valores
 * saem de um {@link Random} semeado pelo hash de {@code ibge+cbo}: são
 * estáveis para a mesma consulta (o que faz parecer um dado real em teste),
 * mas são inventados.</p>
 *
 * <p>A versão anterior devolvia esses números com {@code fonte="PNCP/ComprasNet"} —
 * um número aleatório carimbado como dado oficial, no exato endpoint que um
 * usuário consultaria para montar preço de proposta. A resposta agora se
 * declara sintética em {@code fonte} e {@code aviso}, e o controller marca
 * {@code X-Data-Source: synthetic}.</p>
 *
 * <p><b>Para implementar de verdade</b> seria preciso: (1) mapear CBO →
 * itens/serviços contratados, que hoje não existe no schema analítico;
 * (2) ler {@code valorUnitarioHomologado} da fase de resultados do PNCP
 * (já ingerido em {@code participacao}); (3) agregar por município do órgão.
 * Enquanto (1) não existir, o endpoint não tem como sair do placeholder.</p>
 */
@Service
public class CustosMaoDeObraService {

    /** Marcador da natureza do dado — grep-ável e óbvio no payload. */
    public static final String FONTE_SINTETICA = "SINTETICO";

    private static final String AVISO =
            "Valores gerados sinteticamente a partir de ibge+cbo — placeholder de "
                    + "desenvolvimento, NÃO derivado de contratos do PNCP. Não usar para "
                    + "precificação de proposta.";

    public CustoMaoDeObraDTO obterCustoMaoDeObra(String ibge, String cbo) {
        Random random = new Random(ibge.hashCode() + cbo.hashCode());
        double medio = 25.0 + random.nextInt(150);
        BigDecimal valorMedio = BigDecimal.valueOf(medio).setScale(2, RoundingMode.HALF_UP);
        BigDecimal valorMaximo = BigDecimal.valueOf(medio * 1.4).setScale(2, RoundingMode.HALF_UP);
        return new CustoMaoDeObraDTO(ibge, cbo, valorMedio, valorMaximo, "2026-05",
                FONTE_SINTETICA, AVISO);
    }
}
