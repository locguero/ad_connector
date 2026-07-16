package com.example.iam.ad.web;

import com.example.iam.ad.domain.AdDomain;
import com.example.iam.ad.dto.AdObjectRef;
import com.example.iam.ad.dto.MoveObjectRequest;
import com.example.iam.ad.service.AdOrgUnitService;
import com.example.iam.ad.web.ApiRequests.MoveObjectBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ad/{domain}")
@Tag(name = "Organizational Units", description = "OU verification and object relocation")
public class AdOrgUnitController {

    private final AdOrgUnitService orgUnitService;

    public AdOrgUnitController(AdOrgUnitService orgUnitService) {
        this.orgUnitService = orgUnitService;
    }

    @GetMapping("/ous/exists")
    @Operation(summary = "Check whether an OU exists")
    public Map<String, Boolean> ouExists(@PathVariable AdDomain domain,
                                         @Parameter(description = "Full DN of the OU, URL-encoded")
                                         @RequestParam String dn) {
        return Map.of("exists", orgUnitService.ouExists(domain, dn));
    }

    @PostMapping("/objects/move")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Move a user or group to another OU",
            description = "Fails with 404 if the target OU does not exist.")
    public void moveObject(@PathVariable AdDomain domain, @Valid @RequestBody MoveObjectBody body) {
        AdObjectRef.Type refType = body.refType() != null
                ? body.refType() : AdObjectRef.Type.SAM_ACCOUNT_NAME;
        orgUnitService.moveObject(new MoveObjectRequest(domain,
                new AdObjectRef(refType, body.value()), body.targetOu()));
    }
}
