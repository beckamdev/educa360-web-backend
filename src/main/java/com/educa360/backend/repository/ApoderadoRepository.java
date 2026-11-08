package com.educa360.backend.repository;

import com.educa360.backend.entity.Apoderado;
import com.educa360.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApoderadoRepository extends JpaRepository<Apoderado, Long> {

    Optional<Apoderado> findByUser(User user);

    /** Búsqueda exacta por DNI para el alta de familias (el DNI es único). */
    Optional<Apoderado> findByUserDni(String dni);
}
