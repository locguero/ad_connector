package com.example.iam.ad.web;

import com.example.iam.ad.domain.AdDomain;
import com.example.iam.ad.dto.AdGroup;
import com.example.iam.ad.dto.AdObjectRef;
import com.example.iam.ad.dto.CreateGroupRequest;
import com.example.iam.ad.dto.DeactivateGroupRequest;
import com.example.iam.ad.dto.GroupMembershipRequest;
import com.example.iam.ad.dto.GroupSearchRequest;
import com.example.iam.ad.service.AdGroupService;
import com.example.iam.ad.service.AdUserService;
import com.example.iam.ad.web.ApiRequests.CreateGroupBody;
import com.example.iam.ad.web.ApiRequests.DeactivateGroupBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ad/{domain}/groups")
@Tag(name = "Groups", description = "Group lifecycle, deactivation strategies, and membership")
public class AdGroupController {

    private final AdGroupService groupService;
    private final AdUserService userService;

    public AdGroupController(AdGroupService groupService, AdUserService userService) {
        this.groupService = groupService;
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a group")
    public AdGroup create(@PathVariable AdDomain domain, @Valid @RequestBody CreateGroupBody body) {
        return groupService.createGroup(new CreateGroupRequest(domain, body.name(),
                body.targetOu(), body.scope(),
                body.securityGroup() == null || body.securityGroup(),
                body.description(), body.additionalAttributes()));
    }

    @GetMapping
    @Operation(summary = "Search groups (paginated via Simple Paged Results)")
    public List<AdGroup> search(@PathVariable AdDomain domain,
                                @RequestParam(required = false) String filter,
                                @RequestParam(required = false) String name,
                                @RequestParam(required = false) String searchBase,
                                @RequestParam(required = false) Integer pageSize,
                                @RequestParam(required = false) Integer maxResults) {
        return groupService.searchGroups(new GroupSearchRequest(domain, filter, name,
                searchBase, null, pageSize, maxResults));
    }

    @GetMapping("/{value}")
    @Operation(summary = "Get one group (returns objectGUID and direct members)")
    public AdGroup get(@PathVariable AdDomain domain, @PathVariable String value,
                       @RequestParam(defaultValue = "SAM_ACCOUNT_NAME") AdObjectRef.Type refType) {
        return groupService.getGroup(domain, new AdObjectRef(refType, value));
    }

    @DeleteMapping("/{value}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a group")
    public void delete(@PathVariable AdDomain domain, @PathVariable String value,
                       @RequestParam(defaultValue = "SAM_ACCOUNT_NAME") AdObjectRef.Type refType) {
        groupService.deleteGroup(domain, new AdObjectRef(refType, value));
    }

    @PostMapping("/{value}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a group",
            description = "AD has no native disabled state for groups; applies the requested "
                    + "strategy (STRIP_MEMBERSHIP, CONVERT_TO_DISTRIBUTION, "
                    + "MOVE_TO_QUARANTINE_OU) or the configured default.")
    public void deactivate(@PathVariable AdDomain domain, @PathVariable String value,
                           @RequestParam(defaultValue = "SAM_ACCOUNT_NAME") AdObjectRef.Type refType,
                           @RequestBody(required = false) DeactivateGroupBody body) {
        groupService.deactivateGroup(new DeactivateGroupRequest(domain,
                new AdObjectRef(refType, value), body != null ? body.mode() : null));
    }

    @PostMapping("/{value}/members/{userValue}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Add a user to the group",
            description = "Idempotent; retried with backoff to absorb inter-DC replication lag "
                    + "after a fresh user creation.")
    public void addMember(@PathVariable AdDomain domain, @PathVariable String value,
                          @PathVariable String userValue,
                          @RequestParam(defaultValue = "SAM_ACCOUNT_NAME") AdObjectRef.Type refType,
                          @RequestParam(defaultValue = "SAM_ACCOUNT_NAME") AdObjectRef.Type userRefType) {
        userService.addUserToGroup(new GroupMembershipRequest(domain,
                new AdObjectRef(userRefType, userValue), new AdObjectRef(refType, value)));
    }

    @DeleteMapping("/{value}/members/{userValue}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a user from the group", description = "Idempotent.")
    public void removeMember(@PathVariable AdDomain domain, @PathVariable String value,
                             @PathVariable String userValue,
                             @RequestParam(defaultValue = "SAM_ACCOUNT_NAME") AdObjectRef.Type refType,
                             @RequestParam(defaultValue = "SAM_ACCOUNT_NAME") AdObjectRef.Type userRefType) {
        userService.removeUserFromGroup(new GroupMembershipRequest(domain,
                new AdObjectRef(userRefType, userValue), new AdObjectRef(refType, value)));
    }
}
