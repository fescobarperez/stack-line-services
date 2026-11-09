package com.erp_maya.template.repository;

import com.erp_maya.template.domain.OptionChoice;
import com.erp_maya.template.domain.ProductTemplate;
import com.erp_maya.template.domain.TemplateComponent;
import com.erp_maya.template.domain.TemplateOption;
import com.erp_maya.template.domain.TemplateParam;
import com.erp_maya.template.domain.TemplatePreset;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Repositorios de las plantillas: la cabecera y sus partes, siempre por plantilla. */
public class TemplateRepositories {

    @Repository
    public interface Templates extends JpaRepository<ProductTemplate, Long> {
        List<ProductTemplate> findByCompanyIdOrderByNameAsc(Long companyId);
        Optional<ProductTemplate> findByIdAndCompanyId(Long id, Long companyId);
        Optional<ProductTemplate> findByCompanyIdAndProductId(Long companyId, Long productId);
    }

    @Repository
    public interface Params extends JpaRepository<TemplateParam, Long> {
        List<TemplateParam> findByTemplateIdOrderBySortOrderAsc(Long templateId);
        void deleteByTemplateId(Long templateId);
    }

    @Repository
    public interface Options extends JpaRepository<TemplateOption, Long> {
        List<TemplateOption> findByTemplateIdOrderBySortOrderAsc(Long templateId);
        void deleteByTemplateId(Long templateId);
    }

    @Repository
    public interface Choices extends JpaRepository<OptionChoice, Long> {
        List<OptionChoice> findByOptionIdInOrderBySortOrderAsc(Collection<Long> optionIds);
    }

    @Repository
    public interface Components extends JpaRepository<TemplateComponent, Long> {
        List<TemplateComponent> findByTemplateIdOrderBySortOrderAsc(Long templateId);
        void deleteByTemplateId(Long templateId);
    }

    @Repository
    public interface Presets extends JpaRepository<TemplatePreset, Long> {
        List<TemplatePreset> findByTemplateIdOrderBySortOrderAsc(Long templateId);
        void deleteByTemplateId(Long templateId);
    }
}
