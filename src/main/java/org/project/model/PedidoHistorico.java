package org.project.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Uma linha por mudança de status: o que mudou, de quê para quê, qual admin fez e quando.
 * Nunca é editada nem apagada pelo sistema — é o "livro de registro" do pedido.
 */
@Entity
@Table(name = "pedido_historico")
public class PedidoHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Column(nullable = false, length = 20)
    private String campo; // PAGAMENTO ou PROCESSAMENTO

    @Column(nullable = false, length = 20)
    private String de;

    @Column(nullable = false, length = 20)
    private String para;

    // Vem do token (authentication.getName()), nunca do corpo da requisição
    @Column(name = "admin_email", nullable = false, length = 150)
    private String adminEmail;

    @Column(nullable = false)
    private LocalDateTime data;

    public PedidoHistorico() {
    }

    public PedidoHistorico(Pedido pedido, String campo, String de, String para, String adminEmail, LocalDateTime data) {
        this.pedido = pedido;
        this.campo = campo;
        this.de = de;
        this.para = para;
        this.adminEmail = adminEmail;
        this.data = data;
    }

    public int getId() { return id; }
    public Pedido getPedido() { return pedido; }
    public String getCampo() { return campo; }
    public String getDe() { return de; }
    public String getPara() { return para; }
    public String getAdminEmail() { return adminEmail; }
    public LocalDateTime getData() { return data; }
}
