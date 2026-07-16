package com.example.iam.ad.service;

import com.example.iam.ad.config.AdConnectorProperties.DomainConfig;
import com.example.iam.ad.connection.AdConnectionRegistry;
import com.example.iam.ad.domain.AdDomain;
import com.example.iam.ad.dto.AdObjectRef;
import com.example.iam.ad.dto.AdUser;
import com.example.iam.ad.dto.GroupMembershipRequest;
import com.example.iam.ad.dto.ProvisionUserRequest;
import com.example.iam.ad.dto.UpdateUserAttributesRequest;
import com.example.iam.ad.dto.UserSearchRequest;
import com.example.iam.ad.exception.IamIntegrationException;
import com.example.iam.ad.exception.LdapExceptionMapper;
import com.example.iam.ad.metrics.AdOperationTimer;
import com.example.iam.ad.retry.ReplicationRetrySupport;
import com.example.iam.ad.search.AdFilters;
import com.example.iam.ad.search.DnResolver;
import com.example.iam.ad.search.EntryMappers;
import com.example.iam.ad.search.PagedSearchExecutor;
import com.example.iam.ad.util.UserAccountControl;
import com.unboundid.ldap.sdk.AddRequest;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.DeleteRequest;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import com.unboundid.ldap.sdk.RDN;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchResultEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User lifecycle operations (provision, modify, enable/disable, deprovision,
 * search, group membership) routed per-domain and exposed exclusively through
 * connector DTOs.
 */
@Service
public class AdUserService {

    private static final Logger log = LoggerFactory.getLogger(AdUserService.class);

    private final AdConnectionRegistry registry;
    private final PagedSearchExecutor pagedSearchExecutor;
    private final DnResolver dnResolver;
    private final ReplicationRetrySupport replicationRetry;
    private final AdOperationTimer operationTimer;

    public AdUserService(AdConnectionRegistry registry, PagedSearchExecutor pagedSearchExecutor,
                         DnResolver dnResolver, ReplicationRetrySupport replicationRetry,
                         AdOperationTimer operationTimer) {
        this.registry = registry;
        this.pagedSearchExecutor = pagedSearchExecutor;
        this.dnResolver = dnResolver;
        this.replicationRetry = replicationRetry;
        this.operationTimer = operationTimer;
    }

    // ------------------------------------------------------------------ create

    public AdUser createUser(ProvisionUserRequest request) {
        return operationTimer.execute(request.domain(), "createUser", () -> doCreateUser(request));
    }

    private AdUser doCreateUser(ProvisionUserRequest request) {
        LDAPConnectionPool pool = registry.getPool(request.domain());
        String dn = new RDN("CN", request.commonName()) + "," + request.targetOu();

        boolean hasPassword = request.initialPassword() != null && !request.initialPassword().isEmpty();
        if (request.enabled() && !hasPassword) {
            log.warn("User {} requested enabled without an initial password; creating disabled "
                    + "(AD rejects enabled accounts without a password)", request.sAMAccountName());
        }
        int uac = UserAccountControl.NORMAL_ACCOUNT;
        if (!(request.enabled() && hasPassword)) {
            uac = UserAccountControl.setFlag(uac, UserAccountControl.ACCOUNT_DISABLED);
        }

        List<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("objectClass", "top", "person", "organizationalPerson", "user"));
        attributes.add(new Attribute("sAMAccountName", request.sAMAccountName()));
        attributes.add(new Attribute("userAccountControl", Integer.toString(uac)));
        addIfPresent(attributes, "userPrincipalName", request.userPrincipalName());
        addIfPresent(attributes, "givenName", request.givenName());
        addIfPresent(attributes, "sn", request.surname());
        addIfPresent(attributes, "displayName", request.displayName());
        addIfPresent(attributes, "mail", request.mail());
        if (hasPassword) {
            attributes.add(new Attribute("unicodePwd", encodePassword(request.initialPassword())));
        }
        request.additionalAttributes().forEach((name, values) ->
                attributes.add(new Attribute(name, values.toArray(String[]::new))));

        try {
            pool.add(new AddRequest(dn, attributes));
        } catch (LDAPException e) {
            throw LdapExceptionMapper.map(
                    "createUser sAMAccountName=" + request.sAMAccountName(), e);
        }

        // Read-back may hit a DC that hasn't replicated the add yet.
        AdUser user = replicationRetry.execute("readAfterCreateUser",
                () -> doGetUser(request.domain(), AdObjectRef.byDn(dn)));
        log.info("Provisioned user {} in domain {} (dn={}, objectGuid={}, enabled={})",
                request.sAMAccountName(), request.domain(), dn, user.objectGuid(), user.enabled());
        return user;
    }

    // -------------------------------------------------------------- read/search

    public AdUser getUser(AdDomain domain, AdObjectRef ref) {
        return operationTimer.execute(domain, "getUser", () -> doGetUser(domain, ref));
    }

    private AdUser doGetUser(AdDomain domain, AdObjectRef ref) {
        LDAPConnectionPool pool = registry.getPool(domain);
        DomainConfig cfg = registry.getConfig(domain);
        SearchResultEntry entry = dnResolver.findEntry(pool, cfg.baseDn(), ref,
                AdFilters.USER, EntryMappers.USER_ATTRIBUTES);
        return EntryMappers.toUser(entry);
    }

    public List<AdUser> searchUsers(UserSearchRequest request) {
        return operationTimer.execute(request.domain(), "searchUsers", () -> doSearchUsers(request));
    }

    private List<AdUser> doSearchUsers(UserSearchRequest request) {
        LDAPConnectionPool pool = registry.getPool(request.domain());
        DomainConfig cfg = registry.getConfig(request.domain());

        Filter filter;
        if (request.ldapFilter() != null) {
            filter = Filter.createANDFilter(AdFilters.USER, AdFilters.parse(request.ldapFilter()));
        } else if (request.sAMAccountName() != null) {
            filter = Filter.createANDFilter(AdFilters.USER,
                    Filter.createEqualityFilter("sAMAccountName", request.sAMAccountName()));
        } else {
            filter = AdFilters.USER;
        }
        String base = request.searchBase() != null ? request.searchBase() : cfg.baseDn();
        String[] attributes = mergeAttributes(request.attributes(),
                EntryMappers.USER_ATTRIBUTES, "objectGUID", "userAccountControl", "memberOf");

        List<SearchResultEntry> entries = pagedSearchExecutor.search(pool, base, filter,
                request.pageSize(), request.maxResults(), attributes);
        log.info("User search in domain {} returned {} entries (filter={})",
                request.domain(), entries.size(), filter);
        return entries.stream().map(EntryMappers::toUser).toList();
    }

    // ------------------------------------------------------------------ modify

    public void updateUserAttributes(UpdateUserAttributesRequest request) {
        operationTimer.run(request.domain(), "updateUserAttributes",
                () -> doUpdateUserAttributes(request));
    }

    private void doUpdateUserAttributes(UpdateUserAttributesRequest request) {
        LDAPConnectionPool pool = registry.getPool(request.domain());
        DomainConfig cfg = registry.getConfig(request.domain());
        String dn = dnResolver.resolveDn(pool, cfg.baseDn(), request.user(), AdFilters.USER);

        List<Modification> modifications = new ArrayList<>();
        for (Map.Entry<String, List<String>> attr : request.attributes().entrySet()) {
            if (attr.getValue() == null || attr.getValue().isEmpty()) {
                modifications.add(new Modification(ModificationType.REPLACE, attr.getKey()));
            } else {
                modifications.add(new Modification(ModificationType.REPLACE, attr.getKey(),
                        attr.getValue().toArray(String[]::new)));
            }
        }
        try {
            pool.modify(dn, modifications);
        } catch (LDAPException e) {
            throw LdapExceptionMapper.map("updateUserAttributes user=" + request.user(), e);
        }
        // Attribute names only — values may contain PII.
        log.info("Updated attributes {} on user {} in domain {}",
                request.attributes().keySet(), request.user(), request.domain());
    }

    public void enableUser(AdDomain domain, AdObjectRef ref) {
        operationTimer.run(domain, "enableUser", () -> setAccountEnabled(domain, ref, true));
    }

    public void disableUser(AdDomain domain, AdObjectRef ref) {
        operationTimer.run(domain, "disableUser", () -> setAccountEnabled(domain, ref, false));
    }

    /**
     * Flips only the ACCOUNT_DISABLED bit via read-modify-write so every other
     * userAccountControl flag (DONT_EXPIRE_PASSWORD, SMARTCARD_REQUIRED, ...)
     * is preserved.
     */
    private void setAccountEnabled(AdDomain domain, AdObjectRef ref, boolean enabled) {
        LDAPConnectionPool pool = registry.getPool(domain);
        DomainConfig cfg = registry.getConfig(domain);
        SearchResultEntry entry = dnResolver.findEntry(pool, cfg.baseDn(), ref,
                AdFilters.USER, "userAccountControl");
        Integer current = entry.getAttributeValueAsInteger("userAccountControl");
        if (current == null) {
            throw new IamIntegrationException(
                    "userAccountControl not readable for " + ref + " in domain " + domain);
        }
        int updated = enabled
                ? UserAccountControl.clearFlag(current, UserAccountControl.ACCOUNT_DISABLED)
                : UserAccountControl.setFlag(current, UserAccountControl.ACCOUNT_DISABLED);
        if (updated == current) {
            log.info("User {} in domain {} already {} (userAccountControl={})",
                    ref, domain, enabled ? "enabled" : "disabled", current);
            return;
        }
        try {
            pool.modify(entry.getDN(), new Modification(ModificationType.REPLACE,
                    "userAccountControl", Integer.toString(updated)));
        } catch (LDAPException e) {
            throw LdapExceptionMapper.map(
                    (enabled ? "enableUser " : "disableUser ") + ref, e);
        }
        log.info("{} user {} in domain {} (userAccountControl {} -> {}, other flags preserved)",
                enabled ? "Enabled" : "Disabled", ref, domain, current, updated);
    }

    // ------------------------------------------------------------------ delete

    public void deleteUser(AdDomain domain, AdObjectRef ref) {
        operationTimer.run(domain, "deleteUser", () -> doDeleteUser(domain, ref));
    }

    private void doDeleteUser(AdDomain domain, AdObjectRef ref) {
        LDAPConnectionPool pool = registry.getPool(domain);
        DomainConfig cfg = registry.getConfig(domain);
        String dn = dnResolver.resolveDn(pool, cfg.baseDn(), ref, AdFilters.USER);
        try {
            pool.delete(new DeleteRequest(dn));
        } catch (LDAPException e) {
            throw LdapExceptionMapper.map("deleteUser " + ref, e);
        }
        log.info("Deprovisioned (deleted) user {} in domain {} (dn={})", ref, domain, dn);
    }

    // -------------------------------------------------------- group membership

    /**
     * Idempotent; wrapped in the replication-lag retry so a membership add
     * immediately after user creation succeeds even if this call lands on a
     * DC that hasn't seen the new user yet.
     */
    public void addUserToGroup(GroupMembershipRequest request) {
        operationTimer.run(request.domain(), "addUserToGroup", () ->
                replicationRetry.run("addUserToGroup", () ->
                        modifyMembership(request, ModificationType.ADD)));
    }

    public void removeUserFromGroup(GroupMembershipRequest request) {
        operationTimer.run(request.domain(), "removeUserFromGroup", () ->
                replicationRetry.run("removeUserFromGroup", () ->
                        modifyMembership(request, ModificationType.DELETE)));
    }

    private void modifyMembership(GroupMembershipRequest request, ModificationType type) {
        LDAPConnectionPool pool = registry.getPool(request.domain());
        DomainConfig cfg = registry.getConfig(request.domain());
        String userDn = dnResolver.resolveDn(pool, cfg.baseDn(), request.user(), AdFilters.USER);
        String groupDn = dnResolver.resolveDn(pool, cfg.baseDn(), request.group(), AdFilters.GROUP);
        boolean adding = type == ModificationType.ADD;
        try {
            pool.modify(groupDn, new Modification(type, "member", userDn));
        } catch (LDAPException e) {
            if (adding && e.getResultCode() == ResultCode.ATTRIBUTE_OR_VALUE_EXISTS) {
                log.info("User {} already a member of group {} in domain {}; add is a no-op",
                        request.user(), request.group(), request.domain());
                return;
            }
            if (!adding && e.getResultCode() == ResultCode.NO_SUCH_ATTRIBUTE) {
                log.info("User {} not a member of group {} in domain {}; remove is a no-op",
                        request.user(), request.group(), request.domain());
                return;
            }
            throw LdapExceptionMapper.map(
                    (adding ? "addUserToGroup " : "removeUserFromGroup ") + request.user(), e);
        }
        log.info("{} user {} {} group {} in domain {}",
                adding ? "Added" : "Removed", request.user(), adding ? "to" : "from",
                request.group(), request.domain());
    }

    // ----------------------------------------------------------------- helpers

    private static void addIfPresent(List<Attribute> attributes, String name, String value) {
        if (value != null && !value.isBlank()) {
            attributes.add(new Attribute(name, value));
        }
    }

    /** AD requires the quoted password encoded as UTF-16LE in unicodePwd. */
    private static byte[] encodePassword(String password) {
        return ('"' + password + '"').getBytes(StandardCharsets.UTF_16LE);
    }

    private static String[] mergeAttributes(List<String> requested, String[] defaults,
                                            String... mandatory) {
        if (requested == null || requested.isEmpty()) {
            return defaults;
        }
        Set<String> merged = new LinkedHashSet<>(requested);
        merged.addAll(List.of(mandatory));
        return merged.toArray(String[]::new);
    }
}
