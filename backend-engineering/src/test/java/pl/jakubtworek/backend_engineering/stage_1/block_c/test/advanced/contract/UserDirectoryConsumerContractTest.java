package pl.jakubtworek.backend_engineering.stage_1.block_c.test.advanced.contract;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserDirectoryConsumerContractTest {

    @Test
    void consumerShouldDeclareTheSmallestResponseItCanActuallyRead() {
        UserDirectoryContractFixture contract = UserDirectoryContractFixture.load();
        UserDirectoryClient client = new UserDirectoryClient(path -> {
            assertThat(path).isEqualTo(contract.request().path());
            return new ContractResponse(contract.response().status(), contract.response().body());
        });

        assertThat(client.findRequired(42)).isEqualTo(new UserDirectoryClient.UserProfile(
                42, "Ada", true));
        assertThat(contract.consumer()).isEqualTo("checkout-service");
        assertThat(contract.provider()).isEqualTo("user-directory");
    }
}
