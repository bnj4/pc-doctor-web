package com.pcdoctor.backend.repository;

import com.pcdoctor.backend.model.UsuarioSistema;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UsuarioSistemaRepository extends JpaRepository<UsuarioSistema, Long> {
    Optional<UsuarioSistema> findByRut(String rut);
    boolean existsByRut(String rut);
    List<UsuarioSistema> findByRol(String rol);
}