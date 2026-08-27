package br.com.cernebr.gateway_nacional.licitacoes.custos.controller;

import br.com.cernebr.gateway_nacional.licitacoes.custos.dto.CustoMaoDeObraDTO;
import br.com.cernebr.gateway_nacional.licitacoes.custos.service.CustosMaoDeObraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/licitacoes/custos-mao-de-obra")
@Tag(name = "Licitações — GovTech",
        description = "Benchmark de custo homem/hora. ATENÇÃO: implementação atual é placeholder sintético.")
public class CustosMaoDeObraController {

    private final CustosMaoDeObraService service;

    public CustosMaoDeObraController(CustosMaoDeObraService service) {
        this.service = service;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Custo homem/hora por município e ocupação (PLACEHOLDER SINTÉTICO)",
            description = """
                    **Este endpoint ainda não consulta dado real.** Os valores são \
                    derivados do hash de `ibge`+`cbo` — estáveis para a mesma \
                    consulta, mas inventados.

                    A resposta declara isso em `fonte` (`SINTETICO`) e `aviso`, e \
                    a chamada devolve o header `X-Data-Source: synthetic`. Não use \
                    para precificar proposta enquanto a fonte real não existir; \
                    veja o Javadoc de `CustosMaoDeObraService` para o que falta.""")
    public ResponseEntity<CustoMaoDeObraDTO> getCustoMaoDeObra(
            @Parameter(description = "Código IBGE do município.", example = "3550308", required = true)
            @RequestParam("ibge") String ibge,
            @Parameter(description = "Código CBO da ocupação.", example = "782305", required = true)
            @RequestParam("cbo") String cbo) {
        // Header além do campo no body: um consumidor que só olha status+headers
        // (proxy, cache, painel de saúde) também precisa enxergar que o dado
        // não é real, sem parsear o JSON.
        return ResponseEntity.ok()
                .header("X-Data-Source", "synthetic")
                .body(service.obterCustoMaoDeObra(ibge, cbo));
    }
}
