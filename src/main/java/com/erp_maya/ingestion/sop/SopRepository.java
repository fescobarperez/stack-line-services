package com.erp_maya.ingestion.sop;

import java.util.Optional;

/** Fuente de SOPs; el MVP usa classpath y una fase posterior puede usar S3. */
public interface SopRepository {
    Optional<SopDocument> findActive(String siteKey);
}
