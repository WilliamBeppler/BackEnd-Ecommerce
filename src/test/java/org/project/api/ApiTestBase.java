package org.project.api;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.project.model.Produto;
import org.project.model.Usuario;
import org.project.pagamento.MercadoPagoCliente;
import org.project.repository.EnderecoRepository;
import org.project.repository.ItemPedidoRepository;
import org.project.repository.PedidoHistoricoRepository;
import org.project.repository.PedidoRepository;
import org.project.repository.ProdutoRepository;
import org.project.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;

/**
 * Base dos testes de API.*/

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class ApiTestBase {

    /** Origem da loja (pode comprar, não pode administrar). */

    static final String LOJA = "http://localhost:5500";

    /** Origem do painel de admin (a única aceita nas ações de admin). */

    static final String PAINEL = "http://localhost:5501";

    static final String SENHA = "senhaForte1";

    private static final AtomicInteger contadorCpf = new AtomicInteger(200_000_001);

    @LocalServerPort int porta;

    @MockBean MercadoPagoCliente mercadoPago;

    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired ItemPedidoRepository itemPedidoRepository;
    @Autowired EnderecoRepository enderecoRepository;
    @Autowired PedidoHistoricoRepository historicoRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void prepararApiEBancoLimpo() {
        RestAssured.port = porta;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        // Cada teste começa com o banco vazio. Ordem: de quem aponta para quem é apontado.
        historicoRepository.deleteAll();
        itemPedidoRepository.deleteAll();
        pedidoRepository.deleteAll();
        enderecoRepository.deleteAll();
        produtoRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    //usuários

    static Map<String, Object> dadosCadastro(String email) {
        return Map.of(
                "nome", "Cliente Teste",
                "email", email,
                "senha", SENHA,
                "cpf", gerarCpfValido(),
                "telefone", "(48) 99999-9999",
                "nascimento", "1990-05-20");
    }

    String cadastrarCliente(String email) {
        return given()
                .contentType(ContentType.JSON)
                .body(dadosCadastro(email))
        .when()
                .post("/api/usuarios/registrar")
        .then()
                .statusCode(200)
                .extract().cookie("token");
    }


    String criarAdminELogar(String email) {
        Usuario admin = new Usuario("Admin", LocalDate.of(1985, 1, 1), "48988887777",
                formatar(gerarCpfValido()), email, passwordEncoder.encode(SENHA));
        admin.setRole("ADMIN");
        usuarioRepository.save(admin);

        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", SENHA))
        .when()
                .post("/api/usuarios/login")
        .then()
                .statusCode(200)
                .extract().cookie("token");
    }

    //produtos e pedidos

    Produto criarProduto(String nome, String preco, boolean ativo) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setPreco(new BigDecimal(preco));
        p.setCaminho_imagem("../imgs/img_pastelCarne.jpeg");
        p.setAtivo(ativo);
        return produtoRepository.save(p);
    }

    static Map<String, Object> enderecoValido() {
        return Map.of("cep", "88000-000", "rua", "Rua das Flores", "numero", "10",
                "bairro", "Centro", "cidade", "Florianópolis", "estado", "SC");
    }

    static Map<String, Object> corpoPedido(int produtoId, int quantidade) {
        return Map.of("endereco", enderecoValido(),
                "itens", List.of(Map.of("produtoId", produtoId, "quantidade", quantidade)));
    }

    //CPF

    /** Gera um CPF diferente a cada chamada, com dígitos verificadores corretos. */
    static String gerarCpfValido() {
        String base = String.valueOf(contadorCpf.getAndIncrement());
        String com1 = base + digito(base);
        return com1 + digito(com1);
    }

    private static char digito(String digitos) {
        int quantidade = digitos.length();
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (digitos.charAt(i) - '0') * (quantidade + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return (char) ('0' + (resto == 10 ? 0 : resto));
    }

    private static String formatar(String cpf) {
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
    }
}
