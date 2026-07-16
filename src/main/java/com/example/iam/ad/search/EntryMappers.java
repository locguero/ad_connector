package com.example.iam.ad.search;

import com.example.iam.ad.dto.AdGroup;
import com.example.iam.ad.dto.AdUser;
import com.example.iam.ad.util.GroupTypeHelper;
import com.example.iam.ad.util.ObjectGuidConverter;
import com.example.iam.ad.util.UserAccountControl;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.SearchResultEntry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps raw LDAP entries to framework DTOs. Every mapping resolves the binary
 * {@code objectGUID} to its canonical string form so DTOs always carry the
 * durable foreign key.
 */
public final class EntryMappers {

    /** Standard attribute sets requested by user/group reads and searches. */
    public static final String[] USER_ATTRIBUTES = {
            "objectGUID", "sAMAccountName", "userPrincipalName", "givenName", "sn",
            "displayName", "mail", "userAccountControl", "memberOf", "whenCreated"
    };

    public static final String[] GROUP_ATTRIBUTES = {
            "objectGUID", "cn", "sAMAccountName", "description", "groupType", "member"
    };

    private EntryMappers() {
    }

    public static AdUser toUser(SearchResultEntry entry) {
        int uac = intValue(entry, "userAccountControl");
        return new AdUser(
                guidOf(entry),
                entry.getDN(),
                entry.getAttributeValue("sAMAccountName"),
                entry.getAttributeValue("userPrincipalName"),
                entry.getAttributeValue("displayName"),
                entry.getAttributeValue("mail"),
                !UserAccountControl.isSet(uac, UserAccountControl.ACCOUNT_DISABLED),
                uac,
                valuesOf(entry, "memberOf"),
                attributeMap(entry));
    }

    public static AdGroup toGroup(SearchResultEntry entry) {
        int groupType = intValue(entry, "groupType");
        return new AdGroup(
                guidOf(entry),
                entry.getDN(),
                entry.getAttributeValue("cn"),
                entry.getAttributeValue("sAMAccountName"),
                entry.getAttributeValue("description"),
                groupType,
                GroupTypeHelper.isSecurityGroup(groupType),
                valuesOf(entry, "member"),
                attributeMap(entry));
    }

    private static String guidOf(SearchResultEntry entry) {
        byte[] raw = entry.getAttributeValueBytes("objectGUID");
        return raw != null ? ObjectGuidConverter.toCanonicalString(raw) : null;
    }

    private static int intValue(SearchResultEntry entry, String attribute) {
        Integer value = entry.getAttributeValueAsInteger(attribute);
        return value != null ? value : 0;
    }

    private static List<String> valuesOf(SearchResultEntry entry, String attribute) {
        String[] values = entry.getAttributeValues(attribute);
        return values != null ? List.of(values) : List.of();
    }

    private static Map<String, List<String>> attributeMap(SearchResultEntry entry) {
        Map<String, List<String>> attributes = new LinkedHashMap<>();
        for (Attribute attribute : entry.getAttributes()) {
            if ("objectGUID".equalsIgnoreCase(attribute.getName())) {
                attributes.put("objectGUID", List.of(guidOf(entry)));
            } else {
                attributes.put(attribute.getName(), List.of(attribute.getValues()));
            }
        }
        return attributes;
    }
}
