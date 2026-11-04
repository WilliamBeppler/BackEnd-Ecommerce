# Pedacinho do Céu — Back-end (API)

API REST do e-commerce **Pedacinho do Céu Produtos Coloniais**: catálogo de produtos, cadastro e login de clientes, pedidos, painel de administração e pagamento com **Mercado Pago**.

> Projeto de estudo. As credenciais do Mercado Pago usadas aqui são **de teste**: nenhum pagamento real é processado.

Este repositório é uma das três partes do sistema:

| Parte | Repositório | Endereço em desenvolvimento |
|---|---|---|
| **Back-end (este)** | [BackEnd-Ecommerce](https://github.com/WilliamBeppler/BackEnd-Ecommerce) | `http://localhost:8080` |
| Loja (front-end) | [FrontEnd-Ecommerce](https://github.com/WilliamBeppler/FrontEnd-Ecommerce) | `http://localhost:5500` |
| Painel de administração | [Painel-Admin-Ecommerce](https://github.com/WilliamBeppler/Painel-Admin-Ecommerce) | `http://localhost:5501` |

## Tecnologias

- **Java 21** e **Spring Boot 3.2** (Web, Data JPA, Security)
- **MySQL** com JPA/Hibernate
- **JWT** (jjwt 0.13) guardado em **cookie HttpOnly**
- **BCrypt** para senhas
- **Mercado Pago** (Checkout Pro + webhook), chamado com o `RestClient` do Spring, sem SDK

## Funcionalidades

- **Catálogo:** vitrine pública só com produtos ativos; o admin cadastra, edita preço e imagem, e desativa produtos (sem apagar, para não quebrar o histórico de pedidos).
- **Clientes:** cadastro com validação no servidor (e-mail, CPF com dígitos verificadores, senha de 8 a 72 caracteres, telefone e nascimento), login e logout.
- **Pedidos:** o preço, o total e o dono do pedido são decididos pelo servidor, nunca pelo navegador.
- **Painel de admin:** lista paginada com abas e busca, detalhe com comprador e endereço, e dois status independentes (**pagamento** e **processamento**), com histórico de quem mudou o quê e quando.
- **Pagamento:** o checkout gera uma cobrança no Mercado Pago com os preços do banco; o cliente paga na página do Mercado Pago (o número do cartão nunca passa por este servidor); o **webhook** confirma o pagamento depois de conferir a assinatura e consultar a API.

## Segurança

- Segredos só em **variáveis de ambiente** (`.env` fora do Git; modelo em `.env.example`). Sem variável obrigatória, a aplicação não sobe.
- Sessão em **cookie HttpOnly**, `SameSite=Strict`, `Secure` em produção.
- **CORS** só para as origens listadas, nunca `*`.
- **DTOs** em todas as entradas: o cliente não consegue enviar `id`, cargo, preço ou dono do pedido. Nas entidades, o `id` é somente leitura para o JSON.
- Rotas de admin exigem `ROLE_ADMIN` **e** passam por uma **trava pelo cabeçalho `Origin`**: só o painel consegue alterar produtos ou ler dados de pedidos.
- Login com mensagem única, tempo de resposta igual exista ou não a conta, e **limite de tentativas** por e-mail (5) e por IP (20) a cada 15 minutos.
- Pedidos de outro cliente respondem **404** (proteção contra IDOR).
- Webhook do Mercado Pago com **assinatura HMAC-SHA256** conferida em tempo constante, processamento **idempotente** e trava no pedido (`SELECT ... FOR UPDATE`).

## Principais rotas

| Método | Rota | Quem pode |
|---|---|---|
| POST | `/api/usuarios/registrar`, `/api/usuarios/login` | Público |
| POST | `/api/usuarios/logout` | Público |
| GET | `/api/usuarios/me` | Logado |
| GET | `/api/produtos` | Público (só ativos) |
| GET | `/api/produtos/admin` | Admin |
| POST / PUT / DELETE | `/api/produtos/**` | Admin, vindo do painel |
| POST | `/api/pedidos` | Logado (cria o pedido e devolve o link de pagamento) |
| POST | `/api/pedidos/{id}/pagamento` | Dono do pedido (novo link) |
| GET | `/api/pedidos/{id}/situacao` | Dono do pedido |
| POST | `/api/pedidos/{id}/conferir-pagamento?payment_id=` | Dono do pedido |
| GET | `/api/admin/pedidos?aba=&busca=&pagina=` | Admin, vindo do painel |
| GET | `/api/admin/pedidos/{id}` | Admin, vindo do painel |
| PUT | `/api/admin/pedidos/{id}/pagamento`, `/processamento` | Admin, vindo do painel |
| POST | `/api/pagamentos/webhook` | Mercado Pago (com assinatura) |

## Testando o pagamento

1. O webhook precisa de um endereço público. Em desenvolvimento, use um túnel:
   ```bash
   cloudflared tunnel --url http://localhost:8080
   ```
2. No painel do Mercado Pago → **Webhooks** (modo Teste), configure a URL `https://SEU-TUNEL.trycloudflare.com/api/pagamentos/webhook` com o evento **Pagamentos**, e use **Simular notificação**: a resposta deve ser 200.
3. Compre com um **comprador de teste** do Mercado Pago e os [cartões de teste](https://www.mercadopago.com.br/developers/pt/docs/checkout-pro/integration-test/test-purchases) (titular `APRO` aprova, `OTHE` recusa).
4. Pagamentos de teste não disparam o webhook. Quem confirma, nesse caso, é a página de volta da loja, que pede ao servidor para conferir o pagamento na API.

## Estrutura

```
src/main/java/org/project
├── controller/   rotas (usuários, produtos, pedidos, admin, webhook)
├── dto/          o que entra e o que sai da API (nunca as entidades)
├── model/        entidades JPA e enums de status
├── repository/   acesso ao banco
├── service/      regras de status e filtros dos pedidos
├── pagamento/    integração com o Mercado Pago
└── security/     JWT em cookie, CORS, trava por Origin, limite de login
```
