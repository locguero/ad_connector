package com.example.iam.ad.web;

import com.example.iam.ad.domain.AdDomain;
import com.example.iam.ad.dto.AdObjectRef;
import com.example.iam.ad.dto.AdUser;
import com.example.iam.ad.dto.ProvisionUserRequest;
import com.example.iam.ad.dto.UpdateUserAttributesRequest;
import com.example.iam.ad.dto.UserSearchRequest;
import com.example.iam.ad.service.AdUserService;
import com.example.iam.ad.web.ApiRequests.CreateUserBody;
import com.example.iam.ad.web.ApiRequests.UpdateAttributesBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ad/{domain}/users")
@Tag(name = "Users", description = "User provisioning, lifecycle, and attribute operations")
public class AdUserController {

    private final AdUserService userService;

    public AdUserController(AdUserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Provision a user",
            description = "Creates the user in the target OU. Accounts requested enabled "
                    + "without an initial password are created disabled.")
    public AdUser create(@PathVariable AdDomain domain, @Valid @RequestBody CreateUserBody body) {
        return userService.createUser(new ProvisionUserRequest(domain,
                body.commonName(), body.sAMAccountName(), body.userPrincipalName(),
                body.givenName(), body.surname(), body.displayName(), body.mail(),
                body.targetOu(), body.initialPassword(), body.enabled(),
                body.additionalAttributes()));
    }

    @GetMapping
    @Operation(summary = "Search users",
            description = "Paginated via Simple Paged Results — never truncated at AD's "
                    + "1,000-object limit. Provide either an LDAP filter or a sAMAccountName; "
                    + "neither returns all users under the search base.")
    public List<AdUser> search(@PathVariable AdDomain domain,
                               @Parameter(description = "Raw LDAP filter, AND-ed with the user category filter")
                               @RequestParam(required = false) String filter,
                               @RequestParam(required = false) String samAccountName,
                               @RequestParam(required = false) String searchBase,
                               @RequestParam(required = false) Integer pageSize,
                               @RequestParam(required = false) Integer maxResults) {
        return userService.searchUsers(new UserSearchRequest(domain, filter, samAccountName,
                searchBase, null, pageSize, maxResults));
    }

    @GetMapping("/{value}")
    @Operation(summary = "Get one user (returns objectGUID, attributes, and group memberships)")
    public AdUser get(@PathVariable AdDomain domain, @PathVariable String value,
                      @RequestParam(defaultValue = "SAM_ACCOUNT_NAME") AdObjectRef.Type refType) {
        return userService.getUser(domain, new AdObjectRef(refType, value));
    }

    @PatchMapping("/{value}/attributes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Update arbitrary user attributes",
            description = "Replace semantics; an empty value list removes the attribute.")
    public void updateAttributes(@PathVariable AdDomain domain, @PathVariable String value,
                                 @RequestParam(defaultValue = "SAM_ACCOUNT_NAME") AdObjectRef.Type refType,
                                 @Valid @RequestBody UpdateAttributesBody body) {
        userService.updateUserAttributes(new UpdateUserAttributesRequest(domain,
                new AdObjectRef(refType, value), body.attributes()));
    }

    @PostMapping("/{value}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Enable a user",
            description = "Clears only the ACCOUNT_DISABLED bit of userAccountControl; "
                    + "all other flags are preserved.")
    public void enable(@PathVariable AdDomain domain, @PathVariable String value,
                       @RequestParam(defaultValue = "SAM_ACCOUNT_NAME") AdObjectRef.Type refType) {
        userService.enableUser(domain, new AdObjectRef(refType, value));
    }

    @PostMapping("/{value}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Disable a user",
            description = "Sets only the ACCOUNT_DISABLED bit of userAccountControl; "
                    + "all other flags are preserved.")
    public void disable(@PathVariable AdDomain domain, @PathVariable String value,
                        @RequestParam(defaultValue = "SAM_ACCOUNT_NAME") AdObjectRef.Type refType) {
        userService.disableUser(domain, new AdObjectRef(refType, value));
    }

    @DeleteMapping("/{value}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deprovision (delete) a user")
    public void delete(@PathVariable AdDomain domain, @PathVariable String value,
                       @RequestParam(defaultValue = "SAM_ACCOUNT_NAME") AdObjectRef.Type refType) {
        userService.deleteUser(domain, new AdObjectRef(refType, value));
    }
}
