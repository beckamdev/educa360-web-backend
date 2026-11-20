package com.educa360.backend.entity;

import com.educa360.backend.config.ZonaHoraria;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Base común de todas las entidades del dominio.
 *
 * Centraliza la identidad y la auditoría de fechas para no repetir
 * @PrePersist / @PreUpdate en cada clase.
 */
@MappedSuperclass
public abstract class Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Lifecycle

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now(ZonaHoraria.APP);
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now(ZonaHoraria.APP);
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
