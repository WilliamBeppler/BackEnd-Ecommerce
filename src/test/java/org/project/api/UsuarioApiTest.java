package org.project.api;

import io.restassured.http.ContentType;
import io.restassured.http.Cookie;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/** Cadastro, login, "quem sou eu" e logout, pela porta da frente (HTTP). */
class UsuarioApiTest extends ApiTestBase {

    //cadastro

    @Test
    void cadastroDevolveCookieHttpOnlyEOsDadosPublicos() {
        Response resposta = given()
                .contentType(ContentType.JSON)
                .body(dadosCadastro("maria@email.com"))
        .when()
                .post("/api/usuarios/registrar");

        resposta.then()
                .statusCode(200)
                .body("email", equalTo("maria@email.com"))
                .body("role", equalTo("CLIENTE"))
                .body("$", not(hasKey("senha")))
                .body("$", not(hasKey("cpf")))
                .body("$", not(hasKey("telefone")));

        // O crachá vai num cookie que o JavaScript NÃO consegue ler (HttpOnly)
        Cookie cookie = resposta.getDetailedCookie("token");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getValue()).isNotBlank();
    }

    @Test
    void ninguemSeCadastraComoAdminMandandoRoleNoCorpo() {
        Map<String, Object> corpo = new HashMap<>(dadosCadastro("espertinho@email.com"));
        corpo.put("role", "ADMIN");
        corpo.put("id", 1);

        given()
                .contentType(ContentType.JSON)
                .body(corpo)
        .when()
                .post("/api/usuarios/registrar")
        .then()
                .statusCode(200)
                .body("role", equalTo("CLIENTE"));
    }

    @Test
    void emailRepetidoDa400() {
        cadastrarCliente("maria@email.com");

        given()
                .contentType(ContentType.JSON)
                .body(dadosCadastro("maria@email.com"))
        .when()
                .post("/api/usuarios/registrar")
        .then()
                .statusCode(400)
                .body(equalTo("Erro: Este e-mail já está cadastrado na loja."));
    }

    @Test
    void cadastroInvalidoDa400ComAMensagem() {
        Map<String, Object> corpo = new HashMap<>(dadosCadastro("maria@email.com"));
        corpo.put("senha", "123");

        given()
                .contentType(ContentType.JSON)
                .body(corpo)
        .when()
                .post("/api/usuarios/registrar")
        .then()
                .statusCode(400)
                .body(equalTo("Erro: a senha deve ter no mínimo 8 caracteres."));
    }

    //login

    @Test
    void loginCertoDevolveCookie() {
        cadastrarCliente("maria@email.com");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "maria@email.com", "senha", SENHA))
        .when()
                .post("/api/usuarios/login")
        .then()
                .statusCode(200)
                .cookie("token", notNullValue())
                .body("email", equalTo("maria@email.com"));
    }

    @Test
    void senhaErradaEEmailInexistenteDaoO401ComAMesmaMensagem() {
        cadastrarCliente("joana@email.com");

        given().contentType(ContentType.JSON)
                .body(Map.of("email", "joana@email.com", "senha", "senhaErrada"))
        .when().post("/api/usuarios/login")
        .then().statusCode(401).body(equalTo("Erro: e-mail ou senha inválidos."));

        given().contentType(ContentType.JSON)
                .body(Map.of("email", "naoexiste@email.com", "senha", "qualquerCoisa"))
        .when().post("/api/usuarios/login")
        .then().statusCode(401).body(equalTo("Erro: e-mail ou senha inválidos."));
    }

    @Test
    void cincoErrosSeguidosBloqueiamOEmailCom429() {
        cadastrarCliente("bloqueio@email.com");

        for (int i = 0; i < 5; i++) {
            given().contentType(ContentType.JSON)
                    .body(Map.of("email", "bloqueio@email.com", "senha", "errada" + i))
            .when().post("/api/usuarios/login")
            .then().statusCode(401);
        }

        // Agora nem a senha CERTA entra: o bloqueio vem antes da conferência
        given().contentType(ContentType.JSON)
                .body(Map.of("email", "bloqueio@email.com", "senha", SENHA))
        .when().post("/api/usuarios/login")
        .then()
                .statusCode(429)
                .header("Retry-After", notNullValue());
    }

    //sessão

    @Test
    void meSemCookieDa401() {
        given().when().get("/api/usuarios/me").then().statusCode(401);
    }

    @Test
    void meComCookieDevolveQuemEstaLogado() {
        String token = cadastrarCliente("maria@email.com");

        given().cookie("token", token)
        .when().get("/api/usuarios/me")
        .then()
                .statusCode(200)
                .body("email", equalTo("maria@email.com"))
                .body("role", equalTo("CLIENTE"));
    }

    @Test
    void cookieAdulteradoDa401() {
        String token = cadastrarCliente("maria@email.com");
        String adulterado = token.substring(0, token.length() - 4) + "AAAA";

        given().cookie("token", adulterado)
        .when().get("/api/usuarios/me")
        .then().statusCode(401);
    }

    @Test
    void logoutMandaOCookieVencido() {
        Response resposta = given().when().post("/api/usuarios/logout");

        resposta.then().statusCode(204);
        Cookie cookie = resposta.getDetailedCookie("token");
        assertThat(cookie.getMaxAge()).isZero();
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.isHttpOnly()).isTrue();
    }
}
