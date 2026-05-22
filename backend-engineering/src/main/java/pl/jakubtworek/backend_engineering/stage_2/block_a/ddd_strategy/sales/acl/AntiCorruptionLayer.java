package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.sales.acl;

// Anti-Corruption Layer interface.
// It protects the internal domain model from external or legacy representations.
public interface AntiCorruptionLayer<ExternalModel, InternalModel> {

    InternalModel translate(ExternalModel externalModel);
}