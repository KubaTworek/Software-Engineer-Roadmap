package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.sales.acl;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.legacy.dto.LegacyOrderDto;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.sales.application.command.PlaceOrderCommand;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.sales.application.command.PlaceOrderItem;

// Example ACL translating a legacy order representation into a Sales context command.
// The Sales model does not depend directly on the legacy system.
public final class LegacyOrderAcl
        implements AntiCorruptionLayer<LegacyOrderDto, PlaceOrderCommand> {

    @Override
    public PlaceOrderCommand translate(LegacyOrderDto legacyOrder) {
        return new PlaceOrderCommand(
                legacyOrder.legacyCustomerId(),
                legacyOrder.lines().stream()
                        .map(line -> new PlaceOrderItem(
                                line.sku(),
                                line.qty()
                        ))
                        .toList()
        );
    }
}