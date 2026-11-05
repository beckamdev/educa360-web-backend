-- Educa360 - datos mínimos de demostración (solo desarrollo).
--
-- Actores: ADMIN, SECRETARIA, DOCENTE, ESTUDIANTE y APODERADO.
-- Escenario: año 2026 abierto, bimestre I abierto, 3°A con dos cursos,
-- una alumna matriculada, una unidad publicada por curso, una nota y
-- una asistencia. Así cada rol tiene algo que consultar al iniciar sesión.
--
-- Las contraseñas están hasheadas con BCrypt (strength 10). Las credenciales
-- en claro están documentadas en CREDENCIALES.md.
--
-- En producción NO se debe cargar esta migración:
--   FLYWAY_LOCATIONS=classpath:db/migration

-- ==================== Cuentas ====================

INSERT INTO users (id, created_at, updated_at, activo, apellidos, dni, email, nombres, password, rol) VALUES
(1, '2026-03-02 08:00:00', '2026-03-02 08:00:00', true, 'Ruiz', '10000001', 'admin@educa360.pe', 'Ana',
 '$2y$10$9/1PvpCeaSGPQsPAtBmhYOKicOduZqVF7g9yV6k.uDeddzzKblEj2', 'ADMIN'),
(2, '2026-03-02 08:00:00', '2026-03-02 08:00:00', true, 'Quintos', '21000001', 'sofia@educa360.pe', 'Sofia',
 '$2y$10$0yh5VIxjhXvP2H4ORGKH/eXD7GQbeEsgxChOFKNfjFsfUr/mvBcJa', 'SECRETARIA'),
(3, '2026-03-02 08:00:00', '2026-03-02 08:00:00', true, 'Perez', '20000001', 'dana@educa360.pe', 'Dana',
 '$2y$10$W1FM45DMjAPDSCkFaVVQ0.JKLtDjX/Gbf838AujSKES.8ax3T2IqG', 'DOCENTE'),
(4, '2026-03-02 08:00:00', '2026-03-02 08:00:00', true, 'Luna', '30000001', 'alma@educa360.pe', 'Alma',
 '$2y$10$8Lc8//.kD.fx2vNDmC3Qt.FXU33z4ZA0GDbKakchhI5nIDXhqkIwG', 'ESTUDIANTE'),
(5, '2026-03-02 08:00:00', '2026-03-02 08:00:00', true, 'Soto', '40000001', 'lucia@educa360.pe', 'Lucia',
 '$2y$10$PfBn6i5Sv8RshNa0e0rAZuMJ9hAeR0M9BB/WFqxZxcUJGQkziHUU6', 'APODERADO');

INSERT INTO telefonos (id, created_at, updated_at, numero, user_id) VALUES
(1, '2026-03-02 08:00:00', '2026-03-02 08:00:00', '912345678', 2),
(2, '2026-03-02 08:00:00', '2026-03-02 08:00:00', '999888777', 5);

INSERT INTO secretarias (id, created_at, updated_at, user_id) VALUES
(1, '2026-03-02 08:00:00', '2026-03-02 08:00:00', 2);

INSERT INTO docentes (id, created_at, updated_at, titulo, user_id) VALUES
(1, '2026-03-02 08:00:00', '2026-03-02 08:00:00', 'Licenciada en Educación', 3);

INSERT INTO estudiantes (id, created_at, updated_at, estado, fecha_nacimiento, user_id) VALUES
(1, '2026-03-02 08:00:00', '2026-03-02 08:00:00', 'ACTIVO', '2011-03-03', 4);

INSERT INTO apoderados (id, created_at, updated_at, user_id) VALUES
(1, '2026-03-02 08:00:00', '2026-03-02 08:00:00', 5);

INSERT INTO apoderado_estudiante
    (id, created_at, updated_at, activo, parentesco, responsable_principal, apoderado_id, estudiante_id)
VALUES
    (1, '2026-03-02 08:00:00', '2026-03-02 08:00:00', true, 'MADRE', true, 1, 1);

-- ==================== Catálogo y estructura ====================

INSERT INTO materias (id, created_at, updated_at, activa, descripcion, nombre) VALUES
(1, '2026-03-02 08:00:00', '2026-03-02 08:00:00', true, 'Matemática escolar', 'Matemática'),
(2, '2026-03-02 08:00:00', '2026-03-02 08:00:00', true, 'Comunicación y lenguaje', 'Comunicación');

INSERT INTO docente_materia (docente_id, materia_id) VALUES
(1, 1),
(1, 2);

INSERT INTO anios_escolares (id, created_at, updated_at, anio, estado, fecha_inicio, nombre) VALUES
(1, '2026-03-02 08:00:00', '2026-03-02 08:00:00', 2026, 'ABIERTO', '2026-03-02', 'Año Escolar 2026');

INSERT INTO bimestres (id, created_at, updated_at, estado, fecha_fin, fecha_inicio, numero, anio_id) VALUES
(1, '2026-03-02 08:00:00', '2026-03-02 08:00:00', 'ABIERTO', '2026-05-15', '2026-03-02', 1, 1),
(2, '2026-03-02 08:00:00', '2026-03-02 08:00:00', 'PLANIFICADO', '2026-07-24', '2026-05-18', 2, 1);

INSERT INTO secciones (id, created_at, updated_at, aula, grado, letra, anio_id, tutor_id) VALUES
(1, '2026-03-02 08:00:00', '2026-03-02 08:00:00', 'A-101', 3, 'A', 1, 1);

INSERT INTO cursos (id, created_at, updated_at, docente_id, materia_id, seccion_id) VALUES
(1, '2026-03-02 08:00:00', '2026-03-02 08:00:00', 1, 1, 1),
(2, '2026-03-02 08:00:00', '2026-03-02 08:00:00', 1, 2, 1);

INSERT INTO horario_bloques (id, created_at, updated_at, dia, hora_fin, hora_inicio, curso_id) VALUES
(1, '2026-03-02 08:00:00', '2026-03-02 08:00:00', 'LUNES', '09:00:00', '08:00:00', 1),
(2, '2026-03-02 08:00:00', '2026-03-02 08:00:00', 'LUNES', '10:00:00', '09:00:00', 2);

-- ==================== Matrícula y actividad ====================

INSERT INTO matriculas
    (id, created_at, updated_at, fecha_matricula, observacion, situacion_final, anio_id, estudiante_id, seccion_id)
VALUES
    (1, '2026-03-02 08:00:00', '2026-03-02 08:00:00', '2026-03-02', NULL, 'EN_CURSO', 1, 1, 1);

INSERT INTO unidades
    (id, created_at, updated_at, descripcion, estado, fecha_fin, fecha_inicio, numero, titulo, bimestre_id, curso_id)
VALUES
    (1, '2026-03-02 08:00:00', '2026-03-02 08:00:00', 'Fracciones y operaciones básicas', 'PUBLICADA',
     '2026-04-15', '2026-03-02', 1, 'Unidad 1: Fracciones', 1, 1),
    (2, '2026-03-02 08:00:00', '2026-03-02 08:00:00', 'Comprensión lectora y producción de textos', 'PUBLICADA',
     '2026-04-15', '2026-03-02', 1, 'Unidad 1: Comprensión lectora', 1, 2);

INSERT INTO calificaciones
    (id, created_at, updated_at, criterio, nota, matricula_id, registrado_por_id, unidad_id)
VALUES
    (1, '2026-03-09 08:00:00', '2026-03-09 08:00:00', 'Examen', 16.00, 1, 1, 1),
    (2, '2026-03-09 08:00:00', '2026-03-09 08:00:00', 'Examen', 15.00, 1, 1, 2);

INSERT INTO asistencias
    (id, created_at, updated_at, estado, fecha, observacion, bloque_id, matricula_id, registrado_por_id, ticket_id)
VALUES
    (1, '2026-03-02 08:00:00', '2026-03-02 08:00:00', 'PRESENTE', '2026-03-02', NULL, 1, 1, 1, NULL),
    (2, '2026-03-02 09:00:00', '2026-03-02 09:00:00', 'PRESENTE', '2026-03-02', NULL, 2, 1, 1, NULL);

-- ==================== Secuencias ====================
-- Los ids se insertaron explícitamente; adelantamos cada secuencia para que
-- las altas futuras por API no choquen con la data demo.

SELECT setval(pg_get_serial_sequence('users', 'id'), (SELECT MAX(id) FROM users));
SELECT setval(pg_get_serial_sequence('telefonos', 'id'), (SELECT MAX(id) FROM telefonos));
SELECT setval(pg_get_serial_sequence('secretarias', 'id'), (SELECT MAX(id) FROM secretarias));
SELECT setval(pg_get_serial_sequence('docentes', 'id'), (SELECT MAX(id) FROM docentes));
SELECT setval(pg_get_serial_sequence('estudiantes', 'id'), (SELECT MAX(id) FROM estudiantes));
SELECT setval(pg_get_serial_sequence('apoderados', 'id'), (SELECT MAX(id) FROM apoderados));
SELECT setval(pg_get_serial_sequence('apoderado_estudiante', 'id'), (SELECT MAX(id) FROM apoderado_estudiante));
SELECT setval(pg_get_serial_sequence('materias', 'id'), (SELECT MAX(id) FROM materias));
SELECT setval(pg_get_serial_sequence('anios_escolares', 'id'), (SELECT MAX(id) FROM anios_escolares));
SELECT setval(pg_get_serial_sequence('bimestres', 'id'), (SELECT MAX(id) FROM bimestres));
SELECT setval(pg_get_serial_sequence('secciones', 'id'), (SELECT MAX(id) FROM secciones));
SELECT setval(pg_get_serial_sequence('cursos', 'id'), (SELECT MAX(id) FROM cursos));
SELECT setval(pg_get_serial_sequence('horario_bloques', 'id'), (SELECT MAX(id) FROM horario_bloques));
SELECT setval(pg_get_serial_sequence('matriculas', 'id'), (SELECT MAX(id) FROM matriculas));
SELECT setval(pg_get_serial_sequence('unidades', 'id'), (SELECT MAX(id) FROM unidades));
SELECT setval(pg_get_serial_sequence('calificaciones', 'id'), (SELECT MAX(id) FROM calificaciones));
SELECT setval(pg_get_serial_sequence('asistencias', 'id'), (SELECT MAX(id) FROM asistencias));
