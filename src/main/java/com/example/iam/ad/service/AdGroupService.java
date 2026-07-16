package com.example.iam.ad.service;

import com.example.iam.ad.config.AdConnectorProperties;
import com.example.iam.ad.config.AdConnectorProperties.DomainConfig;
import com.example.iam.ad.connection.AdConnectionRegistry;
import com.example.iam.ad.domain.AdDomain;
import com.example.iam.ad.dto.AdGroup;
import com.example.iam.ad.dto.AdObjectRef;
import com.example.iam.ad.dto.CreateGroupRequest;
import com.example.iam.ad.dto.DeactivateGroupRequest;
import com.example.iam.ad.dto.GroupSearchRequest;
import com.example.iam.ad.exception.IamIntegrationException;
import com.example.iam.ad.exception.LdapExceptionMapper;
import com.example.iam.ad.group.GroupDeactivationMode;
import com.example.iam.ad.group.GroupDeactivationStrategy;
import com.example.iam.ad.metrics.AdOperationTimer;
import com.example.iam.ad.retry.ReplicationRetrySupport;
import com.example.iam.ad.search.AdFilters;
import com.example.iam.ad.search.DnResolver;
import com.example.iam.ad.search.EntryMappers;
import com.example.iam.ad.search.PagedSearchExecutor;
import com.example.iam.ad.util.GroupTypeHelper;
import com.unboundid.ldap.sdk.AddRequest;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.DeleteRequest;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.RDN;
import com.unboundid.ldap.sdk.SearchResultEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Group lifecycle operations, including the synthetic "deactivate" built on a
 * pluggable {@link GroupDeactivationStrategy}.
 */
@Service
public class AdGroupService {

    private static final Logger log = LoggerFactory.getLogger(AdGroupService.class);

    private final AdConnectionRegistry registry;
    private final PagedSearchExecutor pagedSearchExecutor;
    private final DnResolver dnResolver;
    private final ReplicationRetrySupport replicationRetry;
    private final Map<GroupDeactivationMode, GroupDeactivationStrategy> deactivationStrategies;
    private final GroupDeactivationMode defaultDeactivationMode;
    private final AdOperationTimer operationTimer;

    public AdGroupService(AdConnectionRegistry registry, PagedSearchExecutor pagedSearchExecutor,
                          DnResolver dnResolver, ReplicationRetrySupport replicationRetry,
                          List<GroupDeactivationStrategy> strategies,
                          AdConnectorProperties properties,
                          AdOperationTimer operationTimer) {
        this.registry = registry;
        this.pagedSearchExecutor = pagedSearchExecutor;
        this.dnResolver = dnResolver;
        this.replicationRetry = replicationRetry;
        this.operationTimer = operationTimer;
        this.deactivationStrategies = new EnumMap<>(GroupDeactivationMode.class);
        strategies.forEach(s -> this.deactivationStrategies.put(s.mode(), s));
        this.defaultDeactivationMode = properties.defaultGroupDeactivationMode();
    }

    // ------------------------------------------------------------------ create

    public AdGroup createGroup(CreateGroupRequest request) {
        return operationTimer.execute(request.domain(), "createGroup", () -> doCreateGroup(request));
    }

    private AdGroup doCreateGroup(CreateGroupRequest request) {
        LDAPConnectionPool pool = registry.getPool(request.domain());
        String dn = new RDN("CN", request.name()) + "," + request.targetOu();
        int groupType = request.scope().flag()
                | (request.securityGroup() ? GroupTypeHelper.SECURITY_ENABLED : 0);

        List<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("objectClass", "top", "group"));
        attributes.add(new Attribute("sAMAccountName", request.name()));
        attributes.add(new Attribute("groupType", Integer.toString(groupType)));
        if (request.description() != null && !request.description().isBlank()) {
            attributes.add(new Attribute("description", request.description()));
        }
        request.additionalAttributes().forEach((name, values) ->
                attributes.add(new Attribute(name, values.toArray(String[]::new))));

        try {
            pool.add(new AddRequest(dn, attributes));
        } catch (LDAPException e) {
            throw LdapExceptionMapper.map("createGroup name=" + request.name(), e);
        }

        AdGroup group = replicationRetry.execute("readAfterCreateGroup",
                () -> doGetGroup(request.domain(), AdObjectRef.byDn(dn)));
        log.info("Created {} group {} in domain {} (dn={}, objectGuid={}, scope={})",
                request.securityGroup() ? "security" : "distribution", request.name(),
                request.domain(), dn, group.objectGuid(), request.scope());
        return group;
    }

    // -------------------------------------------------------------- read/search

    public AdGroup getGroup(AdDomain domain, AdObjectRef ref) {
        return operationTimer.execute(domain, "getGroup", () -> doGetGroup(domain, ref));
    }

    private AdGroup doGetGroup(AdDomain domain, AdObjectRef ref) {
        LDAPConnectionPool pool = registry.getPool(domain);
        DomainConfig cfg = registry.getConfig(domain);
        SearchResultEntry entry = dnResolver.findEntry(pool, cfg.baseDn(), ref,
                AdFilters.GROUP, EntryMappers.GROUP_ATTRIBUTES);
        return EntryMappers.toGroup(entry);
    }

    public List<AdGroup> searchGroups(GroupSearchRequest request) {
        return operationTimer.execute(request.domain(), "searchGroups",
                () -> doSearchGroups(request));
    }

    private List<AdGroup> doSearchGroups(GroupSearchRequest request) {
        LDAPConnectionPool pool = registry.getPool(request.domain());
        DomainConfig cfg = registry.getConfig(request.domain());

        Filter filter;
        if (request.ldapFilter() != null) {
            filter = Filter.createANDFilter(AdFilters.GROUP, AdFilters.parse(request.ldapFilter()));
        } else if (request.name() != null) {
            filter = Filter.createANDFilter(AdFilters.GROUP,
                    Filter.createORFilter(
                            Filter.createEqualityFilter("cn", request.name()),
                            Filter.createEqualityFilter("sAMAccountName", request.name())));
        } else {
            filter = AdFilters.GROUP;
        }
        String base = request.searchBase() != null ? request.searchBase() : cfg.baseDn();
        String[] attributes = request.attributes() == null || request.attributes().isEmpty()
                ? EntryMappers.GROUP_ATTRIBUTES
                : mergeWithGuid(request.attributes());

        List<SearchResultEntry> entries = pagedSearchExecutor.search(pool, base, filter,
                request.pageSize(), request.maxResults(), attributes);
        log.info("Group search in domain {} returned {} entries (filter={})",
                request.domain(), entries.size(), filter);
        return entries.stream().map(EntryMappers::toGroup).toList();
    }

    // ------------------------------------------------------------------ delete

    public void deleteGroup(AdDomain domain, AdObjectRef ref) {
        operationTimer.run(domain, "deleteGroup", () -> doDeleteGroup(domain, ref));
    }

    private void doDeleteGroup(AdDomain domain, AdObjectRef ref) {
        LDAPConnectionPool pool = registry.getPool(domain);
        DomainConfig cfg = registry.getConfig(domain);
        String dn = dnResolver.resolveDn(pool, cfg.baseDn(), ref, AdFilters.GROUP);
        try {
            pool.delete(new DeleteRequest(dn));
        } catch (LDAPException e) {
            throw LdapExceptionMapper.map("deleteGroup " + ref, e);
        }
        log.info("Deleted group {} in domain {} (dn={})", ref, domain, dn);
    }

    // -------------------------------------------------------------- deactivate

    public void deactivateGroup(DeactivateGroupRequest request) {
        operationTimer.run(request.domain(), "deactivateGroup", () -> doDeactivateGroup(request));
    }

    private void doDeactivateGroup(DeactivateGroupRequest request) {
        LDAPConnectionPool pool = registry.getPool(request.domain());
        DomainConfig cfg = registry.getConfig(request.domain());
        GroupDeactivationMode mode = request.mode() != null ? request.mode()
                : defaultDeactivationMode;
        GroupDeactivationStrategy strategy = deactivationStrategies.get(mode);
        if (strategy == null) {
            throw new IamIntegrationException("No strategy registered for deactivation mode " + mode);
        }
        SearchResultEntry entry = dnResolver.findEntry(pool, cfg.baseDn(), request.group(),
                AdFilters.GROUP, "objectGUID", "groupType", "cn");
        strategy.deactivate(pool, entry, cfg);
        log.info("Deactivated group {} in domain {} using strategy {}",
                request.group(), request.domain(), mode);
    }

    // ----------------------------------------------------------------- helpers

    private static String[] mergeWithGuid(List<String> requested) {
        List<String> merged = new ArrayList<>(requested);
        if (merged.stream().noneMatch("objectGUID"::equalsIgnoreCase)) {
            merged.add("objectGUID");
        }
        if (merged.stream().noneMatch("groupType"::equalsIgnoreCase)) {
            merged.add("groupType");
        }
        return merged.toArray(String[]::new);
    }
}
