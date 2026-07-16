package com.example.iam.ad.group;

/** Available strategies for the synthetic "deactivate group" operation. */
public enum GroupDeactivationMode {
    /** Remove all members but keep the group object in place (default). */
    STRIP_MEMBERSHIP,
    /** Clear the security bit so the group grants no access (Security → Distribution). */
    CONVERT_TO_DISTRIBUTION,
    /** Move the group to the domain's configured quarantine OU. */
    MOVE_TO_QUARANTINE_OU
}
