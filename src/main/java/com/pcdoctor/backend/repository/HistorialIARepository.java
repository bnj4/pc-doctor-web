package com.pcdoctor.backend.repository;

import com.pcdoctor.backend.model.HistorialIA;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HistorialIARepository extends JpaRepository<HistorialIA, Long> {
    List<HistorialIA> findBySessionId(String sessionId);
}