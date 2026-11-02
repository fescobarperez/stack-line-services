package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.Cie10;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;

import java.util.List;

/** Catálogo CIE-10 (solo lectura). */
@Repository
public interface Cie10Repository extends GenericRepository<Cie10, String> {

    /**
     * Por código («J06», «G43.9») o por palabras de la descripción ya
     * normalizadas (minúsculas, sin tildes). Primero los que empiezan por el
     * código buscado.
     */
    @Query(value = "SELECT * FROM salud_cie10 WHERE search_text LIKE :patron OR upper(code) LIKE :codigo "
            + "ORDER BY (upper(code) LIKE :codigo) DESC, code LIMIT :limite", nativeQuery = true)
    List<Cie10> search(String patron, String codigo, int limite);
}
