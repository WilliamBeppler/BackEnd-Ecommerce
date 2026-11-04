package org.project.controller;

import org.project.service.excecao.LoginBloqueadoException;
import org.project.service.excecao.NaoAutenticadoException;
import org.project.service.excecao.NaoEncontradoException;
import org.project.service.excecao.RegraDeNegocioException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Tradutor de erros para HTTP, num lugar só.
 *
 * Os services não sabem o que é status code: quando algo não pode, eles lançam uma
 * exceção de negócio ("regra quebrada", "não encontrado"...). Este tratador, que é da
 * camada de controller, decide qual status HTTP cada uma vira. O corpo continua sendo
 * o texto "Erro: ..." que a loja e o painel já mostram ao usuário.
 */
@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<String> regraDeNegocio(RegraDeNegocioException e) {
        return ResponseEntity.badRequest().body(e.getMessage());                       // 400
    }

    @ExceptionHandler(NaoEncontradoException.class)
    public ResponseEntity<String> naoEncontrado(NaoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());      // 404
    }

    @ExceptionHandler(NaoAutenticadoException.class)
    public ResponseEntity<String> naoAutenticado(NaoAutenticadoException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());   // 401
    }

    @ExceptionHandler(LoginBloqueadoException.class)
    public ResponseEntity<String> loginBloqueado(LoginBloqueadoException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)                     // 429
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getEspera().getSeconds() + 1))
                .body(e.getMessage());
    }
}
