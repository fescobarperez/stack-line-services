package com.erp_maya.salud.repository;

import com.erp_maya.salud.domain.AccessLog;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

/** Solo se inserta: la bitácora no se edita ni se borra desde la aplicación. */
@Repository
public interface AccessLogRepository extends CrudRepository<AccessLog, Long> {
}
