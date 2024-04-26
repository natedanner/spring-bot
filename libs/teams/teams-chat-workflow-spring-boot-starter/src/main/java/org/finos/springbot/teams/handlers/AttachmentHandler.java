package org.finos.springbot.teams.handlers;

import org.finos.springbot.workflow.response.AttachmentResponse;

import com.microsoft.bot.schema.Attachment;

public interface AttachmentHandler {

	public Attachment formatAttachment(AttachmentResponse ar);
}
