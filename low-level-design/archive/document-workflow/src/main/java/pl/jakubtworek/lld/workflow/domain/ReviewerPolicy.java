package pl.jakubtworek.lld.workflow.domain;

/**
 * Zmienna reguła ponownego użycia osoby między etapami. Zakaz autora i rola
 * pozostają wspólne w agregacie. Implementacja ma być czysta i nie czytać czasu.
 */
@FunctionalInterface
public interface ReviewerPolicy {

    boolean permits(UserId reviewer, ReviewRound round);
}
