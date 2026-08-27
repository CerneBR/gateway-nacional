package br.com.cernebr.gateway_nacional.licitacoes.controller;

import br.com.cernebr.gateway_nacional.exception.ResourceNotFoundException;
import br.com.cernebr.gateway_nacional.licitacoes.dto.FiltroAtivas;
import br.com.cernebr.gateway_nacional.licitacoes.dto.LicitacaoDetalheDTO;
import br.com.cernebr.gateway_nacional.licitacoes.dto.LicitacoesAtivasPage;
import br.com.cernebr.gateway_nacional.licitacoes.dto.Portal;
import br.com.cernebr.gateway_nacional.licitacoes.service.LicitacoesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Validated
@RestController
// Canônico é /api/v1/** (padrão dos demais 55 controllers); /v1/** fica como
// alias para não quebrar integrações que já apontam para o caminho antigo.
@RequestMapping({"/api/v1/licitacoes", "/v1/licitacoes"})
@Tag(
        name = "Licitações — GovTech",
        description = "Listagem unificada e detalhe de licitações públicas e privadas a partir do PNCP (inclui ComprasNet federal), BLL, BNC e Licitanet. Saídas normalizadas no contrato canônico LicitacaoResumoDTO / LicitacaoDetalheDTO; engine de resiliência aplica RefreshAheadCache (Soft 30m / Hard 12h) sobre cascata sequencial."
)
public class LicitacaoController {

    private static final String UF_REGEX = "^[A-Za-z]{2}$";
    // 'comprasnet' segue aceito como alias legado de 'pncp' (ver Portal.ALIASES).
    private static final String PORTAL_REGEX = "^(pncp|comprasnet|bll|bnc|licitanet)$";
    private static final String SORT_REGEX = "^(abertura|encerramento|valor|orgao)$";
    private static final String ORDER_REGEX = "^(asc|desc)$";
    private static final String MODALIDADE_REGEX = "^(pregao_eletronico|pregao_presencial|concorrencia|dispensa|inexigibilidade|leilao|concurso|dialogo_competitivo)$";

    private final LicitacoesService licitacoesService;

    public LicitacaoController(LicitacoesService licitacoesService) {
        this.licitacoesService = licitacoesService;
    }

    @GetMapping(value = "/ativas", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Listar licitações ativas dos 4 portais agregados",
            description = """
                    Agrega o estado corrente de licitações **publicadas/em \
                    propostas** do PNCP, Licitanet, BNC e BLL em \
                    um único contrato canônico. Filtros são opcionais e \
                    cumulativos.

                    **Engine de Resiliência:**
                    - `RefreshAheadCache` Soft 30m / Hard 12h — chaves quentes \
                      refrescam em background sem prejudicar o consumidor; \
                      cache distribuído via Redis para todos os pods.
                    - **Cascata sequencial** (não hedge) — protege a quota dos \
                      portais frágeis (BLL/BNC bloqueiam IPs sob martelagem). \
                      Falhas individuais NÃO interrompem a agregação: \
                      portais respondidos e falhos vêm separados no envelope, \
                      e o cliente decide se aceita parcial.
                    - **Circuit Breakers** por portal (`comprasnetCB`, `bllCB`, \
                      `bncCB`, `licitanetCB`) com janela deslizante de 10 \
                      chamadas — abre em 50% de falha e drena por 15s antes \
                      do half-open.

                    **Política de erros:**
                    - **200 com `portaisFalhos` populado** — degradação \
                      parcial (cenário comum, BLL costuma cair).
                    - **503 ProblemDetail** — TODOS os 4 portais falharam \
                      simultaneamente E não há cache stale para servir."""
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Página da listagem agregada (pode incluir portaisFalhos não-vazio em caso de degradação parcial)",
                    content = @Content(schema = @Schema(implementation = LicitacoesAtivasPage.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Filtro inválido (uf não-2-letras, portal desconhecido, modalidade fora do enum, page/size fora da faixa)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Todos os portais falharam e não há cache disponível",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<LicitacoesAtivasPage> listarAtivas(
            @Parameter(description = "Restringe a um único portal (slug). Default: agrega todos. 'comprasnet' é alias legado de 'pncp'.",
                    example = "pncp",
                    schema = @Schema(allowableValues = {"pncp", "bll", "bnc", "licitanet"}))
            @RequestParam(value = "portal", required = false)
            @Pattern(regexp = PORTAL_REGEX,
                    message = "portal deve ser um de: pncp, bll, bnc, licitanet (comprasnet aceito como alias legado).")
            String portal,

            @Parameter(description = "UF do órgão promotor (sigla 2 letras).", example = "SP")
            @RequestParam(value = "uf", required = false)
            @Pattern(regexp = UF_REGEX,
                    message = "uf deve ser uma sigla de 2 letras (ex.: SP).")
            String uf,

            @Parameter(description = "Modalidade canônica.",
                    example = "pregao_eletronico",
                    schema = @Schema(allowableValues = {
                            "pregao_eletronico", "pregao_presencial", "concorrencia",
                            "dispensa", "inexigibilidade", "leilao", "concurso", "dialogo_competitivo"
                    }))
            @RequestParam(value = "modalidade", required = false)
            @Pattern(regexp = MODALIDADE_REGEX,
                    flags = Pattern.Flag.CASE_INSENSITIVE,
                    message = "modalidade fora do vocabulário canônico — consulte o enum Modalidade.")
            String modalidade,

            @Parameter(description = "Busca por palavra-chave em objeto, número, órgão, município e identificador. "
                    + "Case- e acento-insensitive; todos os tokens precisam casar (AND).",
                    example = "notebook educacao")
            @RequestParam(value = "q", required = false)
            @Size(max = 120, message = "q deve ter no máximo 120 caracteres.")
            String q,

            @Parameter(description = "Índice da página (0-based).", example = "0")
            @RequestParam(value = "page", defaultValue = "0")
            @Min(value = 0, message = "page deve ser >= 0.")
            int pageIndex,

            @Parameter(description = "Itens por página (1..200).", example = "20")
            @RequestParam(value = "size", defaultValue = "20")
            @Min(value = 1, message = "size deve ser >= 1.")
            @Max(value = 200, message = "size deve ser <= 200.")
            int size,

            @Parameter(description = "Abertura da sessão a partir de (ISO-8601 UTC).",
                    example = "2026-06-01T00:00:00Z")
            @RequestParam(value = "aberturaDe", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime aberturaDe,

            @Parameter(description = "Abertura da sessão até (ISO-8601 UTC).",
                    example = "2026-06-30T23:59:59Z")
            @RequestParam(value = "aberturaAte", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime aberturaAte,

            @Parameter(description = "Valor estimado mínimo em BRL. Exclui licitações sem valor publicado.",
                    example = "50000")
            @RequestParam(value = "valorMin", required = false) BigDecimal valorMin,

            @Parameter(description = "Valor estimado máximo em BRL. Exclui licitações sem valor publicado.",
                    example = "500000")
            @RequestParam(value = "valorMax", required = false) BigDecimal valorMax,

            @Parameter(description = "Nome do órgão promotor (match parcial, acento-insensitive).",
                    example = "secretaria de educacao")
            @RequestParam(value = "orgao", required = false)
            @Size(max = 120, message = "orgao deve ter no máximo 120 caracteres.")
            String orgao,

            @Parameter(description = "Município sede do órgão (match parcial, acento-insensitive).",
                    example = "aracaju")
            @RequestParam(value = "municipio", required = false)
            @Size(max = 120, message = "municipio deve ter no máximo 120 caracteres.")
            String municipio,

            @Parameter(description = "Campo de ordenação.", example = "abertura",
                    schema = @Schema(allowableValues = {"abertura", "encerramento", "valor", "orgao"}))
            @RequestParam(value = "sort", defaultValue = "abertura")
            @Pattern(regexp = SORT_REGEX,
                    message = "sort deve ser um de: abertura, encerramento, valor, orgao.")
            String sort,

            @Parameter(description = "Direção da ordenação. Ausentes (null) ficam sempre ao fim.",
                    example = "asc",
                    schema = @Schema(allowableValues = {"asc", "desc"}))
            @RequestParam(value = "order", defaultValue = "asc")
            @Pattern(regexp = ORDER_REGEX, message = "order deve ser asc ou desc.")
            String order
    ) {
        FiltroAtivas filtro = new FiltroAtivas(q, aberturaDe, aberturaAte, valorMin, valorMax,
                orgao, municipio, sort, order, pageIndex, size);
        LicitacoesAtivasPage page = licitacoesService.listarAtivas(portal, uf, modalidade, filtro);
        // X-Cascade / X-Portais-Respondidos: a docs page do frontend usa
        // esses headers para acender o badge de degradação no topo da UI
        // sem precisar parsear o body. Mantemos o JSON intacto (campos
        // portaisRespondidos/portaisFalhos seguem para clients que
        // preferem o envelope).
        String respondidos = String.join(",", page.portaisRespondidos());
        String falhos = String.join(",", page.portaisFalhos());
        String cascade = page.portaisFalhos().isEmpty() ? "full" : "partial";
        return ResponseEntity.ok()
                .header("X-Cascade", cascade)
                .header("X-Portais-Respondidos", respondidos)
                .header("X-Portais-Falhos", falhos)
                .header("X-Total-Count", String.valueOf(page.total()))
                .header("X-Total-Pages", String.valueOf(page.totalPaginas()))
                .body(page);
    }

    @GetMapping(value = "/{portal}/{identificador}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Detalhe completo de uma licitação por portal e identificador",
            description = """
                    Resolve a licitação no portal indicado e devolve o \
                    contrato canônico **expandido** (itens, anexos, datas-marco \
                    e rótulo bruto da modalidade).

                    **Cache:** RAC Soft 2h / Hard 12h por chave \
                    `{portal}:{identificador}`. Detalhe muda menos que a \
                    listagem (anexos costumam ser estáveis após publicação), \
                    então o soft-TTL é mais largo.

                    **Identificadores aceitos por portal:**
                    - `pncp` (alias legado: `comprasnet`) — formato canônico `{cnpjOrgao}-{ano}-{sequencial}` \
                      (ex.: `00394460000141-2026-1230`). Veja `urlOriginal` na \
                      listagem para o slug exato.
                    - `bll`, `bnc`, `licitanet` — slug interno do portal, \
                      sempre exposto no campo `identificador` da listagem."""
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Detalhe resolvido",
                    content = @Content(schema = @Schema(implementation = LicitacaoDetalheDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Portal não suportado ou identificador em formato inválido",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Licitação não localizada no portal indicado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Portal indisponível ou Circuit Breaker aberto",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public LicitacaoDetalheDTO detalhe(
            @Parameter(description = "Slug do portal de origem", example = "pncp", required = true,
                    schema = @Schema(allowableValues = {"pncp", "bll", "bnc", "licitanet"}))
            @PathVariable("portal")
            @Pattern(regexp = PORTAL_REGEX,
                    message = "portal deve ser um de: pncp, bll, bnc, licitanet (comprasnet aceito como alias legado).")
            String portal,

            @Parameter(description = "Identificador interno do portal (ver campo identificador na listagem).",
                    example = "00394460000141-2026-1230", required = true)
            @PathVariable("identificador") String identificador
    ) {
        Portal p = Portal.fromSlug(portal)
                .orElseThrow(() -> new ResourceNotFoundException("portal",
                        "Portal '" + portal + "' não está coberto pelo gateway."));
        return licitacoesService.detalhe(p, identificador);
    }
}
