package com.educa360.backend.dto;

import com.educa360.backend.entity.Telefono;
import com.educa360.backend.entity.User;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vista pública del usuario. Nunca incluye la contraseña
 * (aunque User.password ya es WRITE_ONLY, el DTO es la capa
 * que decide qué se expone por la API).
 */
public record UserResponse(
        Long id,
        String email,
        String nombres,
        String apellidos,
        String dni,
        String rol,
        boolean activo,
        List<String> telefonos,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNombres(),
                user.getApellidos(),
                user.getDni(),
                user.getRol().name(),
                user.isActivo(),
                user.getTelefonos().stream().map(Telefono::getNumero).sorted().toList(),
                user.getCreatedAt()
        );
    }
}
