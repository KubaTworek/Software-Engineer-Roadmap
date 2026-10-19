package pl.jakubtworek.backend_engineering.stage_1.block_c.test.advanced.contract;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserDirectoryProviderContractTest {

    @Test
    void providerShouldSatisfyTheContractPublishedByCheckout() {
        UserDirectoryContractFixture contract = UserDirectoryContractFixture.load();
        ContractResponse actual = new UserDirectoryProvider().handle(
                contract.request().method(), contract.request().path());

        assertThat(actual.status()).isEqualTo(contract.response().status());
        assertThat(actual.body()).containsAllEntriesOf(contract.response().body());
    }

    @Test
    void providerMayReturnAStableFailureOutsideThePublishedInteraction() {
        ContractResponse actual = new UserDirectoryProvider().handle("GET", "/internal/users/99");

        assertThat(actual.status()).isEqualTo(404);
        assertThat(actual.body()).containsEntry("code", "USER_NOT_FOUND");
    }
}
