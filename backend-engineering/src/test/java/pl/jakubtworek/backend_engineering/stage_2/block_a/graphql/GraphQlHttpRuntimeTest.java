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
