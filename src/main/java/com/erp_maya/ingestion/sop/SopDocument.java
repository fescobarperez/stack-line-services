package com.erp_maya.ingestion.sop;

import io.micronaut.serde.annotation.Serdeable;

/** Documento de instrucciones del crawler cargado desde recursos versionados. */
@Serdeable
public record SopDocument(String siteKey, String id, String version,
                           String instructionsMarkdown, String rulesJson) {
}
