package org.project.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // READ_ONLY: o id aparece nas respostas, mas é IGNORADO se vier num JSON de entrada.
    // Segunda camada além dos DTOs: um "id" vindo do navegador nunca vira UPDATE de outro registro.
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private int id;

    // A data da compra é gerada automaticamente pelo java
    @CreationTimestamp
    @Column(name = "data_pedidos", updatable = false)
    private LocalDateTime dataPedido;

    @Column(name = "valor_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @Column(nullable = false, length = 50)
    private String status;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_pagamento", nullable = false, length = 20)
    private StatusPagamento statusPagamento = StatusPagamento.AGUARDANDO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_processamento", nullable = false, length = 20)
    private StatusProcessamento statusProcessamento = StatusProcessamento.NAO_PROCESSADO;

    @Column(name = "codigo_rastreio", length = 50)
    private String codigoRastreio;

    // --- Pagamento pelo gateway (Mercado Pago) ---

    @Column(name = "gateway_pagamento_id", length = 50, unique = true)
    private String gatewayPagamentoId;

    @Column(name = "data_pagamento")
    private LocalDateTime dataPagamento;

    @Column(name = "metodo_pagamento", nullable = false, length = 50)
    private String metodoPagamento; // Ex: "pix", "cartão de credito"

    // Relacionamento: Vários pedidos pertencem a Um Usuário
    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    // Relacionamento: O pedido precisa de Um Endereço de entrega
    @ManyToOne (cascade = CascadeType.ALL)
    @JoinColumn(name = "endereco_id", nullable = false)
    private Endereco endereco;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL)
    private List<ItemPedido> itens;

    public Pedido() {
    }

    // --- Getters e Setters ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public LocalDateTime getDataPedido() { return dataPedido; }
    public void setDataPedido(LocalDateTime dataPedido) { this.dataPedido = dataPedido; }

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public StatusPagamento getStatusPagamento() { return statusPagamento; }
    public void setStatusPagamento(StatusPagamento statusPagamento) { this.statusPagamento = statusPagamento; }

    public StatusProcessamento getStatusProcessamento() { return statusProcessamento; }
    public void setStatusProcessamento(StatusProcessamento statusProcessamento) { this.statusProcessamento = statusProcessamento; }

    public String getCodigoRastreio() { return codigoRastreio; }
    public void setCodigoRastreio(String codigoRastreio) { this.codigoRastreio = codigoRastreio; }

    public String getGatewayPagamentoId() { return gatewayPagamentoId; }
    public void setGatewayPagamentoId(String gatewayPagamentoId) { this.gatewayPagamentoId = gatewayPagamentoId; }

    public LocalDateTime getDataPagamento() { return dataPagamento; }
    public void setDataPagamento(LocalDateTime dataPagamento) { this.dataPagamento = dataPagamento; }

    public String getMetodoPagamento() { return metodoPagamento; }
    public void setMetodoPagamento(String metodoPagamento) { this.metodoPagamento = metodoPagamento; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public Endereco getEndereco() { return endereco; }
    public void setEndereco(Endereco endereco) { this.endereco = endereco; }

    public List<ItemPedido> getItens() { return itens; }
    public void setItens(List<ItemPedido> itens) { this.itens = itens; }
}
