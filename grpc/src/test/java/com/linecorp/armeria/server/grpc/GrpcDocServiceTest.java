/*
 * Copyright 2017 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.linecorp.armeria.server.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.ClassRule;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Descriptors.ServiceDescriptor;

import com.linecorp.armeria.common.grpc.GrpcSerializationFormats;
import com.linecorp.armeria.grpc.testing.ReconnectServiceGrpc.ReconnectServiceImplBase;
import com.linecorp.armeria.grpc.testing.TestServiceGrpc.TestServiceImplBase;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.docs.DocService;
import com.linecorp.armeria.server.docs.EndpointInfo;
import com.linecorp.armeria.server.grpc.GrpcDocServicePlugin.ServiceEntry;
import com.linecorp.armeria.server.logging.LoggingService;
import com.linecorp.armeria.testing.server.ServerRule;

public class GrpcDocServiceTest {

    private static final ServiceDescriptor TEST_SERVICE_DESCRIPTOR =
            com.linecorp.armeria.grpc.testing.Test.getDescriptor()
                                                  .findServiceByName("TestService");

    private static final ServiceDescriptor RECONNECT_SERVICE_DESCRIPTOR =
            com.linecorp.armeria.grpc.testing.Test.getDescriptor()
                                                  .findServiceByName("ReconnectService");

    @ClassRule
    public static final ServerRule server = new ServerRule() {
        @Override
        protected void configure(ServerBuilder sb) throws Exception {
            sb.serviceUnder("/test", new GrpcServiceBuilder()
                    .addService(mock(TestServiceImplBase.class))
                    .build());
            sb.serviceUnder("/reconnect", new GrpcServiceBuilder()
                    .addService(mock(ReconnectServiceImplBase.class))
                    .build());
            sb.serviceUnder("/docs/", new DocService().decorate(LoggingService::new));
        }
    };

    @Test
    public void testOk() throws Exception {
        List<ServiceEntry> entries = ImmutableList.of(
                new ServiceEntry(
                        TEST_SERVICE_DESCRIPTOR,
                        ImmutableList.of(new EndpointInfo("*",
                                                          "/test/armeria.grpc.testing.TestService/*",
                                                          "",
                                                          GrpcSerializationFormats.PROTO,
                                                          ImmutableSet.of(GrpcSerializationFormats.PROTO)))),
                new ServiceEntry(
                        RECONNECT_SERVICE_DESCRIPTOR,
                        ImmutableList.of(new EndpointInfo(
                                "*",
                                "/reconnect/armeria.grpc.testing.ReconnectService/*",
                                "",
                                GrpcSerializationFormats.PROTO,
                                ImmutableSet.of(GrpcSerializationFormats.PROTO)))));
        final ObjectMapper mapper = new ObjectMapper();

        final JsonNode expectedJson = mapper.valueToTree(new GrpcDocServicePlugin().generate(entries));

        try (CloseableHttpClient hc = HttpClients.createMinimal()) {
            final HttpGet req = new HttpGet(specificationUri());

            try (CloseableHttpResponse res = hc.execute(req)) {
                assertThat(res.getStatusLine().toString()).isEqualTo("HTTP/1.1 200 OK");
                final JsonNode actualJson = mapper.readTree(EntityUtils.toString(res.getEntity()));

                // The specification generated by ThriftDocServicePlugin does not include the docstrings
                // because it's injected by the DocService, so we remove them here for easier comparison.
                removeDocStrings(actualJson);

                // Convert to the prettified strings for human-readable comparison.
                final ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
                final String actualJsonString = writer.writeValueAsString(actualJson);
                final String expectedJsonString = writer.writeValueAsString(expectedJson);
                assertThat(actualJsonString).isEqualTo(expectedJsonString);
            }
        }
    }

    @Test
    public void testMethodNotAllowed() throws Exception {
        try (CloseableHttpClient hc = HttpClients.createMinimal()) {
            final HttpPost req = new HttpPost(specificationUri());

            try (CloseableHttpResponse res = hc.execute(req)) {
                assertThat(res.getStatusLine().toString()).isEqualTo("HTTP/1.1 405 Method Not Allowed");
            }
        }
    }

    private static void removeDocStrings(JsonNode json) {
        if (json.isObject()) {
            ((ObjectNode) json).remove("docString");
        }

        if (json.isObject() || json.isArray()) {
            json.forEach(GrpcDocServiceTest::removeDocStrings);
        }
    }

    private static String specificationUri() {
        return server.uri("/docs/specification.json");
    }
}
