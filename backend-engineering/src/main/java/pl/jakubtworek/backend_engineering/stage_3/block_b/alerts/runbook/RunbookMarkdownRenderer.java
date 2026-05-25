package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts.runbook;

/**
 * Renders runbooks as Markdown files.
 *
 * Keeping runbooks generated from typed objects can reduce drift between alerts,
 * dashboards, and operational documentation.
 */
public final class RunbookMarkdownRenderer {

    public String render(Runbook runbook) {
        StringBuilder markdown = new StringBuilder();

        markdown.append("# runbooks/")
                .append(runbook.incidentType().slug())
                .append(".md\n\n");

        markdown.append("# ").append(runbook.title()).append("\n\n");

        markdown.append("## Detection\n\n");
        for (String signal : runbook.detectionSignals()) {
            markdown.append("- ").append(signal).append("\n");
        }

        markdown.append("\n## First actions\n\n");
        for (RunbookStep step : runbook.firstActions()) {
            markdown.append(step.number())
                    .append(". ")
                    .append(step.instruction())
                    .append("\n");
        }

        markdown.append("\n## PromQL\n\n");
        markdown.append("```promql\n");
        for (String query : runbook.promqlQueries()) {
            markdown.append(query).append("\n\n");
        }
        markdown.append("```\n\n");

        markdown.append("## Commands\n\n");
        markdown.append("```bash\n");
        for (String command : runbook.commands()) {
            markdown.append(command).append("\n");
        }
        markdown.append("```\n\n");

        markdown.append("## Done when\n\n");
        for (String condition : runbook.doneWhen()) {
            markdown.append("- ").append(condition).append("\n");
        }

        return markdown.toString();
    }
}