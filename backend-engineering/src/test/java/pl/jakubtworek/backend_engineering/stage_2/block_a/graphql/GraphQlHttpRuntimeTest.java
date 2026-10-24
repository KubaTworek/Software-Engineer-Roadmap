package pl.jakubtworek.backend_engineering.stage_2.block_a.graphql;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GraphQlHttpRuntimeTest {

    @Autowired
    private MockMvc mockMvc;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private pl.jakubtworek.backend_engineering.stage_2.block_a.reference_flow.InMemoryProductQueryUseCase query;

    @org.junit.jupiter.api.BeforeEach void clearCalls() { org.mockito.Mockito.clearInvocations(query); }

    @Test void aliasesAndFragmentsCannotBypassBudgetAndRejectedQueryDoesNotReadData() throws Exception {
        String aliases = java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> "p" + i + ": product(id: \\\"p-1\\\") { ...Details }").collect(java.util.stream.Collectors.joining(" "));
        mockMvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"query { " + aliases + " } fragment Details on Product { id name version }\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.errors").isNotEmpty());
        org.mockito.Mockito.verifyNoInteractions(query);
    }

    @Test void coercedVariablesLimitListBeforeLoadersAndCacheLivesOneRequest() throws Exception {
        String ids = java.util.stream.IntStream.range(0, 21).mapToObj(i -> "\"p-1\"")
                .collect(java.util.stream.Collectors.joining(","));
        mockMvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"query($ids:[ID!]!){ products(ids:$ids){id} }\",\"variables\":{\"ids\":[" + ids + "]}}"))
                .andExpect(jsonPath("$.errors").isNotEmpty());
        org.mockito.Mockito.verifyNoInteractions(query);
        String body = "{\"query\":\"{ a: product(id: \\\"p-1\\\") {id} b: product(id: \\\"p-1\\\") {name} }\"}";
        for (int request = 0; request < 2; request++)
            mockMvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(jsonPath("$.errors").doesNotExist());
        org.mockito.Mockito.verify(query, org.mockito.Mockito.times(2)).find("p-1");
    }

    @Test void sensitiveNonNullFieldChecksOwnerAndPropagatesNullToNullableProduct() throws Exception {
        String body = "{\"query\":\"{ product(id: \\\"p-1\\\") { id internalMarginPercent } }\"}";
        mockMvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.errors").isNotEmpty()).andExpect(jsonPath("$.data.product").isEmpty());
        mockMvc.perform(post("/graphql").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("seller-1"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.errors").doesNotExist()).andExpect(jsonPath("$.data.product.internalMarginPercent").value(25));
    }

    @Test
    void realSchemaAndHttpTransportExecuteAgainstSharedUseCase() throws Exception {
        String request = """
                {"query":"query { product(id: \\"p-1\\") { id name version } }"}
                """;

        mockMvc.perform(post("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.product.id").value("p-1"))
                .andExpect(jsonPath("$.data.product.name").value("Java Backend Handbook"))
                .andExpect(jsonPath("$.data.product.version").value(3));
    }

    @Test
    void schemaRejectsUnknownFieldBeforeCallingDomainLogic() throws Exception {
        String request = """
                {"query":"query { product(id: \\"p-1\\") { password } }"}
                """;

        mockMvc.perform(post("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("ValidationError"));
    }
}
