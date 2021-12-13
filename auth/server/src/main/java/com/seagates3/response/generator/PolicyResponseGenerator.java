/*
 * Copyright (c) 2020 Seagate Technology LLC and/or its Affiliates
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * For any questions about this software or licensing,
 * please email opensource@seagate.com or cortx-questions@seagate.com.
 *
 */

package com.seagates3.response.generator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import com.seagates3.authserver.AuthServerConfig;
import com.seagates3.model.Policy;
import com.seagates3.response.ServerResponse;
import com.seagates3.response.formatter.xml.XMLResponseFormatter;

import io.netty.handler.codec.http.HttpResponseStatus;

public class PolicyResponseGenerator extends AbstractResponseGenerator {

    public ServerResponse generateCreateResponse(Policy policy) {
       LinkedHashMap<String, String> responseElements =
           new LinkedHashMap<String, String>();
        responseElements.put("PolicyName", policy.getName());
        responseElements.put("DefaultVersionId", policy.getDefaultVersionid());
        responseElements.put("PolicyId", policy.getPolicyId());
        responseElements.put("Path", policy.getPath());
        responseElements.put("Arn", policy.getARN());
        responseElements.put("AttachmentCount",
                String.valueOf(policy.getAttachmentCount()));
        responseElements.put("CreateDate", policy.getCreateDate());
        responseElements.put("UpdateDate", policy.getUpdateDate());

        return new XMLResponseFormatter().formatCreateResponse(
            "CreatePolicy", "Policy", responseElements,
            AuthServerConfig.getReqId());
    }

   public
    ServerResponse malformedPolicy(String errorMessage) {
      return formatResponse(HttpResponseStatus.BAD_REQUEST, "MalformedPolicy",
                            errorMessage);
    }

   public
    ServerResponse limitExceeded(String errorMessage) {
      return formatResponse(HttpResponseStatus.BAD_REQUEST, "LimitExceeded",
                            errorMessage);
    }

   public
    ServerResponse noSuchPolicy() {
      String errorMessage = "The specified policy does not exist.";
      return formatResponse(HttpResponseStatus.NOT_FOUND, "NoSuchPolicy",
                            errorMessage);
    }

   public
    ServerResponse invalidPolicyDocument() {
      String errorMessage =
          "The content of the form does not meet the conditions specified in " +
          "the " + "policy document.";
      return formatResponse(HttpResponseStatus.BAD_REQUEST,
                            "InvalidPolicyDocument", errorMessage);
    }

   public
    ServerResponse generateDeleteResponse() {
      return new XMLResponseFormatter().formatDeleteResponse("DeletePolicy");
    }

   public
    ServerResponse generateListResponse(List<Policy> policyList) {
      ArrayList<LinkedHashMap<String, String>> policyMembers =
          new ArrayList<>();
      LinkedHashMap responseElements;
      for (Policy policy : policyList) {
        responseElements = new LinkedHashMap();
        responseElements.put("PolicyId", policy.getPolicyId());
        responseElements.put("Path", policy.getPath());
        responseElements.put("PolicyName", policy.getName());
        responseElements.put("Arn", policy.getARN());
        responseElements.put("CreateDate", policy.getCreateDate());
        responseElements.put("DefaultVersionId", policy.getDefaultVersionid());
        responseElements.put("AttachmentCount",
                             ((Integer)policy.getAttachmentCount()).toString());
        responseElements.put(
            "PermissionsBoundaryUsageCount",
            ((Integer)policy.getPermissionsBoundaryUsageCount()).toString());
        responseElements.put("IsAttachable", policy.getIsPolicyAttachable());
        responseElements.put("UpdateDate", policy.getUpdateDate());
        policyMembers.add(responseElements);
      }
      return new XMLResponseFormatter().formatListResponse(
          "ListPolicies", "Policies", policyMembers, false,
          AuthServerConfig.getReqId());
    }

   public
    ServerResponse generateGetResponse(Policy policy) {
      ArrayList<LinkedHashMap<String, String>> policyMembers =
          new ArrayList<>();
      LinkedHashMap responseElements;
      responseElements = new LinkedHashMap();
      responseElements.put("PolicyId", policy.getPolicyId());
      responseElements.put("Path", policy.getPath());
      responseElements.put("PolicyName", policy.getName());
      responseElements.put("Arn", policy.getARN());
      responseElements.put("CreateDate", policy.getCreateDate());
      responseElements.put("DefaultVersionId", policy.getDefaultVersionid());
      responseElements.put("AttachmentCount",
                           ((Integer)policy.getAttachmentCount()).toString());
      responseElements.put(
          "PermissionsBoundaryUsageCount",
          ((Integer)policy.getPermissionsBoundaryUsageCount()).toString());
      responseElements.put("IsAttachable", policy.getIsPolicyAttachable());
      responseElements.put("UpdateDate", policy.getUpdateDate());

      policyMembers.add(responseElements);
      return new XMLResponseFormatter().formatGetResponse(
          "GetPolicy", "Policy", policyMembers, AuthServerConfig.getReqId());
    }

   public
    ServerResponse deletePolicyConflict(String errorMessage) {
      return formatResponse(HttpResponseStatus.BAD_REQUEST, "DeleteConflict",
                            errorMessage);
    }

   public
    ServerResponse generateGetPolicyVersionResponse(Policy policy) {

      ArrayList<LinkedHashMap<String, String>> policyMembers =
          new ArrayList<>();
      LinkedHashMap responseElements;
      responseElements = new LinkedHashMap();
      responseElements.put("Document", policy.getPolicyDoc());
      responseElements.put("VersionId", policy.getDefaultVersionid());
      responseElements.put("IsDefaultVersion", true);
      responseElements.put("CreateDate", policy.getCreateDate());

      policyMembers.add(responseElements);
      return new XMLResponseFormatter().formatGetResponse(
          "PolicyVersion", "PolicyVersion", policyMembers,
          AuthServerConfig.getReqId());
    }
}
