package com.example.iam.ad.search;

import com.example.iam.ad.dto.AdObjectRef;
import com.example.iam.ad.exception.IamIntegrationException;
import com.example.iam.ad.exception.LdapExceptionMapper;
import com.example.iam.ad.exception.ObjectNotFoundException;
import com.example.iam.ad.util.ObjectGuidConverter;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import org.springframework.stereotype.Component;

/**
 * Resolves an {@link AdObjectRef} (GUID / DN / sAMAccountName) to a single
 * directory entry, throwing {@link ObjectNotFoundException} when absent —
 * the signal the replication-lag retry wrapper keys on.
 */
@Component
public class DnResolver {

    public SearchResultEntry findEntry(LDAPConnectionPool pool, String baseDn, AdObjectRef ref,
                                       Filter categoryFilter, String... attributes) {
        try {
            return switch (ref.type()) {
                case DN -> {
                    SearchResultEntry entry = pool.getEntry(ref.value(), attributes);
                    if (entry == null) {
                        throw new ObjectNotFoundException("No entry found at " + ref);
                    }
                    yield entry;
                }
                case OBJECT_GUID -> searchSingle(pool, baseDn, ref,
                        Filter.createANDFilter(categoryFilter,
                                Filter.createEqualityFilter("objectGUID",
                                        ObjectGuidConverter.toBytes(ref.value()))),
                        attributes);
                case SAM_ACCOUNT_NAME -> searchSingle(pool, baseDn, ref,
                        Filter.createANDFilter(categoryFilter,
                                Filter.createEqualityFilter("sAMAccountName", ref.value())),
                        attributes);
            };
        } catch (LDAPException e) {
            throw LdapExceptionMapper.map("resolve " + ref, e);
        }
    }

    public String resolveDn(LDAPConnectionPool pool, String baseDn, AdObjectRef ref,
                            Filter categoryFilter) {
        if (ref.type() == AdObjectRef.Type.DN) {
            return ref.value();
        }
        return findEntry(pool, baseDn, ref, categoryFilter, SearchRequest.NO_ATTRIBUTES).getDN();
    }

    private SearchResultEntry searchSingle(LDAPConnectionPool pool, String baseDn, AdObjectRef ref,
                                           Filter filter, String... attributes)
            throws LDAPException {
        SearchRequest request = new SearchRequest(baseDn, SearchScope.SUB, filter, attributes);
        request.setSizeLimit(2);
        SearchResult result;
        try {
            result = pool.search(request);
        } catch (LDAPSearchException e) {
            if (e.getResultCode() == ResultCode.SIZE_LIMIT_EXCEEDED) {
                throw new IamIntegrationException("Ambiguous reference " + ref
                        + ": multiple entries matched under " + baseDn);
            }
            throw e;
        }
        if (result.getEntryCount() == 0) {
            throw new ObjectNotFoundException("No entry found for " + ref + " under " + baseDn);
        }
        if (result.getEntryCount() > 1) {
            throw new IamIntegrationException("Ambiguous reference " + ref
                    + ": multiple entries matched under " + baseDn);
        }
        return result.getSearchEntries().get(0);
    }
}
