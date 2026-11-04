package org.project.dto.admin;

import java.util.List;
import java.util.Map;

/**
 * Uma página da lista: os pedidos desta página, onde estamos, quantas páginas existem
 * e o contador de cada aba (os numerozinhos ao lado do nome das abas).
 */
public record PaginaPedidos(List<PedidoResumo> itens,
                            int pagina,
                            int tamanho,
                            int totalPaginas,
                            long totalItens,
                            Map<String, Long> contadores) {
}
