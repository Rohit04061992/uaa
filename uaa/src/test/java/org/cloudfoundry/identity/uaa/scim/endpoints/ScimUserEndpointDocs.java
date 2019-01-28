/*******************************************************************************
 *     Cloud Foundry
 *     Copyright (c) [2009-2016] Pivotal Software, Inc. All Rights Reserved.
 *
 *     This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *     You may not use this product except in compliance with the License.
 *
 *     This product includes a number of subcomponents with
 *     separate copyright notices and license terms. Your use of these
 *     subcomponents is subject to the terms and conditions of the
 *     subcomponent's license, as noted in the LICENSE file.
 *******************************************************************************/
package org.cloudfoundry.identity.uaa.scim.endpoints;

import org.apache.commons.lang3.ArrayUtils;
import org.cloudfoundry.identity.uaa.TestSpringContext;
import org.cloudfoundry.identity.uaa.account.PasswordChangeRequest;
import org.cloudfoundry.identity.uaa.account.UserAccountStatus;
import org.cloudfoundry.identity.uaa.approval.Approval;
import org.cloudfoundry.identity.uaa.approval.ApprovalStore;
import org.cloudfoundry.identity.uaa.constants.OriginKeys;
import org.cloudfoundry.identity.uaa.mock.EndpointDocs;
import org.cloudfoundry.identity.uaa.mock.util.MockMvcUtils;
import org.cloudfoundry.identity.uaa.scim.ScimUser;
import org.cloudfoundry.identity.uaa.scim.ScimUserProvisioning;
import org.cloudfoundry.identity.uaa.test.HoneycombAuditEventTestListenerExtension;
import org.cloudfoundry.identity.uaa.test.HoneycombJdbcInterceptorExtension;
import org.cloudfoundry.identity.uaa.test.JUnitRestDocumentationExtension;
import org.cloudfoundry.identity.uaa.user.UaaUserDatabase;
import org.cloudfoundry.identity.uaa.util.JsonUtils;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneHolder;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneSwitchingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.request.ParameterDescriptor;
import org.springframework.restdocs.request.RequestDocumentation;
import org.springframework.restdocs.snippet.Snippet;
import org.springframework.security.oauth2.common.util.RandomValueStringGenerator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Collections;
import java.util.Date;

import static org.cloudfoundry.identity.uaa.test.SnippetUtils.fieldWithPath;
import static org.cloudfoundry.identity.uaa.test.SnippetUtils.parameterWithName;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.JsonFieldType.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.requestParameters;
import static org.springframework.restdocs.snippet.Attributes.key;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@ExtendWith(JUnitRestDocumentationExtension.class)
@ExtendWith(HoneycombJdbcInterceptorExtension.class)
@ExtendWith(HoneycombAuditEventTestListenerExtension.class)
@ActiveProfiles("default")
@WebAppConfiguration
@ContextConfiguration(classes = TestSpringContext.class)
class ScimUserEndpointDocs extends EndpointDocs {

    private final String startIndexDescription = "The starting index of the search results when paginated. Index starts with 1.";
    private final String countAndItemsPerPageDescription = "The maximum number of items returned per request.";
    private final String totalResultsDescription = "Number of results in result set.";

    private final String userIdDescription = "A guid generated by the UAA to uniquely identity this user.";
    private final String resourceDescription = "A list of SCIM user objects retrieved by the search.";
    private final String usernameDescription = "User name of the user, typically an email address.";
    private final String nameObjectDescription = "A map with the user's first name and last name.";
    private final String lastnameDescription = "The user's last name.";
    private final String firstnameDescription = "The user's first name.";
    private final String emailListDescription = "The user's email addresses.";
    private final String emailDescription = "The email address.";
    private final String emailPrimaryDescription = "Set to true if this is the user's primary email address.";
    private final String groupDescription = "A list of groups the user belongs to.";

    private final String schemasDescription = "SCIM schemas used, currently always set to `[ \"urn:scim:schemas:core:1.0\" ]`";
    private final String groupIdDescription = "A guid generated by the UAA to uniquely identity this group.";
    private final String groupDisplayNameDescription = "The group display name, also referred to as scope during authorization.";
    private final String membershipTypeDescription = "Membership type. `DIRECT` means the user is directly associated with the group. `INDIRECT` means that the membership is derived from a nested group.";
    private final String approvalsListDescription = "A list of approval decisions made by this user. Approvals record the user's explicit approval or rejection for an application's request for delegated permissions.";
    private final String approvalUserIdDescription = "The user id on the approval. Will be the same as the id field.";
    private final String approvalClientIdDescription = "The client id on the approval. Represents the application this approval or denial was for.";
    private final String approvalScopeDescription = "The scope on the approval. Will be a group display value.";
    private final String approvalStatusDescription = "The status of the approval. Status may be either `APPROVED` or `DENIED`.";
    private final String approvalsLastUpdatedAtDescription = "Date this approval was last updated.";
    private final String approvalsExpiresAtDescription = "Date this approval will expire.";
    private final String userActiveDescription = "Whether the user is allowed to log in. False acts as a soft delete; the user will not be able to log in.";
    private final String userVerifiedDescription = "Becomes true when the user verifies their email address.";
    private final String userOriginDescription = "The alias of the Identity Provider that authenticated this user. The value `uaa` indicates a user from the UAA's internal user store.";
    private final String userZoneIdDescription = "The Identity Zone this user belongs to. The value `uaa` refers to the default zone.";
    private final String passwordLastModifiedDescription = "The timestamp when this user's password was last changed.";
    private final String externalIdDescription = "External user ID if authenticated through an external identity provider.";
    private final String passwordDescription = "User's password, required if origin is set to `uaa`. May be be subject to validations if the UAA is configured with a password policy.";
    private final String phoneNumbersListDescription = "The user's phone numbers.";
    private final String phoneNumbersDescription = "The phone number.";

    private final String metaDesc = "SCIM object meta data.";
    private final String metaVersionDesc = "Object version.";
    private final String metaLastModifiedDesc = "Object last modified date.";
    private final String metaCreatedDesc = "Object created date.";
    private final String metaAttributesDesc = "Names of attributes that shall be deleted";
    private final String userLastLogonTimeDescription = "The unix epoch timestamp in milliseconds of when the user last authenticated. This field will be omitted from the response if the user has never authenticated.";
    private final String userPreviousLogonTimeDescription = "The unix epoch timestamp in milliseconds of 2nd to last successful user authentication. This field will only be included in the response once the user has authenticated two or more times.";

    private final String scimWriteOrUaaAdminRequired = "Access token with `scim.write` or `uaa.admin` required";

    private FieldDescriptor[] searchResponseFields = {
        fieldWithPath("startIndex").type(NUMBER).description(startIndexDescription),
        fieldWithPath("itemsPerPage").type(NUMBER).description(countAndItemsPerPageDescription),
        fieldWithPath("totalResults").type(NUMBER).description(totalResultsDescription),
        fieldWithPath("schemas").type(ARRAY).description(schemasDescription),
        fieldWithPath("resources").type(ARRAY).description(resourceDescription),
        fieldWithPath("resources[].schemas").type(ARRAY).description(schemasDescription),
        fieldWithPath("resources[].id").type(STRING).description(userIdDescription),
        fieldWithPath("resources[].userName").type(STRING).description(usernameDescription),
        fieldWithPath("resources[].name").type(OBJECT).description(nameObjectDescription),
        fieldWithPath("resources[].name.familyName").type(STRING).description(lastnameDescription),
        fieldWithPath("resources[].name.givenName").type(STRING).description(firstnameDescription),
        fieldWithPath("resources[].phoneNumbers").type(ARRAY).description(phoneNumbersListDescription),
        fieldWithPath("resources[].phoneNumbers[].value").type(STRING).description(phoneNumbersDescription),
        fieldWithPath("resources[].emails").type(ARRAY).description(emailListDescription),
        fieldWithPath("resources[].emails[].value").type(STRING).description(emailDescription),
        fieldWithPath("resources[].emails[].primary").type(BOOLEAN).description(emailPrimaryDescription),
        fieldWithPath("resources[].groups").type(ARRAY).description(groupDescription),
        fieldWithPath("resources[].groups[].value").type(STRING).description(groupIdDescription),
        fieldWithPath("resources[].groups[].display").type(STRING).description(groupDisplayNameDescription),
        fieldWithPath("resources[].groups[].type").type(STRING).description(membershipTypeDescription),
        fieldWithPath("resources[].approvals").type(ARRAY).description(approvalsListDescription),
        fieldWithPath("resources[].approvals[].userId").type(STRING).description(approvalUserIdDescription),
        fieldWithPath("resources[].approvals[].clientId").type(STRING).description(approvalClientIdDescription),
        fieldWithPath("resources[].approvals[].scope").type(STRING).description(approvalScopeDescription),
        fieldWithPath("resources[].approvals[].status").type(STRING).description(approvalStatusDescription),
        fieldWithPath("resources[].approvals[].lastUpdatedAt").type(STRING).description(approvalsLastUpdatedAtDescription),
        fieldWithPath("resources[].approvals[].expiresAt").type(STRING).description(approvalsExpiresAtDescription),
        fieldWithPath("resources[].active").type(BOOLEAN).description(userActiveDescription),
        fieldWithPath("resources[].lastLogonTime").optional(null).type(NUMBER).description(userLastLogonTimeDescription),
        fieldWithPath("resources[].previousLogonTime").optional(null).type(NUMBER).description(userPreviousLogonTimeDescription),
        fieldWithPath("resources[].verified").type(BOOLEAN).description(userVerifiedDescription),
        fieldWithPath("resources[].origin").type(STRING).description(userOriginDescription),
        fieldWithPath("resources[].zoneId").type(STRING).description(userZoneIdDescription),
        fieldWithPath("resources[].passwordLastModified").type(STRING).description(passwordLastModifiedDescription),
        fieldWithPath("resources[].externalId").type(STRING).description(externalIdDescription),
        fieldWithPath("resources[].meta").type(OBJECT).description(metaDesc),
        fieldWithPath("resources[].meta.version").type(NUMBER).description(metaVersionDesc),
        fieldWithPath("resources[].meta.lastModified").type(STRING).description(metaLastModifiedDesc),
        fieldWithPath("resources[].meta.created").type(STRING).description(metaCreatedDesc)
    };

    private Snippet createFields = requestFields(
        fieldWithPath("userName").required().type(STRING).description(usernameDescription),
        fieldWithPath("password").optional(null).type(STRING).description(passwordDescription),
        fieldWithPath("name").required().type(OBJECT).description(nameObjectDescription),
        fieldWithPath("name.formatted").ignored().type(STRING).description("First and last name combined"),
        fieldWithPath("name.familyName").optional(null).type(STRING).description(lastnameDescription),
        fieldWithPath("name.givenName").optional(null).type(STRING).description(firstnameDescription),
        fieldWithPath("phoneNumbers").optional(null).type(ARRAY).description(phoneNumbersListDescription),
        fieldWithPath("phoneNumbers[].value").optional(null).type(STRING).description(phoneNumbersDescription),
        fieldWithPath("emails").required().type(ARRAY).description(emailListDescription),
        fieldWithPath("emails[].value").required().type(STRING).description(emailDescription),
        fieldWithPath("emails[].primary").required().type(BOOLEAN).description(emailPrimaryDescription),
        fieldWithPath("active").optional(true).type(BOOLEAN).description(userActiveDescription),
        fieldWithPath("verified").optional(false).type(BOOLEAN).description(userVerifiedDescription),
        fieldWithPath("origin").optional(OriginKeys.UAA).type(STRING).description(userOriginDescription),
        fieldWithPath("externalId").optional(null).type(STRING).description(externalIdDescription),
        fieldWithPath("schemas").optional().ignored().type(ARRAY).description(schemasDescription),
        fieldWithPath("meta.*").optional().ignored().type(OBJECT).description("SCIM object meta data not read.")
    );

    private FieldDescriptor[] createResponse = {
        fieldWithPath("schemas").type(ARRAY).description(schemasDescription),
        fieldWithPath("id").type(STRING).description(userIdDescription),
        fieldWithPath("userName").type(STRING).description(usernameDescription),
        fieldWithPath("name").type(OBJECT).description(nameObjectDescription),
        fieldWithPath("name.familyName").type(STRING).description(lastnameDescription),
        fieldWithPath("name.givenName").type(STRING).description(firstnameDescription),
        fieldWithPath("phoneNumbers").type(ARRAY).description(phoneNumbersListDescription),
        fieldWithPath("phoneNumbers[].value").type(STRING).description(phoneNumbersDescription),
        fieldWithPath("emails").type(ARRAY).description(emailListDescription),
        fieldWithPath("emails[].value").type(STRING).description(emailDescription),
        fieldWithPath("emails[].primary").type(BOOLEAN).description(emailPrimaryDescription),
        fieldWithPath("groups").type(ARRAY).description(groupDescription),
        fieldWithPath("groups[].value").type(STRING).description(groupIdDescription),
        fieldWithPath("groups[].display").type(STRING).description(groupDisplayNameDescription),
        fieldWithPath("groups[].type").type(STRING).description(membershipTypeDescription),
        fieldWithPath("approvals").type(ARRAY).description(approvalsListDescription),
        fieldWithPath("active").type(BOOLEAN).description(userActiveDescription),
        fieldWithPath("verified").type(BOOLEAN).description(userVerifiedDescription),
        fieldWithPath("origin").type(STRING).description(userOriginDescription),
        fieldWithPath("zoneId").type(STRING).description(userZoneIdDescription),
        fieldWithPath("passwordLastModified").type(STRING).description(passwordLastModifiedDescription),
        fieldWithPath("externalId").type(STRING).description(externalIdDescription),
        fieldWithPath("meta").type(OBJECT).description(metaDesc),
        fieldWithPath("meta.version").type(NUMBER).description(metaVersionDesc),
        fieldWithPath("meta.lastModified").type(STRING).description(metaLastModifiedDesc),
        fieldWithPath("meta.created").type(STRING).description(metaCreatedDesc)
    };

    private Snippet updateFields = requestFields(
        fieldWithPath("schemas").ignored().type(ARRAY).description(schemasDescription),
        fieldWithPath("id").ignored().type(STRING).description(userIdDescription),
        fieldWithPath("userName").required().type(STRING).description(usernameDescription),
        fieldWithPath("name").required().type(OBJECT).description(nameObjectDescription),
        fieldWithPath("name.familyName").required().type(STRING).description(lastnameDescription),
        fieldWithPath("name.givenName").required().type(STRING).description(firstnameDescription),
        fieldWithPath("phoneNumbers").optional(null).type(ARRAY).description(phoneNumbersListDescription),
        fieldWithPath("phoneNumbers[].value").optional(null).type(STRING).description(phoneNumbersDescription),
        fieldWithPath("emails").required().type(ARRAY).description(emailListDescription),
        fieldWithPath("emails[].value").required().type(STRING).description(emailDescription),
        fieldWithPath("emails[].primary").required().type(BOOLEAN).description(emailPrimaryDescription),
        fieldWithPath("groups").ignored().type(ARRAY).description("Groups are not created at this time."),
        fieldWithPath("approvals").ignored().type(ARRAY).description("Approvals are not created at this time"),
        fieldWithPath("active").optional(true).type(BOOLEAN).description(userActiveDescription),
        fieldWithPath("verified").optional(false).type(BOOLEAN).description(userVerifiedDescription),
        fieldWithPath("origin").optional(OriginKeys.UAA).type(STRING).description(userOriginDescription),
        fieldWithPath("zoneId").ignored().type(STRING).description(userZoneIdDescription),
        fieldWithPath("passwordLastModified").ignored().type(STRING).description(passwordLastModifiedDescription),
        fieldWithPath("externalId").optional(null).type(STRING).description(externalIdDescription),
        fieldWithPath("meta.*").ignored().type(OBJECT).description("SCIM object meta data not read.")
    );

    private FieldDescriptor[] updateResponse = {
        fieldWithPath("schemas").type(ARRAY).description(schemasDescription),
        fieldWithPath("id").type(STRING).description(userIdDescription),
        fieldWithPath("userName").type(STRING).description(usernameDescription),
        fieldWithPath("name").type(OBJECT).description(nameObjectDescription),
        fieldWithPath("name.familyName").type(STRING).description(lastnameDescription),
        fieldWithPath("name.givenName").type(STRING).description(firstnameDescription),
        fieldWithPath("phoneNumbers").type(ARRAY).description(phoneNumbersListDescription),
        fieldWithPath("phoneNumbers[].value").type(STRING).description(phoneNumbersDescription),
        fieldWithPath("emails").type(ARRAY).description(emailListDescription),
        fieldWithPath("emails[].value").type(STRING).description(emailDescription),
        fieldWithPath("emails[].primary").type(BOOLEAN).description(emailPrimaryDescription),
        fieldWithPath("groups").type(ARRAY).description(groupDescription),
        fieldWithPath("groups[].value").type(STRING).description(groupIdDescription),
        fieldWithPath("groups[].display").type(STRING).description(groupDisplayNameDescription),
        fieldWithPath("groups[].type").type(STRING).description(membershipTypeDescription),
        fieldWithPath("approvals").type(ARRAY).description(approvalsListDescription),
        fieldWithPath("approvals[].userId").type(STRING).description(approvalUserIdDescription),
        fieldWithPath("approvals[].clientId").type(STRING).description(approvalClientIdDescription),
        fieldWithPath("approvals[].scope").type(STRING).description(approvalScopeDescription),
        fieldWithPath("approvals[].status").type(STRING).description(approvalStatusDescription),
        fieldWithPath("approvals[].lastUpdatedAt").type(STRING).description(approvalsLastUpdatedAtDescription),
        fieldWithPath("approvals[].expiresAt").type(STRING).description(approvalsExpiresAtDescription),
        fieldWithPath("active").type(BOOLEAN).description(userActiveDescription),
        fieldWithPath("verified").type(BOOLEAN).description(userVerifiedDescription),
        fieldWithPath("origin").type(STRING).description(userOriginDescription),
        fieldWithPath("zoneId").type(STRING).description(userZoneIdDescription),
        fieldWithPath("passwordLastModified").type(STRING).description(passwordLastModifiedDescription),
        fieldWithPath("lastLogonTime").optional(null).type(NUMBER).description(userLastLogonTimeDescription),
        fieldWithPath("previousLogonTime").optional(null).type(NUMBER).description(userLastLogonTimeDescription),
        fieldWithPath("externalId").type(STRING).description(externalIdDescription),
        fieldWithPath("meta").type(OBJECT).description(metaDesc),
        fieldWithPath("meta.version").type(NUMBER).description(metaVersionDesc),
        fieldWithPath("meta.lastModified").type(STRING).description(metaLastModifiedDesc),
        fieldWithPath("meta.created").type(STRING).description(metaCreatedDesc)
    };

    private Snippet patchFields = requestFields(
        fieldWithPath("schemas").ignored().type(ARRAY).description(schemasDescription),
        fieldWithPath("id").ignored().type(STRING).description(userIdDescription),
        fieldWithPath("userName").required().type(STRING).description(usernameDescription),
        fieldWithPath("name").required().type(OBJECT).description(nameObjectDescription),
        fieldWithPath("name.familyName").required().type(STRING).description(lastnameDescription),
        fieldWithPath("name.givenName").required().type(STRING).description(firstnameDescription),
        fieldWithPath("phoneNumbers").optional(null).type(ARRAY).description(phoneNumbersListDescription),
        fieldWithPath("phoneNumbers[].value").optional(null).type(STRING).description(phoneNumbersDescription),
        fieldWithPath("emails").required().type(ARRAY).description(emailListDescription),
        fieldWithPath("emails[].value").required().type(STRING).description(emailDescription),
        fieldWithPath("emails[].primary").required().type(BOOLEAN).description(emailPrimaryDescription),
        fieldWithPath("groups").ignored().type(ARRAY).description("Groups are not created at this time."),
        fieldWithPath("approvals").ignored().type(ARRAY).description("Approvals are not created at this time"),
        fieldWithPath("active").optional(true).type(BOOLEAN).description(userActiveDescription),
        fieldWithPath("verified").optional(false).type(BOOLEAN).description(userVerifiedDescription),
        fieldWithPath("origin").optional(OriginKeys.UAA).type(STRING).description(userOriginDescription),
        fieldWithPath("zoneId").ignored().type(STRING).description(userZoneIdDescription),
        fieldWithPath("passwordLastModified").ignored().type(STRING).description(passwordLastModifiedDescription),
        fieldWithPath("externalId").optional(null).type(STRING).description(externalIdDescription),
        fieldWithPath("meta.*").ignored().type(OBJECT).description("SCIM object meta data not read."),
        fieldWithPath("meta.attributes").optional(null).type(ARRAY).description(metaAttributesDesc)
    );

    private final String scimFilterDescription = "SCIM filter for searching";
    private final String scimAttributeDescription = "Comma separated list of attribute names to be returned.";
    private final String sortByDescription = "Sorting field name, like email or id";
    private final String sortOrderDescription = "Sort order, ascending/descending";
    private final String countDescription = "Max number of results to be returned";

    private ParameterDescriptor[] searchUsersParameters = {
        parameterWithName("filter").optional(null).description(scimFilterDescription).attributes(key("type").value(STRING)),
        parameterWithName("sortBy").optional("created").description(sortByDescription).attributes(key("type").value(STRING)),
        parameterWithName("sortOrder").optional("ascending").description(sortOrderDescription).attributes(key("type").value(STRING)),
        parameterWithName("startIndex").optional("1").description(startIndexDescription).attributes(key("type").value(NUMBER)),
        parameterWithName("count").optional("100").description(countDescription).attributes(key("type").value(NUMBER))
    };

    private ParameterDescriptor[] searchWithAttributes = ArrayUtils.addAll(
        searchUsersParameters,
        new ParameterDescriptor[] {parameterWithName("attributes").optional(null).description(scimAttributeDescription).attributes(key("type").value(STRING))}
    );

    private FieldDescriptor[] searchWithAttributesResponseFields = {
        fieldWithPath("startIndex").type(NUMBER).description(startIndexDescription),
        fieldWithPath("itemsPerPage").type(NUMBER).description(countAndItemsPerPageDescription),
        fieldWithPath("totalResults").type(NUMBER).description(totalResultsDescription),
        fieldWithPath("schemas").type(ARRAY).description(schemasDescription),
        fieldWithPath("resources").type(ARRAY).description(resourceDescription),
        fieldWithPath("resources[].id").type(STRING).description(userIdDescription),
        fieldWithPath("resources[].userName").type(STRING).description(usernameDescription),
        fieldWithPath("resources[].emails").type(ARRAY).description(emailListDescription),
        fieldWithPath("resources[].emails[].value").type(STRING).description(emailDescription),
        fieldWithPath("resources[].emails[].primary").type(BOOLEAN).description(emailPrimaryDescription),
        fieldWithPath("resources[].active").type(BOOLEAN).description(userActiveDescription),
    };

    private static final HeaderDescriptor IDENTITY_ZONE_ID_HEADER = headerWithName(IdentityZoneSwitchingFilter.HEADER).description("May include this header to administer another zone if using `zones.<zoneId>.admin` or `uaa.admin` scope against the default UAA zone.").optional();
    private static final HeaderDescriptor IDENTITY_ZONE_SUBDOMAIN_HEADER = headerWithName(IdentityZoneSwitchingFilter.SUBDOMAIN_HEADER).optional().description("If using a `zones.<zoneId>.admin` scope/token, indicates what Identity Zone this request goes to by supplying a subdomain.");

    private String scimReadToken;
    private String scimWriteToken;
    private ScimUser user;
    private ScimUserProvisioning userProvisioning;

    @BeforeEach
    void setUp() throws Exception {
        userProvisioning = webApplicationContext.getBean(ScimUserProvisioning.class);

        scimReadToken = MockMvcUtils.getClientCredentialsOAuthAccessToken(
            mockMvc,
            "admin",
            "adminsecret",
            "scim.read",
            null,
            true
        );
        scimWriteToken = MockMvcUtils.getClientCredentialsOAuthAccessToken(
            mockMvc,
            "admin",
            "adminsecret",
            "scim.write",
            null,
            true
        );

        user = createScimUserObject();
        user = MockMvcUtils.createUser(mockMvc, scimWriteToken, user);
        ApprovalStore approvalStore = webApplicationContext.getBean(ApprovalStore.class);
        approvalStore.addApproval(
            new Approval()
                .setClientId("client id")
                .setUserId(user.getId())
                .setExpiresAt(new Date(System.currentTimeMillis() + 10000))
                .setScope("scim.read")
                .setStatus(Approval.ApprovalStatus.APPROVED), IdentityZoneHolder.get().getId()
        );
    }

    ScimUser createScimUserObject() {
        String username = new RandomValueStringGenerator().generate() + "@test.org";
        ScimUser user = new ScimUser(null, username, "given name", "family name");
        user.setPrimaryEmail(username);
        user.setPassword("secret");
        user.setExternalId("test-user");
        user.addPhoneNumber("5555555555");
        return user;
    }

    @Test
    void test_Find_Users() throws Exception {
        Snippet responseFields = responseFields(searchResponseFields);
        Snippet requestParameters = requestParameters(searchUsersParameters);

        webApplicationContext.getBean(UaaUserDatabase.class).updateLastLogonTime(user.getId());
        webApplicationContext.getBean(UaaUserDatabase.class).updateLastLogonTime(user.getId());

        mockMvc.perform(
            get("/Users")
                .accept(APPLICATION_JSON)
                .header("Authorization", "Bearer " + scimReadToken)
                .param("filter", String.format("id eq \"%s\" or email eq \"%s\"", user.getId(), user.getUserName()))
                .param("sortBy", "email")
                .param("count", "50")
                .param("sortOrder", "ascending")
                .param("startIndex", "1")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resources[0].previousLogonTime").exists())
            .andExpect(jsonPath("$.resources[0].lastLogonTime").exists())
            .andDo(
                document("{ClassName}/{methodName}",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(
                        headerWithName("Authorization").description("Access token with `scim.read` or `uaa.admin` required"),
                        IDENTITY_ZONE_ID_HEADER,
                        IDENTITY_ZONE_SUBDOMAIN_HEADER
                    ),
                    requestParameters,
                    responseFields
                )
            );
    }

    @Test
    void test_Find_With_Attributes_Users() throws Exception {
        Snippet responseFields = responseFields(searchWithAttributesResponseFields);
        Snippet requestParameters = requestParameters(searchWithAttributes);

        mockMvc.perform(
            get("/Users")
                .accept(APPLICATION_JSON)
                .header("Authorization", "Bearer " + scimReadToken)
                .param("attributes", "id,userName,emails,active")
                .param("filter", String.format("id eq \"%s\"", user.getId()))
                .param("sortBy", "email")
                .param("count", "50")
                .param("sortOrder", "ascending")
                .param("startIndex", "1")
        )
            .andExpect(status().isOk())
            .andDo(print())
            .andDo(
                document("{ClassName}/{methodName}",
                         preprocessRequest(prettyPrint()),
                         preprocessResponse(prettyPrint()),
                         requestHeaders(
                             headerWithName("Authorization").description("Access token with `scim.read` or `uaa.admin` required"),
                             IDENTITY_ZONE_ID_HEADER,
                             IDENTITY_ZONE_SUBDOMAIN_HEADER
                         ),
                         requestParameters,
                         responseFields
                )
            );
    }

    @Test
    void test_Create_User() throws Exception {

        user = createScimUserObject();

        mockMvc.perform(
            RestDocumentationRequestBuilders.post("/Users")
                .accept(APPLICATION_JSON)
                .header("Authorization", "Bearer " + scimWriteToken)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .content(JsonUtils.writeValueAsString(user))
        )
            .andExpect(status().isCreated())
            .andDo(
                document("{ClassName}/{methodName}",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(
                        headerWithName("Authorization").description("Access token with `scim.write` or `uaa.admin` scope required"),
                        IDENTITY_ZONE_ID_HEADER,
                        IDENTITY_ZONE_SUBDOMAIN_HEADER
                    ),
                    createFields,
                    responseFields(createResponse)
                )
            );
    }

    @Test
    void test_status_unlock_user() throws Exception {
        UserAccountStatus alteredAccountStatus = new UserAccountStatus();
        alteredAccountStatus.setLocked(false);
        String jsonStatus = JsonUtils.writeValueAsString(alteredAccountStatus);

        mockMvc
            .perform(
                RestDocumentationRequestBuilders.patch("/Users/{userId}/status", user.getId())
                    .header("Authorization", "Bearer " + scimWriteToken)
                    .accept(APPLICATION_JSON)
                    .contentType(APPLICATION_JSON)
                    .content(jsonStatus)
            )
            .andExpect(status().isOk())
            .andExpect(content().json(jsonStatus))
            .andDo(
                document("{ClassName}/{methodName}",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(parameterWithName("userId").description(userIdDescription)),
                    requestHeaders(
                        headerWithName("Authorization").description("Access token with `scim.write`, `uaa.account_status.write`, or `uaa.admin` required"),
                        IDENTITY_ZONE_ID_HEADER,
                        IDENTITY_ZONE_SUBDOMAIN_HEADER
                    ),
                    requestFields(fieldWithPath("locked").optional(null).description("Set to `false` in order to unlock the user when they have been locked out according to the password lock-out policy. Setting to `true` will produce an error, as the user cannot be locked out via the API.").type(BOOLEAN)),
                    responseFields(fieldWithPath("locked").description("The `locked` value given in the request.").type(BOOLEAN))
                )
            );
    }

    @Test
    void test_status_password_expire_user() throws Exception {
        UserAccountStatus alteredAccountStatus = new UserAccountStatus();
        alteredAccountStatus.setPasswordChangeRequired(true);
        String jsonStatus = JsonUtils.writeValueAsString(alteredAccountStatus);

        mockMvc
            .perform(
                RestDocumentationRequestBuilders.patch("/Users/{userId}/status", user.getId())
                    .header("Authorization", "Bearer " + scimWriteToken)
                    .accept(APPLICATION_JSON)
                    .contentType(APPLICATION_JSON)
                    .content(jsonStatus)
            )
            .andExpect(status().isOk())
            .andExpect(content().json(jsonStatus))
            .andDo(
                document("{ClassName}/{methodName}",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(parameterWithName("userId").description(userIdDescription)),
                    requestHeaders(
                        headerWithName("Authorization").description("Access token with `scim.write`, `uaa.account_status.write`, or `uaa.admin` required"),
                        IDENTITY_ZONE_ID_HEADER,
                        IDENTITY_ZONE_SUBDOMAIN_HEADER
                    ),
                    requestFields(fieldWithPath("passwordChangeRequired").optional(null).description("Set to `true` in order to force internal user’s password to expire").type(BOOLEAN)),
                    responseFields(fieldWithPath("passwordChangeRequired").description("The `passwordChangeRequired` value given in the request.").type(BOOLEAN))
                )
            );
    }

    @Test
    void test_Update_User() throws Exception {
        ApprovalStore store = webApplicationContext.getBean(ApprovalStore.class);
        Approval approval = new Approval()
            .setUserId(user.getId())
            .setStatus(Approval.ApprovalStatus.DENIED)
            .setScope("uaa.user")
            .setClientId("identity")
            .setExpiresAt(new Date(System.currentTimeMillis() + 30000))
            .setLastUpdatedAt(new Date(System.currentTimeMillis() + 30000));
        store.addApproval(approval, IdentityZoneHolder.get().getId());
        user.setGroups(Collections.emptyList());

        mockMvc.perform(
            RestDocumentationRequestBuilders.put("/Users/{userId}", user.getId())
                .accept(APPLICATION_JSON)
                .header("Authorization", "Bearer " + scimWriteToken)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("If-Match", user.getVersion())
                .content(JsonUtils.writeValueAsString(user))
        )
            .andExpect(status().isOk())
            .andDo(
                document("{ClassName}/{methodName}",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(parameterWithName("userId").description(userIdDescription)),
                    requestHeaders(
                        headerWithName("Authorization").description(scimWriteOrUaaAdminRequired),
                        headerWithName("If-Match").description("The version of the SCIM object to be updated. Wildcard (*) accepted."),
                        IDENTITY_ZONE_ID_HEADER,
                        IDENTITY_ZONE_SUBDOMAIN_HEADER
                    ),
                    updateFields,
                    responseFields(updateResponse)
                )
            );
    }

    @Test
    void test_Patch_User() throws Exception {
        ApprovalStore store = webApplicationContext.getBean(ApprovalStore.class);
        Approval approval = new Approval()
            .setUserId(user.getId())
            .setStatus(Approval.ApprovalStatus.DENIED)
            .setScope("uaa.user")
            .setClientId("identity")
            .setExpiresAt(new Date(System.currentTimeMillis() + 30000))
            .setLastUpdatedAt(new Date(System.currentTimeMillis() + 30000));
        store.addApproval(approval, IdentityZoneHolder.get().getId());
        user.setGroups(Collections.emptyList());

        mockMvc.perform(
            RestDocumentationRequestBuilders.patch("/Users/{userId}", user.getId())
                .accept(APPLICATION_JSON)
                .header("Authorization", "Bearer " + scimWriteToken)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("If-Match", user.getVersion())
                .content(JsonUtils.writeValueAsString(user))
        )
            .andExpect(status().isOk())
            .andDo(
                document("{ClassName}/{methodName}",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(parameterWithName("userId").description(userIdDescription)),
                    requestHeaders(
                        headerWithName("Authorization").description(scimWriteOrUaaAdminRequired),
                        headerWithName("If-Match").description("The version of the SCIM object to be updated. Wildcard (*) accepted.")
                    ),
                    patchFields,
                    responseFields(updateResponse)
                )
            );
    }

    @Test
    void test_Delete_User() throws Exception {
        ApprovalStore store = webApplicationContext.getBean(ApprovalStore.class);
        Approval approval = new Approval()
            .setUserId(user.getId())
            .setStatus(Approval.ApprovalStatus.APPROVED)
            .setScope("uaa.user")
            .setClientId("identity")
            .setExpiresAt(new Date(System.currentTimeMillis() + 30000))
            .setLastUpdatedAt(new Date(System.currentTimeMillis() + 30000));
        store.addApproval(approval, IdentityZoneHolder.get().getId());

        mockMvc.perform(
            RestDocumentationRequestBuilders.delete("/Users/{userId}", user.getId())
                .accept(APPLICATION_JSON)
                .header("Authorization", "Bearer " + scimWriteToken)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("If-Match", user.getVersion())
        )
            .andExpect(status().isOk())
            .andDo(
                document("{ClassName}/{methodName}",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(parameterWithName("userId").description(userIdDescription)),
                    requestHeaders(
                        headerWithName("Authorization").description(scimWriteOrUaaAdminRequired),
                        headerWithName("If-Match").optional().description("The version of the SCIM object to be deleted. Optional."),
                        IDENTITY_ZONE_ID_HEADER,
                        IDENTITY_ZONE_SUBDOMAIN_HEADER
                    ),

                    responseFields(updateResponse)
                )
            );
    }

    @Test
    void test_Get_User() throws Exception {
        ApprovalStore store = webApplicationContext.getBean(ApprovalStore.class);
        Approval approval = new Approval()
            .setUserId(user.getId())
            .setStatus(Approval.ApprovalStatus.APPROVED)
            .setScope("uaa.user")
            .setClientId("identity")
            .setExpiresAt(new Date(System.currentTimeMillis() + 30000))
            .setLastUpdatedAt(new Date(System.currentTimeMillis() + 30000));
        store.addApproval(approval, IdentityZoneHolder.get().getId());

        webApplicationContext.getBean(UaaUserDatabase.class).updateLastLogonTime(user.getId());
        webApplicationContext.getBean(UaaUserDatabase.class).updateLastLogonTime(user.getId());

        mockMvc.perform(
            RestDocumentationRequestBuilders.get("/Users/{userId}", user.getId())
                .accept(APPLICATION_JSON)
                .header("Authorization", "Bearer " + scimReadToken)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("If-Match", user.getVersion())
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.previousLogonTime").exists())
            .andExpect(jsonPath("$.lastLogonTime").exists())
            .andDo(
                document("{ClassName}/{methodName}",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(parameterWithName("userId").description(userIdDescription)),
                    requestHeaders(
                        headerWithName("Authorization").description("Access token with scope `scim.read`, `uaa.admin`, or `zones.uaa.admin` required"),
                        headerWithName("If-Match").optional().description("The version of the SCIM object to be deleted. Optional."),
                        IDENTITY_ZONE_ID_HEADER,
                        IDENTITY_ZONE_SUBDOMAIN_HEADER
                    ),

                    responseFields(updateResponse)
                )
            );
    }


    @Test
    void test_Change_Password() throws Exception {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setOldPassword("secret");
        request.setPassword("newsecret");

        String myToken = MockMvcUtils.getUserOAuthAccessToken(mockMvc, "app", "appclientsecret", user.getUserName(), "secret", null, null, true);

        mockMvc.perform(
            RestDocumentationRequestBuilders.put("/Users/{userId}/password", user.getId())
                .accept(APPLICATION_JSON)
                .header("Authorization", "Bearer " + myToken)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .content(JsonUtils.writeValueAsString(request))
        )
            .andExpect(status().isOk())
            .andDo(
                document("{ClassName}/{methodName}",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(parameterWithName("userId").description(userIdDescription)),
                    requestHeaders(
                        headerWithName("Authorization").description("Access token with `password.write` or `uaa.admin` required"),
                        IDENTITY_ZONE_ID_HEADER,
                        IDENTITY_ZONE_SUBDOMAIN_HEADER
                    ),
                    requestFields(
                        fieldWithPath("oldPassword").required().description("Old password. Optional when resetting another users password as an admin with `uaa.admin` scope").type(STRING),
                        fieldWithPath("password").required().description("New password.").type(STRING)
                    ),
                    responseFields(
                        fieldWithPath("status").description("Will be 'ok' if password changed successfully."),
                        fieldWithPath("message").description("Will be 'password updated' if password changed successfully.")
                    )
                )
            );
    }

    @Test
    void getUserVerificationLink() throws Exception {
        String accessToken = testClient.getClientCredentialsOAuthAccessToken("admin", "adminsecret", "uaa.admin");

        String email = "joel" + new RandomValueStringGenerator().generate() + "@example.com";
        ScimUser joel = new ScimUser(null, email, "Joel", "D'sa");
        joel.setVerified(false);
        joel.addEmail(email);
        joel = userProvisioning.createUser(joel, "pas5Word", IdentityZoneHolder.get().getId());

        MockHttpServletRequestBuilder get = RestDocumentationRequestBuilders.get("/Users/{userId}/verify-link", joel.getId())
            .header("Authorization", "Bearer " + accessToken)
            .param("redirect_uri", "http://redirect.to/app")
            .accept(APPLICATION_JSON);

        Snippet requestHeaders = requestHeaders(headerWithName("Authorization").description("The bearer token, with a pre-amble of `Bearer`"), IDENTITY_ZONE_ID_HEADER, IDENTITY_ZONE_SUBDOMAIN_HEADER);
        Snippet requestParameters = requestParameters(parameterWithName("redirect_uri").required().description("Location where the user will be redirected after verifying by clicking the verification link").attributes(key("type").value(STRING)));
        Snippet responseFields = responseFields(fieldWithPath("verify_link").description("Location the user must visit and authenticate to verify"));

        Snippet pathParameters = pathParameters(
            RequestDocumentation.parameterWithName("userId").description("The ID of the user to verify")
        );
        mockMvc.perform(get)
            .andDo(print())
            .andExpect(status().isOk())
            .andDo(document("{ClassName}/{methodName}", preprocessResponse(prettyPrint()),
                pathParameters, requestHeaders, requestParameters, responseFields))
        ;
    }

    @Test
    void directlyVerifyUser() throws Exception {
        String accessToken = testClient.getClientCredentialsOAuthAccessToken("admin", "adminsecret", "uaa.admin");

        String email = "billy_o@example.com";
        ScimUser billy = new ScimUser(null, email, "William", "d'Orange");
        billy.setVerified(false);
        billy.addEmail(email);
        billy.setVersion(12);
        billy = userProvisioning.createUser(billy, "pas5Word", IdentityZoneHolder.get().getId());

        Snippet requestHeaders = requestHeaders(headerWithName("Authorization").description("The bearer token, with a pre-amble of `Bearer`"),
            headerWithName("If-Match").description("(Optional) The expected current version of the user, which will prevent update if the version does not match"),
            IDENTITY_ZONE_ID_HEADER,
            IDENTITY_ZONE_SUBDOMAIN_HEADER);

        Snippet pathParameters = pathParameters(
            RequestDocumentation.parameterWithName("userId").description("The ID of the user to verify")
        );

        MockHttpServletRequestBuilder get = RestDocumentationRequestBuilders.get("/Users/{userId}/verify", billy.getId())
            .header("Authorization", "Bearer " + accessToken)
            .header("If-Match", "12")
            .accept(APPLICATION_JSON);

        mockMvc.perform(get)
            .andExpect(status().isOk())
            .andDo(document("{ClassName}/{methodName}", preprocessResponse(prettyPrint()),
                pathParameters, requestHeaders))
        ;
    }

    @Test
    void deleteMfaRegistration() throws Exception {
        String accessToken = testClient.getClientCredentialsOAuthAccessToken("admin", "adminsecret", "uaa.admin");

        String email = "tom.mugwort@example.com";
        ScimUser tommy = new ScimUser(null, email, "Tom", "Mugwort");
        tommy.setVerified(false);
        tommy.addEmail(email);
        tommy = userProvisioning.createUser(tommy, "pas5Word", IdentityZoneHolder.get().getId());

        Snippet requestHeaders = requestHeaders(headerWithName("Authorization").description("Access token with `zones.<zoneId>.admin` or `uaa.admin` required."),
            IDENTITY_ZONE_ID_HEADER,
            IDENTITY_ZONE_SUBDOMAIN_HEADER);

        Snippet pathParameters = pathParameters(
            RequestDocumentation.parameterWithName("userId").description("Unique user identifier.")
        );

        MockHttpServletRequestBuilder delete = RestDocumentationRequestBuilders.delete("/Users/{userId}/mfa", tommy.getId())
            .header("Authorization", "Bearer " + accessToken);

        mockMvc.perform(delete)
            .andExpect(status().isOk())
            .andDo(document("{ClassName}/{methodName}", preprocessResponse(prettyPrint()),
                pathParameters, requestHeaders))
        ;
    }
}
