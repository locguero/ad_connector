package com.example.iam.ad.search;

import com.example.iam.ad.exception.LdapExceptionMapper;
import com.unboundid.asn1.ASN1OctetString;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs subtree searches with UnboundID's {@link SimplePagedResultsControl}
 * so result sets are never silently truncated at AD's 1,000-object
 * MaxPageSize limit.
 *
 * <p>The paging cookie is only valid on the connection (and DC) that issued
 * it, so the whole page loop is pinned to a single connection checked out of
 * the pool rather than issuing each page through the pool facade.</p>
 */
@Component
public class PagedSearchExecutor {

    public static final int DEFAULT_PAGE_SIZE = 500;

    private static final Logger log = LoggerFactory.getLogger(PagedSearchExecutor.class);

    public List<SearchResultEntry> search(LDAPConnectionPool pool, String baseDn, Filter filter,
                                          Integer pageSize, Integer maxResults,
                                          String... attributes) {
        int effectivePageSize = pageSize != null ? pageSize : DEFAULT_PAGE_SIZE;
        LDAPConnection connection;
        try {
            connection = pool.getConnection();
        } catch (LDAPException e) {
            throw LdapExceptionMapper.map("acquireConnection pool=" + pool.getConnectionPoolName(), e);
        }

        List<SearchResultEntry> entries = new ArrayList<>();
        try {
            ASN1OctetString cookie = null;
            int pages = 0;
            do {
                SearchRequest request = new SearchRequest(baseDn, SearchScope.SUB, filter, attributes);
                request.addControl(new SimplePagedResultsControl(effectivePageSize, cookie));
                SearchResult result = connection.search(request);
                entries.addAll(result.getSearchEntries());
                pages++;

                if (maxResults != null && entries.size() >= maxResults) {
                    log.warn("Paged search under {} stopped at maxResults={} (filter={})",
                            baseDn, maxResults, filter);
                    return new ArrayList<>(entries.subList(0, maxResults));
                }

                SimplePagedResultsControl response = SimplePagedResultsControl.get(result);
                cookie = (response != null && response.moreResultsToReturn())
                        ? response.getCookie() : null;
            } while (cookie != null && cookie.getValueLength() > 0);

            log.debug("Paged search under {} returned {} entries in {} page(s) (filter={})",
                    baseDn, entries.size(), pages, filter);
            return entries;
        } catch (LDAPException e) {
            pool.releaseConnectionAfterException(connection, e);
            connection = null;
            throw LdapExceptionMapper.map("pagedSearch base=" + baseDn + " filter=" + filter, e);
        } finally {
            if (connection != null) {
                pool.releaseConnection(connection);
            }
        }
    }
}
