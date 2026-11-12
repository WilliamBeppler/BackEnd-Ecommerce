package org.project.api;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.project.model.Pedido;
import org.project.model.Produto;
import org.project.pagamento.PagamentoException;
import org.project.pagamento.PreferenciaResposta;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Checkout, "meus pedidos", pedido de outra pessoa (IDOR) e o painel de pedidos. */

class PedidoApiTest extends ApiTestBase {

    private static final String LINK = "https://mercadopago.exemplo/pagar/123";

    private void mercadoPagoFunciona() {
        when(mercadoPago.criarPreferencia(any(), any())).thenReturn(new PreferenciaResposta("pref-1", LINK, null));
    }

    private int comprar(String token, int produtoId, int quantidade) {
        return given().cookie("token", token).header("Origin", LOJA).contentType(ContentType.JSON)
                .body(corpoPedido(produtoId, quantidade))
        .when().post("/api/pedidos")
        .then().statusCode(200)
                .extract().path("pedidoId");
    }

    //checkout

    @Nested
    class Checkout {

        @Test
        void semLoginDa401() {
            Produto pastel = criarProduto("Pastel", "12.50", true);

            given().header("Origin", LOJA).contentType(ContentType.JSON).body(corpoPedido(pastel.getId(), 1))
            .when().post("/api/pedidos")
            .then().statusCode(401);
        }

        @Test
        void pedidoCertoDevolveOLinkDePagamento() {
            mercadoPagoFunciona();
            String maria = cadastrarCliente("maria@email.com");
            Produto pastel = criarProduto("Pastel", "12.50", true);

            given().cookie("token", maria).header("Origin", LOJA).contentType(ContentType.JSON)
                    .body(corpoPedido(pastel.getId(), 2))
            .when().post("/api/pedidos")
            .then()
                    .statusCode(200)
                    .body("linkPagamento", equalTo(LINK))
                    .body("$", not(hasKey("erro")));
        }

        @Test
        void precoETotalMandadosPeloNavegadorSaoIgnorados() {
            // Ataque da Parte 27: mandar "preco" e "valorTotal" de um centavo
            mercadoPagoFunciona();
            String maria = cadastrarCliente("maria@email.com");
            Produto pastel = criarProduto("Pastel", "12.50", true);

            Map<String, Object> corpo = Map.of(
                    "endereco", enderecoValido(),
                    "valorTotal", 0.01,
                    "status", "PAGO",
                    "itens", List.of(Map.of("produtoId", pastel.getId(), "quantidade", 2, "preco", 0.01)));

            int id = given().cookie("token", maria).header("Origin", LOJA).contentType(ContentType.JSON).body(corpo)
                    .when().post("/api/pedidos")
                    .then().statusCode(200).extract().path("pedidoId");

            Pedido salvo = pedidoRepository.findById(id).orElseThrow();
            assertThat(salvo.getValorTotal()).isEqualByComparingTo("25.00"); // 2 x 12.50, do banco
            assertThat(salvo.getStatusPagamento().name()).isEqualTo("AGUARDANDO");
        }

        @Test
        void mercadoPagoForaDoArDa502MasOPedidoFicaSalvo() {
            when(mercadoPago.criarPreferencia(any(), any())).thenThrow(new PagamentoException("Mercado Pago indisponível.", null));
            String maria = cadastrarCliente("maria@email.com");
            Produto pastel = criarProduto("Pastel", "12.50", true);

            given().cookie("token", maria).header("Origin", LOJA).contentType(ContentType.JSON)
                    .body(corpoPedido(pastel.getId(), 1))
            .when().post("/api/pedidos")
            .then()
                    .statusCode(502)
                    .body("erro", equalTo("Não foi possível abrir o pagamento agora. Seu pedido foi guardado; tente novamente em instantes."));

            assertThat(pedidoRepository.count()).isEqualTo(1);
        }

        @Test
        void quantidadeAcimaDe99Da400() {
            String maria = cadastrarCliente("maria@email.com");
            Produto pastel = criarProduto("Pastel", "12.50", true);

            given().cookie("token", maria).header("Origin", LOJA).contentType(ContentType.JSON)
                    .body(corpoPedido(pastel.getId(), 100))
            .when().post("/api/pedidos")
            .then()
                    .statusCode(400)
                    .body(equalTo("Erro: quantidade inválida. Use de 1 a 99."));

            assertThat(pedidoRepository.count()).isZero();
        }

        @Test
        void produtoDesativadoDa400() {
            String maria = cadastrarCliente("maria@email.com");
            Produto antigo = criarProduto("Pamonha", "9.00", false);

            given().cookie("token", maria).header("Origin", LOJA).contentType(ContentType.JSON)
                    .body(corpoPedido(antigo.getId(), 1))
            .when().post("/api/pedidos")
            .then().statusCode(400);
        }
    }

    //pedidos do cliente

    @Nested
    class PedidosDoCliente {

        @Test
        void cadaClienteVeSoOsProprios() {
            mercadoPagoFunciona();
            String maria = cadastrarCliente("maria@email.com");
            String joao = cadastrarCliente("joao@email.com");
            Produto pastel = criarProduto("Pastel", "12.50", true);
            int pedidoDaMaria = comprar(maria, pastel.getId(), 2);

            given().cookie("token", maria).when().get("/api/pedidos/meus")
            .then().statusCode(200)
                    .body("$", hasSize(1))
                    .body("[0].id", equalTo(pedidoDaMaria))
                    .body("[0].itens[0].produto", equalTo("Pastel"))
                    .body("[0]", not(hasKey("endereco")));   // nada de endereço nem CPF aqui

            given().cookie("token", joao).when().get("/api/pedidos/meus")
            .then().statusCode(200).body("$", hasSize(0));
        }

        @Test
        void situacaoDoProprioPedido() {
            mercadoPagoFunciona();
            String maria = cadastrarCliente("maria@email.com");
            int id = comprar(maria, criarProduto("Pastel", "12.50", true).getId(), 1);

            given().cookie("token", maria).when().get("/api/pedidos/" + id + "/situacao")
            .then().statusCode(200)
                    .body("pedidoId", equalTo(id))
                    .body("statusPagamento", equalTo("AGUARDANDO"));
        }

        @Test
        void pedidoDeOutraPessoaDa404() {
            // IDOR: João troca o número na URL tentando ver o pedido da Maria
            mercadoPagoFunciona();
            String maria = cadastrarCliente("maria@email.com");
            String joao = cadastrarCliente("joao@email.com");
            int pedidoDaMaria = comprar(maria, criarProduto("Pastel", "12.50", true).getId(), 1);

            given().cookie("token", joao).when().get("/api/pedidos/" + pedidoDaMaria + "/situacao")
            .then().statusCode(404).body(equalTo("Erro: pedido não encontrado."));

            given().cookie("token", joao).header("Origin", LOJA)
            .when().post("/api/pedidos/" + pedidoDaMaria + "/pagamento")
            .then().statusCode(404);
        }
    }

    //painel de pedidos (admin)

    @Nested
    class PainelDePedidos {

        @Test
        void clienteNaoAcessaOPainelMesmoVindoDoPainel() {
            String maria = cadastrarCliente("maria@email.com");

            given().cookie("token", maria).header("Origin", PAINEL)
            .when().get("/api/admin/pedidos")
            .then().statusCode(403);
        }

        @Test
        void adminSemOriginDoPainelNaoLeOsPedidos() {
            // As leituras do painel também têm a trava: as respostas trazem dados pessoais
            String admin = criarAdminELogar("admin@loja.com");

            given().cookie("token", admin)
            .when().get("/api/admin/pedidos")
            .then().statusCode(403);
        }

        @Test
        void adminListaEMarcaComoPago() {
            mercadoPagoFunciona();
            String maria = cadastrarCliente("maria@email.com");
            int id = comprar(maria, criarProduto("Pastel", "12.50", true).getId(), 1);
            String admin = criarAdminELogar("admin@loja.com");

            given().cookie("token", admin).header("Origin", PAINEL)
            .when().get("/api/admin/pedidos?aba=aguardando")
            .then().statusCode(200)
                    .body("itens.id", hasItem(id))
                    .body("contadores.AGUARDANDO", equalTo(1));

            given().cookie("token", admin).header("Origin", PAINEL).contentType(ContentType.JSON)
                    .body(Map.of("status", "PAGO"))
            .when().put("/api/admin/pedidos/" + id + "/pagamento")
            .then().statusCode(200)
                    .body("statusPagamento", equalTo("PAGO"))
                    .body("historico[0].adminEmail", equalTo("admin@loja.com")) // quem mudou vem do token
                    .body("acoesPermitidas", equalTo(List.of("PROCESSAR", "CANCELAR")));
        }

        @Test
        void transicaoProibidaDa400() {
            mercadoPagoFunciona();
            String maria = cadastrarCliente("maria@email.com");
            int id = comprar(maria, criarProduto("Pastel", "12.50", true).getId(), 1);
            String admin = criarAdminELogar("admin@loja.com");

            given().cookie("token", admin).header("Origin", PAINEL).contentType(ContentType.JSON)
                    .body(Map.of("status", "PROCESSADO"))
            .when().put("/api/admin/pedidos/" + id + "/processamento")
            .then().statusCode(400)
                    .body(equalTo("Erro: Só pedidos pagos podem ser processados."));
        }
    }
}
