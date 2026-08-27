package br.com.cernebr.gateway_nacional.licitacoes.dto;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalização de texto compartilhada entre o filtro e os predicados de busca.
 *
 * <p>Existe como utilitário (e não como método privado do service) porque o
 * termo de busca é tokenizado UMA vez na borda — ao montar o
 * {@link FiltroAtivas} — enquanto o lado do registro é normalizado por linha
 * durante a filtragem. As duas pontas precisam usar exatamente a mesma
 * transformação, senão o match silenciosamente não fecha.</p>
 */
public final class TextoBusca {

    private static final Pattern DIACRITICOS = Pattern.compile("\p{InCombiningDiacriticalMarks}+");
    private static final Pattern ESPACOS = Pattern.compile("\s+");

    private TextoBusca() {
    }

    /** Minúsculas + remoção de acentos: os portais publicam sem padrão de acentuação. */
    public static String normalizar(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String nfd = Normalizer.normalize(raw, Normalizer.Form.NFD);
        return DIACRITICOS.matcher(nfd).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** Quebra o termo em tokens normalizados; lista vazia quando não há busca. */
    public static List<String> tokenizar(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return Arrays.stream(ESPACOS.split(normalizar(q)))
                .filter(t -> !t.isBlank())
                .toList();
    }
}
