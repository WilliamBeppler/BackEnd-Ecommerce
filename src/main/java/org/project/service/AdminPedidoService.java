package org.project.service;

import org.project.dto.admin.MudarStatusRequest;
import org.project.dto.admin.PaginaPedidos;
import org.project.dto.admin.PedidoDetalhe;
import org.project.dto.admin.PedidoResumo;
import org.project.model.Pedido;
import org.project.model.PedidoHistorico;
import org.project.model.StatusPagamento;
import org.project.model.StatusProcessamento;
import org.project.repository.PedidoHistoricoRepository;
import org.project.repository.PedidoRepository;
import org.project.service.FiltroPedidos.Aba;
import org.project.service.FiltroPedidos.Busca;
import org.project.service.excecao.NaoEncontradoException;
import org.project.service.excecao.RegraDeNegocioException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/*** Regras dos pedidos no painel de admin: lista com abas e busca, detalhe e mudança
 * dos dois status (com histórico). As regras de transição ficam no RegrasPedido.*/

@Service
public class AdminPedidoService {

    public static final int TAMANHO_PADRAO = 20;
    private static final int TAMANHO_MAXIMO = 50;
    private static final int RASTREIO_MAXIMO = 50;

    private final PedidoRepository pedidoRepository;
    private final PedidoHistoricoRepository historicoRepository;

    public AdminPedidoService(PedidoRepository pedidoRepository, PedidoHistoricoRepository historicoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.historicoRepository = historicoRepository;
    }

    /** Lista paginada, filtrada pela aba e pela busca, com o contador de cada aba. */
    @Transactional(readOnly = true)
    public PaginaPedidos listar(String aba, String busca, int pagina, int tamanho) {
        Aba abaEscolhida = FiltroPedidos.interpretarAba(aba);
        if (abaEscolhida == null) {
            throw new RegraDeNegocioException("Erro: aba inválida.");
        }

        Busca filtro = FiltroPedidos.interpretarBusca(busca);
        int paginaSegura = Math.max(0, pagina);
        int tamanhoSeguro = Math.min(Math.max(1, tamanho), TAMANHO_MAXIMO);

        // Mais recentes primeiro; o id desempata pedidos feitos no mesmo segundo
        PageRequest pedido = PageRequest.of(paginaSegura, tamanhoSeguro,
                Sort.by(Sort.Direction.DESC, "dataPedido").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<Pedido> resultado = pedidoRepository.buscarParaPainel(
                abaEscolhida.pagamento, abaEscolhida.processamento, filtro.id(), filtro.texto(), pedido);

        return new PaginaPedidos(
                resultado.getContent().stream().map(PedidoResumo::de).toList(),
                paginaSegura,
                tamanhoSeguro,
                resultado.getTotalPages(),
                resultado.getTotalElements(),
                contadores());
    }

    @Transactional(readOnly = true)
    public PedidoDetalhe detalhe(int id) {
        return montarDetalhe(buscar(id));
    }

    @Transactional
    public PedidoDetalhe mudarPagamento(int id, MudarStatusRequest dados, String autor) {
        Pedido pedido = buscar(id);

        StatusPagamento novo = lerEnum(StatusPagamento.class, dados == null ? null : dados.status());
        if (novo == null) {
            throw new RegraDeNegocioException("Erro: status de pagamento inválido.");
        }

        StatusPagamento atual = pedido.getStatusPagamento();
        String bloqueio = RegrasPedido.motivoBloqueioPagamento(atual, pedido.getStatusProcessamento(), novo);
        if (bloqueio != null) {
            throw new RegraDeNegocioException("Erro: " + bloqueio);
        }

        pedido.setStatusPagamento(novo);
        registrar(pedido, "PAGAMENTO", atual.name(), novo.name(), autor);
        return montarDetalhe(pedidoRepository.save(pedido));
    }

    @Transactional
    public PedidoDetalhe mudarProcessamento(int id, MudarStatusRequest dados, String autor) {
        Pedido pedido = buscar(id);

        StatusProcessamento novo = lerEnum(StatusProcessamento.class, dados == null ? null : dados.status());
        if (novo == null) {
            throw new RegraDeNegocioException("Erro: status de processamento inválido.");
        }

        StatusProcessamento atual = pedido.getStatusProcessamento();
        String bloqueio = RegrasPedido.motivoBloqueioProcessamento(pedido.getStatusPagamento(), atual, novo);
        if (bloqueio != null) {
            throw new RegraDeNegocioException("Erro: " + bloqueio);
        }

        if (novo == StatusProcessamento.PROCESSADO) {
            String rastreio = dados.codigoRastreio() == null ? "" : dados.codigoRastreio().trim();
            if (rastreio.length() > RASTREIO_MAXIMO) {
                throw new RegraDeNegocioException(
                        "Erro: o código de rastreio pode ter no máximo " + RASTREIO_MAXIMO + " caracteres.");
            }
            pedido.setCodigoRastreio(rastreio.isEmpty() ? null : rastreio);
        } else {
            pedido.setCodigoRastreio(null);
        }

        pedido.setStatusProcessamento(novo);
        registrar(pedido, "PROCESSAMENTO", atual.name(), novo.name(), autor);
        return montarDetalhe(pedidoRepository.save(pedido));
    }

    // ---------- apoio ----------

    private Pedido buscar(int id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new NaoEncontradoException("Erro: pedido " + id + " não encontrado."));
    }

    private Map<String, Long> contadores() {
        Map<String, Long> contadores = new LinkedHashMap<>();
        for (Aba aba : Aba.values()) {
            long total;
            if (aba.pagamento == null) {
                total = pedidoRepository.count();
            } else if (aba.processamento == null) {
                total = pedidoRepository.countByStatusPagamento(aba.pagamento);
            } else {
                total = pedidoRepository.countByStatusPagamentoAndStatusProcessamento(aba.pagamento, aba.processamento);
            }
            contadores.put(aba.name(), total);
        }
        return contadores;
    }

    private PedidoDetalhe montarDetalhe(Pedido pedido) {
        return PedidoDetalhe.de(pedido, historicoRepository.findByPedidoIdOrderByDataDescIdDesc(pedido.getId()));
    }

    // Grava no histórico. Está dentro da mesma transação da mudança de status:
    // ou os dois ficam salvos, ou nenhum dos dois.
    private void registrar(Pedido pedido, String campo, String de, String para, String autor) {
        historicoRepository.save(new PedidoHistorico(pedido, campo, de, para, autor, LocalDateTime.now()));
    }

    private <E extends Enum<E>> E lerEnum(Class<E> tipo, String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return Enum.valueOf(tipo, valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
