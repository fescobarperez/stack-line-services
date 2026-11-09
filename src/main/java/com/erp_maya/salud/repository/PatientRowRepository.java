package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.PatientRow;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;

import java.util.List;
import java.util.Optional;

/**
 * Listado y búsqueda de pacientes sobre la vista salud_v_patients.
 *
 * Los filtros vacíos llegan como '' y no como null: Hibernate no siempre
 * sabe el tipo de un parámetro nulo en una consulta nativa y Postgres
 * responde "could not determine data type of parameter".
 * `texto` busca en nombre, expediente, documento y NIT; `digitos`, en el
 * final del teléfono (como ClientService.findByPhone).
 */
@Repository
public interface PatientRowRepository extends GenericRepository<PatientRow, Long> {

    String FILTRO = "FROM salud_v_patients WHERE company_id = :companyId "
            + "AND (:status = '' OR status = :status) "
            + "AND (:texto = '' OR name ILIKE '%' || :texto || '%' OR record_number ILIKE '%' || :texto || '%' "
            + "OR doc_number ILIKE '%' || :texto || '%' OR nit ILIKE '%' || :texto || '%' "
            + "OR (:digitos <> '' AND phone_digits LIKE '%' || :digitos)) ";

    Optional<PatientRow> findByIdAndCompanyId(Long id, Long companyId);

    @Query(value = "SELECT * " + FILTRO + "ORDER BY name, id LIMIT :limite OFFSET :desde", nativeQuery = true)
    List<PatientRow> search(Long companyId, String status, String texto, String digitos, int limite, int desde);

    @Query(value = "SELECT count(*) " + FILTRO, nativeQuery = true)
    long countSearch(Long companyId, String status, String texto, String digitos);
}
