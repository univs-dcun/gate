package ai.univs.gate.modules.project.application.input;

public record ProjectQuery(
        Long accountId,
        String projectKeyword,
        Integer page,
        Integer pageSize,
        String direction,
        String sortBy
) {

}
