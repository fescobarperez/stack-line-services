package com.erp_maya.project.service;

import com.erp_maya.catalog.domain.Product;
import com.erp_maya.catalog.domain.ProductSupplier;
import com.erp_maya.catalog.repository.ProductRepository;
import com.erp_maya.catalog.repository.ProductSupplierRepository;
import com.erp_maya.common.ResourceNotFoundException;
import com.erp_maya.common.TenantContext;
import com.erp_maya.partner.domain.Supplier;
import com.erp_maya.partner.repository.SupplierRepository;
import com.erp_maya.project.domain.Project;
import com.erp_maya.project.domain.ProjectMaterial;
import com.erp_maya.project.domain.ProjectMaterialGroup;
import com.erp_maya.project.dto.ProjectMaterialDtos;
import com.erp_maya.project.repository.ProjectMaterialRepositories.Groups;
import com.erp_maya.project.repository.ProjectMaterialRepositories.Materials;
import com.erp_maya.project.repository.ProjectRepositories.Projects;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Plan de materiales de un proyecto. No modifica cotizaciones ni inventario. */
@Singleton
public class ProjectMaterialService {

    private final Projects projects;
    private final Groups groups;
    private final Materials materials;
    private final ProductRepository products;
    private final ProductSupplierRepository productSuppliers;
    private final SupplierRepository suppliers;
    private final TenantContext tenant;

    public ProjectMaterialService(Projects projects, Groups groups, Materials materials,
                                  ProductRepository products, ProductSupplierRepository productSuppliers,
                                  SupplierRepository suppliers, TenantContext tenant) {
        this.projects = projects;
        this.groups = groups;
        this.materials = materials;
        this.products = products;
        this.productSuppliers = productSuppliers;
        this.suppliers = suppliers;
        this.tenant = tenant;
    }

    @Transactional
    public ProjectMaterialDtos.PlanResponse getPlan(Long projectId) {
        Project project = findProject(projectId);
        Long companyId = project.getCompanyId();
        List<ProjectMaterialGroup> groupRows = groups
                .findByCompanyIdAndProjectIdOrderBySortOrderAsc(companyId, projectId);
        List<ProjectMaterial> materialRows = materials
                .findByCompanyIdAndProjectIdOrderByIdAsc(companyId, projectId);

        Map<Long, List<ProjectMaterialDtos.MaterialResponse>> byGroup = materialRows.stream()
                .filter(material -> material.getGroupId() != null)
                .map(this::toMaterialResponse)
                .collect(Collectors.groupingBy(ProjectMaterialDtos.MaterialResponse::groupId));
        Map<Long, BigDecimal> directGroupTotals = byGroup.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(ProjectMaterialDtos.MaterialResponse::estimatedAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                ));
        Map<Long, List<Long>> childGroups = groupRows.stream()
                .filter(group -> group.getParentGroupId() != null)
                .collect(Collectors.groupingBy(
                        ProjectMaterialGroup::getParentGroupId,
                        Collectors.mapping(ProjectMaterialGroup::getId, Collectors.toList())
                ));
        Map<Long, BigDecimal> recursiveGroupTotals = new HashMap<>();
        groupRows.forEach(group -> recursiveGroupCost(
                group.getId(), directGroupTotals, childGroups, recursiveGroupTotals, new HashSet<>()
        ));
        List<ProjectMaterialDtos.GroupResponse> result = groupRows.stream()
                .map(g -> toGroupResponse(
                        g,
                        byGroup.getOrDefault(g.getId(), List.of()),
                        recursiveGroupTotals.getOrDefault(g.getId(), BigDecimal.ZERO)
                ))
                .toList();
        List<ProjectMaterialDtos.MaterialResponse> ungrouped = materialRows.stream()
                .filter(material -> material.getGroupId() == null)
                .map(this::toMaterialResponse)
                .toList();
        BigDecimal total = materialRows.stream().map(this::estimatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ProjectMaterialDtos.PlanResponse(projectId, result, ungrouped, money(total), materialRows.size());
    }

    @Transactional
    public ProjectMaterialDtos.GroupResponse createGroup(Long projectId, ProjectMaterialDtos.GroupRequest req) {
        Project project = mutableProject(projectId);
        Long companyId = project.getCompanyId();
        Long parentId = req.parentGroupId();
        if (parentId != null) {
            ProjectMaterialGroup parent = groups.findByIdAndCompanyId(parentId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Grupo padre " + parentId + " no encontrado"));
            if (!projectId.equals(parent.getProjectId())) {
                throw new IllegalStateException("El grupo padre no pertenece a este proyecto");
            }
        }
        ProjectMaterialGroup group = new ProjectMaterialGroup();
        group.setCompanyId(companyId);
        group.setProjectId(projectId);
        group.setParentGroupId(parentId);
        group.setName(req.name().trim());
        group.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        ProjectMaterialGroup saved = groups.save(group);
        return toGroupResponse(saved, List.of());
    }

    @Transactional
    public ProjectMaterialDtos.MaterialResponse createMaterial(Long projectId,
                                                                ProjectMaterialDtos.MaterialRequest req) {
        Project project = mutableProject(projectId);
        Long companyId = project.getCompanyId();
        if (req.quantityPlanned() == null || req.quantityPlanned().signum() <= 0) {
            throw new IllegalStateException("La cantidad planificada debe ser mayor que cero");
        }
        Product product = products.findByIdAndCompanyId(req.productId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto " + req.productId() + " no encontrado"));
        if ("service".equalsIgnoreCase(product.getItemType())) {
            throw new IllegalStateException("Un servicio no puede agregarse como materia prima");
        }
        if (req.groupId() != null) {
            ProjectMaterialGroup group = groups.findByIdAndCompanyId(req.groupId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Grupo " + req.groupId() + " no encontrado"));
            if (!projectId.equals(group.getProjectId())) {
                throw new IllegalStateException("El grupo no pertenece a este proyecto");
            }
        }
        if (req.supplierId() == null) {
            throw new IllegalStateException("Selecciona el proveedor del producto");
        }
        ProductSupplier productSupplier = productSuppliers
                .findByCompanyIdAndProductIdAndSupplierId(companyId, product.getId(), req.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("El proveedor no está asociado a este producto"));
        Supplier supplier = suppliers.findByIdAndCompanyId(req.supplierId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor " + req.supplierId() + " no encontrado"));

        BigDecimal unitCost = productSupplier.getUnitCost();
        ProjectMaterial material = new ProjectMaterial();
        material.setCompanyId(companyId);
        material.setProjectId(projectId);
        material.setGroupId(req.groupId());
        material.setProductId(product.getId());
        material.setSupplierId(supplier.getId());
        material.setQuantityPlanned(req.quantityPlanned());
        material.setUom(req.uom() == null || req.uom().isBlank()
                ? (product.getUnit() == null || product.getUnit().isBlank() ? "unid" : product.getUnit())
                : req.uom().trim());
        material.setUnitCostSnapshot(unitCost);
        material.setCostSnapshotAt(Instant.now());
        material.setNotes(req.notes());
        return toMaterialResponse(materials.save(material));
    }

    @Transactional
    public ProjectMaterialDtos.MaterialResponse moveMaterial(Long projectId, Long materialId,
                                                             ProjectMaterialDtos.MoveMaterialRequest req) {
        Project project = mutableProject(projectId);
        Long companyId = project.getCompanyId();
        ProjectMaterial material = materials.findByIdAndCompanyId(materialId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Material " + materialId + " no encontrado"));
        if (!projectId.equals(material.getProjectId())) {
            throw new IllegalStateException("El material no pertenece a este proyecto");
        }
        Long groupId = req == null ? null : req.groupId();
        if (groupId != null) {
            ProjectMaterialGroup group = groups.findByIdAndCompanyId(groupId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Grupo " + groupId + " no encontrado"));
            if (!projectId.equals(group.getProjectId())) {
                throw new IllegalStateException("El grupo no pertenece a este proyecto");
            }
        }
        material.setGroupId(groupId);
        return toMaterialResponse(materials.save(material));
    }

    @Transactional
    public ProjectMaterialDtos.GroupResponse moveGroup(Long projectId, Long groupId,
                                                       ProjectMaterialDtos.MoveGroupRequest req) {
        Project project = mutableProject(projectId);
        Long companyId = project.getCompanyId();
        ProjectMaterialGroup group = groups.findByIdAndCompanyId(groupId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo " + groupId + " no encontrado"));
        if (!projectId.equals(group.getProjectId())) {
            throw new IllegalStateException("El grupo no pertenece a este proyecto");
        }
        Long parentId = req == null ? null : req.parentGroupId();
        if (parentId != null) {
            ProjectMaterialGroup parent = groups.findByIdAndCompanyId(parentId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Grupo padre " + parentId + " no encontrado"));
            if (!projectId.equals(parent.getProjectId())) {
                throw new IllegalStateException("El grupo padre no pertenece a este proyecto");
            }
            if (groupId.equals(parentId) || isDescendant(parentId, groupId, companyId, projectId)) {
                throw new IllegalStateException("Un grupo no puede moverse dentro de sí mismo o de un subfolder propio");
            }
        }
        group.setParentGroupId(parentId);
        return toGroupResponse(groups.save(group), List.of());
    }

    @Transactional
    public ProjectMaterialDtos.MaterialResponse copyMaterial(Long projectId, Long materialId) {
        Project project = mutableProject(projectId);
        Long companyId = project.getCompanyId();
        ProjectMaterial source = materials.findByIdAndCompanyId(materialId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Material " + materialId + " no encontrado"));
        if (!projectId.equals(source.getProjectId())) {
            throw new IllegalStateException("El material no pertenece a este proyecto");
        }

        ProjectMaterial copy = new ProjectMaterial();
        copy.setCompanyId(companyId);
        copy.setProjectId(projectId);
        copy.setGroupId(source.getGroupId());
        copy.setProductId(source.getProductId());
        copy.setSupplierId(source.getSupplierId());
        copy.setQuantityPlanned(source.getQuantityPlanned());
        copy.setUom(source.getUom());
        copy.setUnitCostSnapshot(source.getUnitCostSnapshot());
        copy.setCostSnapshotAt(source.getCostSnapshotAt());
        copy.setQuantityOrdered(source.getQuantityOrdered());
        copy.setQuantityConsumed(source.getQuantityConsumed());
        copy.setStatus(source.getStatus());
        copy.setNotes(source.getNotes());
        return toMaterialResponse(materials.save(copy));
    }

    @Transactional
    public ProjectMaterialDtos.GroupResponse copyGroup(Long projectId, Long groupId) {
        Project project = mutableProject(projectId);
        Long companyId = project.getCompanyId();
        ProjectMaterialGroup source = groups.findByIdAndCompanyId(groupId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo " + groupId + " no encontrado"));
        if (!projectId.equals(source.getProjectId())) {
            throw new IllegalStateException("El grupo no pertenece a este proyecto");
        }

        List<ProjectMaterialGroup> projectGroups = groups
                .findByCompanyIdAndProjectIdOrderBySortOrderAsc(companyId, projectId);
        List<ProjectMaterial> projectMaterials = materials
                .findByCompanyIdAndProjectIdOrderByIdAsc(companyId, projectId);
        Map<Long, List<ProjectMaterialGroup>> childrenByParent = projectGroups.stream()
                .filter(group -> group.getParentGroupId() != null)
                .collect(Collectors.groupingBy(ProjectMaterialGroup::getParentGroupId));
        Map<Long, List<ProjectMaterial>> materialsByGroup = projectMaterials.stream()
                .filter(material -> material.getGroupId() != null)
                .collect(Collectors.groupingBy(ProjectMaterial::getGroupId));

        ProjectMaterialGroup copy = copyGroupTree(
                source, source.getParentGroupId(), childrenByParent, materialsByGroup, companyId, projectId, true
        );
        return getPlan(projectId).groups().stream()
                .filter(group -> group.id().equals(copy.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No se pudo cargar el folder copiado"));
    }

    @Transactional
    public ProjectMaterialDtos.GroupResponse renameGroup(Long projectId, Long groupId,
                                                          ProjectMaterialDtos.RenameGroupRequest req) {
        Project project = mutableProject(projectId);
        Long companyId = project.getCompanyId();
        ProjectMaterialGroup group = groups.findByIdAndCompanyId(groupId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo " + groupId + " no encontrado"));
        if (!projectId.equals(group.getProjectId())) {
            throw new IllegalStateException("El grupo no pertenece a este proyecto");
        }
        if (req == null || req.name() == null || req.name().isBlank()) {
            throw new IllegalStateException("Indica el nombre del folder");
        }
        group.setName(req.name().trim());
        ProjectMaterialGroup saved = groups.save(group);
        List<ProjectMaterialDtos.MaterialResponse> directMaterials = materials
                .findByCompanyIdAndProjectIdOrderByIdAsc(companyId, projectId).stream()
                .filter(material -> groupId.equals(material.getGroupId()))
                .map(this::toMaterialResponse)
                .toList();
        return toGroupResponse(saved, directMaterials);
    }

    private ProjectMaterialGroup copyGroupTree(ProjectMaterialGroup source, Long parentId,
                                                Map<Long, List<ProjectMaterialGroup>> childrenByParent,
                                                Map<Long, List<ProjectMaterial>> materialsByGroup,
                                                Long companyId, Long projectId, boolean rootCopy) {
        ProjectMaterialGroup copy = new ProjectMaterialGroup();
        copy.setCompanyId(companyId);
        copy.setProjectId(projectId);
        copy.setParentGroupId(parentId);
        copy.setName(source.getName() + (rootCopy ? " (copia)" : ""));
        copy.setSortOrder(source.getSortOrder());
        ProjectMaterialGroup saved = groups.save(copy);

        for (ProjectMaterial sourceMaterial : materialsByGroup.getOrDefault(source.getId(), List.of())) {
            ProjectMaterial materialCopy = new ProjectMaterial();
            materialCopy.setCompanyId(companyId);
            materialCopy.setProjectId(projectId);
            materialCopy.setGroupId(saved.getId());
            materialCopy.setProductId(sourceMaterial.getProductId());
            materialCopy.setSupplierId(sourceMaterial.getSupplierId());
            materialCopy.setQuantityPlanned(sourceMaterial.getQuantityPlanned());
            materialCopy.setUom(sourceMaterial.getUom());
            materialCopy.setUnitCostSnapshot(sourceMaterial.getUnitCostSnapshot());
            materialCopy.setCostSnapshotAt(sourceMaterial.getCostSnapshotAt());
            materialCopy.setQuantityOrdered(sourceMaterial.getQuantityOrdered());
            materialCopy.setQuantityConsumed(sourceMaterial.getQuantityConsumed());
            materialCopy.setStatus(sourceMaterial.getStatus());
            materialCopy.setNotes(sourceMaterial.getNotes());
            materials.save(materialCopy);
        }
        for (ProjectMaterialGroup child : childrenByParent.getOrDefault(source.getId(), List.of())) {
            copyGroupTree(child, saved.getId(), childrenByParent, materialsByGroup, companyId, projectId, false);
        }
        return saved;
    }

    private boolean isDescendant(Long candidateId, Long ancestorId, Long companyId, Long projectId) {
        Long cursor = candidateId;
        while (cursor != null) {
            ProjectMaterialGroup current = groups.findByIdAndCompanyId(cursor, companyId).orElse(null);
            if (current == null || !projectId.equals(current.getProjectId())) return false;
            if (ancestorId.equals(current.getParentGroupId())) return true;
            cursor = current.getParentGroupId();
        }
        return false;
    }

    private BigDecimal recursiveGroupCost(Long groupId,
                                          Map<Long, BigDecimal> directGroupTotals,
                                          Map<Long, List<Long>> childGroups,
                                          Map<Long, BigDecimal> memo,
                                          Set<Long> visiting) {
        BigDecimal cached = memo.get(groupId);
        if (cached != null) return cached;
        // Evita una recursión infinita si existieran datos históricos con un ciclo.
        if (!visiting.add(groupId)) return BigDecimal.ZERO;

        BigDecimal directTotal = directGroupTotals.getOrDefault(groupId, BigDecimal.ZERO);
        BigDecimal childrenTotal = childGroups.getOrDefault(groupId, List.of()).stream()
                .map(childId -> recursiveGroupCost(childId, directGroupTotals, childGroups, memo, visiting))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        visiting.remove(groupId);

        BigDecimal total = money(directTotal.add(childrenTotal));
        memo.put(groupId, total);
        return total;
    }

    private ProjectMaterialDtos.GroupResponse toGroupResponse(ProjectMaterialGroup group,
                                                              List<ProjectMaterialDtos.MaterialResponse> rows) {
        BigDecimal total = rows.stream().map(ProjectMaterialDtos.MaterialResponse::estimatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return toGroupResponse(group, rows, total);
    }

    private ProjectMaterialDtos.GroupResponse toGroupResponse(ProjectMaterialGroup group,
                                                              List<ProjectMaterialDtos.MaterialResponse> rows,
                                                              BigDecimal total) {
        return new ProjectMaterialDtos.GroupResponse(group.getId(), group.getParentGroupId(), group.getName(),
                group.getSortOrder(), rows, money(total));
    }

    private ProjectMaterialDtos.MaterialResponse toMaterialResponse(ProjectMaterial material) {
        Product product = products.findByIdAndCompanyId(material.getProductId(), material.getCompanyId()).orElse(null);
        Supplier supplier = material.getSupplierId() == null ? null
                : suppliers.findByIdAndCompanyId(material.getSupplierId(), material.getCompanyId()).orElse(null);
        return new ProjectMaterialDtos.MaterialResponse(material.getId(), material.getGroupId(), material.getProductId(),
                product == null ? null : product.getSku(), product == null ? null : product.getName(),
                material.getQuantityPlanned(), material.getUom(), material.getSupplierId(),
                supplier == null ? null : supplier.getName(), material.getUnitCostSnapshot(), estimatedAmount(material),
                material.getStatus(), material.getNotes(), material.getCostSnapshotAt(),
                material.getQuoteId());
    }

    private BigDecimal estimatedAmount(ProjectMaterial material) {
        return money((material.getUnitCostSnapshot() == null ? BigDecimal.ZERO : material.getUnitCostSnapshot())
                .multiply(material.getQuantityPlanned() == null ? BigDecimal.ZERO : material.getQuantityPlanned()));
    }

    private Project mutableProject(Long id) {
        Project project = findProject(id);
        if ("closed".equals(project.getStatus()) || "cancelled".equals(project.getStatus())) {
            throw new IllegalStateException("El proyecto está " + project.getStatus() + "; no admite cambios de planificación");
        }
        return project;
    }

    private Project findProject(Long id) {
        return projects.findByIdAndCompanyId(id, tenant.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto " + id + " no encontrado"));
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
