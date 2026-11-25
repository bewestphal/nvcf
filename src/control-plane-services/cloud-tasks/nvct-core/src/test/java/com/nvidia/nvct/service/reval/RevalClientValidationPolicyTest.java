/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nvidia.nvct.service.reval;

import static org.assertj.core.api.Assertions.assertThat;

import com.nvidia.nvct.rest.task.dto.GpuSpecificationDto;
import com.nvidia.nvct.rest.task.dto.HelmValidationPolicyDto;
import com.nvidia.nvct.rest.task.dto.ValidationPolicyNameEnum;
import com.nvidia.nvct.service.reval.RevalStubService.KubernetesType;
import com.nvidia.nvct.service.reval.RevalStubService.RevalValidateResponse;
import com.nvidia.nvct.service.reval.RevalStubService.ValidationPolicyResult;
import com.nvidia.nvct.service.reval.RevalStubService.ValidationPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class RevalClientValidationPolicyTest {

    private static final HelmValidationPolicyDto.KubernetesType WIDGET =
            HelmValidationPolicyDto.KubernetesType.builder()
                    .group("example.com")
                    .version("v1")
                    .kind("Widget")
                    .build();

    private static GpuSpecificationDto spec(HelmValidationPolicyDto policy) {
        return GpuSpecificationDto.builder().gpu("L40S").helmValidationPolicy(policy).build();
    }

    private static ValidationPolicy defaultPolicyWithWidget() {
        return ValidationPolicy.builder()
                .id(RevalClient.VALIDATION_POLICY_ID)
                .name("Default")
                .allowedExtraKubernetesTypes(List.of(KubernetesType.builder()
                        .group("example.com")
                        .version("v1")
                        .kind("Widget")
                        .build()))
                .build();
    }

    @Test
    void testNoPolicyKeepsCurrentRequest() {
        assertThat(RevalClient.validationPolicies(spec(null))).isNull();
    }

    @Test
    void testPolicyWithoutExtraTypesKeepsCurrentRequest() {
        for (var name : ValidationPolicyNameEnum.values()) {
            var policy = HelmValidationPolicyDto.builder().name(name).build();

            assertThat(RevalClient.validationPolicies(spec(policy))).isNull();
        }
    }

    @Test
    void testForwardsExtraKubernetesTypes() {
        var policy = HelmValidationPolicyDto.builder()
                .name(ValidationPolicyNameEnum.DEFAULT)
                .extraKubernetesTypes(List.of(WIDGET))
                .build();

        assertThat(RevalClient.validationPolicies(spec(policy))).containsExactly(defaultPolicyWithWidget());
    }

    private static ValidationPolicyResult policyResult(boolean valid, List<String> errors) {
        return ValidationPolicyResult.builder().id("task").valid(valid).validationErrors(errors).build();
    }

    @Test
    void testFailedPolicyRejectsEvenWhenTopLevelIsValid() {
        var body = RevalValidateResponse.builder()
                .valid(true)
                .validationPolicies(List.of(policyResult(false, List.of())))
                .build();

        assertThat(RevalClient.isValid(body)).isFalse();
        assertThat(RevalClient.validationErrors(body))
                .containsExactly("Helm chart validation failure. Unknown error.");
    }

    @Test
    void testPassingPoliciesKeepTopLevelResult() {
        var body = RevalValidateResponse.builder()
                .valid(true)
                .validationPolicies(List.of(policyResult(true, null)))
                .build();

        assertThat(RevalClient.isValid(body)).isTrue();
    }

    @Test
    void testCollectsTopLevelAndFailedPolicyErrors() {
        var body = RevalValidateResponse.builder()
                .valid(false)
                .validationErrors(List.of("top"))
                .validationPolicies(List.of(
                        policyResult(false, List.of("policy")),
                        policyResult(true, List.of("ignored"))))
                .build();

        assertThat(RevalClient.isValid(body)).isFalse();
        assertThat(RevalClient.validationErrors(body)).containsExactly("top", "policy");
    }

    @Test
    void testUnrestrictedKeepsDefaultRulesAtSubmit() {
        var policy = HelmValidationPolicyDto.builder()
                .name(ValidationPolicyNameEnum.UNRESTRICTED)
                .extraKubernetesTypes(List.of(WIDGET))
                .build();

        assertThat(RevalClient.validationPolicies(spec(policy))).containsExactly(defaultPolicyWithWidget());
    }
}
