package org.finos.springbot.teams.response;

import org.finos.springbot.workflow.annotations.WorkMode;
import org.finos.springbot.workflow.content.Addressable;
import org.finos.springbot.workflow.form.ButtonList;
import org.finos.springbot.workflow.form.ErrorMap;
import org.finos.springbot.workflow.response.WorkResponse;

import java.util.Map;

public class TeamsWorkResponse extends WorkResponse {
    private final String summary;

    public TeamsWorkResponse(Addressable to, Map<String, Object> data, String templateName, WorkMode m, Class<?> formClass, String summary) {
        super(to, data, templateName, m, formClass);
        this.summary = summary;
    }

    public TeamsWorkResponse(Addressable to, Object o, WorkMode m, ButtonList buttons, ErrorMap errors, String summary) {
        super(to, o, m, buttons, errors);
        this.summary = summary;
    }

    public TeamsWorkResponse(Addressable to, Object o, WorkMode m, String summary) {
        super(to, o, m);
        this.summary = summary;
    }

    public String getSummary() {
        return summary;
    }
}
