package org.project.api;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.project.model.Produto;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

/**
 * Catálogo e as três camadas de defesa da Parte 2 do guia:
 * CORS (de onde vem), trava por Origin do painel, e o crachá (quem é e qual o cargo).*/

class ProdutoApiTest extends ApiTestBase {

    private static Map<String, Object> produtoNovo() {
        return Map.of("nome", "Pastel de Queijo", "preco", 11.90, "caminhoImagem", "../imgs/img_pastelQueijo.jpeg");
    }

    //vitrine pública

    @Test
    void vitrineEhPublicaEMostraSoOsAtivos() {
        criarProduto("Pastel", "10.00", true);
        criarProduto("Produto Antigo", "5.00", false);

        given()
        .when().get("/api/produtos")
        .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("nome", hasItem("Pastel"))
                .body("nome", not(hasItem("Produto Antigo")));
    }

    //camada 1: CORS

    @Nested
    class Cors {

        @Test
        void siteDesconhecidoEhRecusado() {
            given().header("Origin", "https://site-falso.com")
            .when().get("/api/produtos")
            .then().statusCode(403);
        }

        @Test
        void preflightDaLojaEhAceitoComCookies() {
            given()
                    .header("Origin", LOJA)
                    .header("Access-Control-Request-Method", "POST")
            .when().options("/api/pedidos")
            .then()
                    .statusCode(200)
                    .header("Access-Control-Allow-Origin", LOJA)   // a origem exata, nunca "*"
                    .header("Access-Control-Allow-Credentials", "true");
        }
    }

    //camada 2: trava por Origin

    @Nested
    class TravaDoPainel {

        @Test
        void adminLogadoMasSemOriginEhRecusado() {
            String admin = criarAdminELogar("admin@loja.com");

            given().cookie("token", admin).contentType(ContentType.JSON).body(produtoNovo())
            .when().post("/api/produtos")
            .then()
                    .statusCode(403)
                    .body(equalTo("Erro: esta ação só é aceita pelo painel de administração."));
        }

        @Test
        void adminLogadoVindoDaLojaEhRecusado() {
            String admin = criarAdminELogar("admin@loja.com");

            given().cookie("token", admin).header("Origin", LOJA).contentType(ContentType.JSON).body(produtoNovo())
            .when().post("/api/produtos")
            .then().statusCode(403);
        }
    }

    //camada 3: crachá e cargo

    @Nested
    class Permissoes {

        @Test
        void semLoginDa401() {
            given().header("Origin", PAINEL).contentType(ContentType.JSON).body(produtoNovo())
            .when().post("/api/produtos")
            .then().statusCode(401);
        }

        @Test
        void clienteLogadoDa403() {
            String cliente = cadastrarCliente("maria@email.com");

            given().cookie("token", cliente).header("Origin", PAINEL).contentType(ContentType.JSON).body(produtoNovo())
            .when().post("/api/produtos")
            .then().statusCode(403);
        }

        @Test
        void listaCompletaDoAdminTambemEhProtegida() {
            String cliente = cadastrarCliente("maria@email.com");

            given().when().get("/api/produtos/admin").then().statusCode(401);
            given().cookie("token", cliente).when().get("/api/produtos/admin").then().statusCode(403);
        }
    }

    //ações do admin

    @Nested
    class AcoesDoAdmin {

        @Test
        void cadastrarDevolve201EIgnoraIdEAtivoDoCorpo() {
            String admin = criarAdminELogar("admin@loja.com");
            Map<String, Object> corpo = new java.util.HashMap<>(produtoNovo());
            corpo.put("id", 999);
            corpo.put("ativo", false);

            given().cookie("token", admin).header("Origin", PAINEL).contentType(ContentType.JSON).body(corpo)
            .when().post("/api/produtos")
            .then()
                    .statusCode(201)
                    .body("nome", equalTo("Pastel de Queijo"))
                    .body("ativo", equalTo(true))
                    .body("id", not(equalTo(999)));
        }

        @Test
        void imagemComJavascriptDa400() {
            String admin = criarAdminELogar("admin@loja.com");

            given().cookie("token", admin).header("Origin", PAINEL).contentType(ContentType.JSON)
                    .body(Map.of("nome", "Hack", "preco", 1, "caminhoImagem", "javascript:alert(1)"))
            .when().post("/api/produtos")
            .then()
                    .statusCode(400)
                    .body(equalTo("Erro: a imagem deve ser um endereço https:// ou um arquivo da pasta imgs (ex.: ../imgs/foto.jpeg)."));
        }

        @Test
        void desativarTiraDaVitrineMasNaoApaga() {
            String admin = criarAdminELogar("admin@loja.com");
            Produto pastel = criarProduto("Pastel", "10.00", true);

            given().cookie("token", admin).header("Origin", PAINEL)
            .when().delete("/api/produtos/" + pastel.getId())
            .then().statusCode(200).body("ativo", equalTo(false));

            given().when().get("/api/produtos").then().body("$", hasSize(0));

            given().cookie("token", admin)
            .when().get("/api/produtos/admin")
            .then().statusCode(200).body("nome", hasItem("Pastel"));
        }

        @Test
        void editarProdutoInexistenteDa404() {
            String admin = criarAdminELogar("admin@loja.com");

            given().cookie("token", admin).header("Origin", PAINEL).contentType(ContentType.JSON).body(produtoNovo())
            .when().put("/api/produtos/123456")
            .then()
                    .statusCode(404)
                    .body(equalTo("Erro: produto 123456 não encontrado."));
        }
    }
}
