package pl.jakubtworek.lld.workflow.domain;

import java.util.HashSet;
import java.util.List;

/**
 * Uporządkowany, niemutowalny plan przypięty do dokumentu na całe jego życie.
 * Zmiana konfiguracji poza agregatem nie może przesunąć trwającego przeglądu.
 * Rozszerzenie z jednego do wielu etapów zmienia dane planu, nie klasy stanów.
 */
public record WorkflowDefinition(List<ReviewStage> stages) {

    public WorkflowDefinition {
        stages = List.copyOf(stages);
        if (stages.isEmpty())
            throw new IllegalArgumentException("Workflow wymaga przynajmniej jednego etapu");
        var ids = new HashSet<String>();
        for (ReviewStage stage : stages) if (!ids.add(stage.id()))
            throw new IllegalArgumentException("Powtórzone ID etapu");
    }
}
