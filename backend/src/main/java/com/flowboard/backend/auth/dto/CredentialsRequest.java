package com.flowboard.backend.auth.dto;

import com.flowboard.backend.auth.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Usuário e senha (cadastro, login e "visitante vira conta").
 *
 * O padrão do nome aceita espaços nas pontas porque o servidor os remove antes de usar:
 * "  Ana " entra como "ana".
 */
public record CredentialsRequest(
        @NotBlank(message = "Informe o nome de usuário")
        @Pattern(regexp = "^\\s*[A-Za-z0-9._]{3,20}\\s*$", message = "Use de 3 a 20 letras, números, ponto ou _")
        String username,

        @NotBlank(message = "Informe a senha")
        @Size(min = 8, message = "A senha precisa ter pelo menos 8 caracteres")
        // O BCrypt só considera os primeiros 72 BYTES da senha e ignora o resto em silêncio.
        // Com acentos, 72 bytes podem ser bem menos que 72 caracteres, por isso a conta é em bytes.
        @MaxUtf8Bytes(value = 72, message = "Senha longa demais")
        String password
) {
}
