package org.finos.springbot.teams.handlers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.finos.springbot.workflow.response.AttachmentResponse;

import com.microsoft.bot.schema.Attachment;
import com.microsoft.bot.schema.teams.FileConsentCard;

public class SimpleAttachmentHandler implements AttachmentHandler {

	public Attachment formatAttachment(AttachmentResponse ar) {

		try {
			Attachment a = new Attachment();
			String fileName = ar.getName().replaceAll("\\s+", "_") + "." + ar.getExtension();
			File f = null;
			f = File.createTempFile("temp", fileName);
			Files.write(f.toPath(), ar.getAttachment());

			Map<String, String> consentContext = new HashMap<>();
			consentContext.put("filename", fileName);
			consentContext.put("filepath", f.getAbsolutePath());

			FileConsentCard fileCard = new FileConsentCard();
			fileCard.setDescription("This is the file I want to send you");
			fileCard.setSizeInBytes(ar.getAttachment().length);
			fileCard.setAcceptContext(consentContext);
			fileCard.setDeclineContext(consentContext);

			a.setContent(fileCard);
			a.setName(fileName);
			a.setContentType(FileConsentCard.CONTENT_TYPE);
			return a;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}
}
