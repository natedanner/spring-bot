package org.finos.springbot.teams.handlers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;

import org.finos.springbot.teams.TeamsException;
import org.finos.springbot.teams.content.TeamsAddressable;
import org.finos.springbot.teams.conversations.TeamsErrorResourceResponse;
import org.finos.springbot.teams.history.StorageIDResponseHandler;
import org.finos.springbot.teams.history.TeamsHistory;
import org.finos.springbot.teams.response.templating.EntityMarkupTemplateProvider;
import org.finos.springbot.teams.response.templating.MarkupAndEntities;
import org.finos.springbot.teams.state.TeamsStateStorage;
import org.finos.springbot.teams.templating.adaptivecard.AdaptiveCardPassthrough;
import org.finos.springbot.teams.templating.adaptivecard.AdaptiveCardTemplateProvider;
import org.finos.springbot.teams.templating.thymeleaf.ThymeleafTemplateProvider;
import org.finos.springbot.workflow.actions.Action;
import org.finos.springbot.workflow.actions.ErrorAction;
import org.finos.springbot.workflow.annotations.WorkMode;
import org.finos.springbot.workflow.response.AttachmentResponse;
import org.finos.springbot.workflow.response.ErrorResponse;
import org.finos.springbot.workflow.response.MessageResponse;
import org.finos.springbot.workflow.response.Response;
import org.finos.springbot.workflow.response.WorkResponse;
import org.finos.springbot.workflow.response.handlers.ResponseHandler;
import org.finos.springbot.workflow.tags.HeaderDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.ErrorHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.bot.connector.rest.ErrorResponseException;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.Attachment;
import com.microsoft.bot.schema.Entity;
import com.microsoft.bot.schema.ResourceResponse;
import com.microsoft.bot.schema.TextFormatTypes;

public class TeamsResponseHandler implements ResponseHandler<ResourceResponse>, ApplicationContextAware {
	
	private static final Logger LOG = LoggerFactory.getLogger(TeamsResponseHandler.class);
	
	
	
	
	protected AttachmentHandler attachmentHandler;
	protected ApplicationContext ctx;
	protected ErrorHandler eh;
	protected EntityMarkupTemplateProvider messageTemplater;
	protected AdaptiveCardTemplateProvider workTemplater;
	protected ThymeleafTemplateProvider displayTemplater;
	protected TeamsStateStorage teamsState;
	protected ActivityHandler ah;
	
	public TeamsResponseHandler( 
			AttachmentHandler attachmentHandler,
			EntityMarkupTemplateProvider messageTemplater,
			AdaptiveCardTemplateProvider workTemplater,
			ThymeleafTemplateProvider displayTemplater, 
			TeamsStateStorage th, 
			ActivityHandler ah) {
		this.attachmentHandler = attachmentHandler;
		this.messageTemplater = messageTemplater;
		this.workTemplater = workTemplater;
		this.displayTemplater = displayTemplater;
		this.teamsState = th;
		this.ah = ah;
	}
	
	protected void initErrorHandler() {
		if (eh == null) {
			eh = ctx.getBean(ErrorHandler.class);
		}
	}	
	
	enum TemplateType { ADAPTIVE_CARD, THYMELEAF, BOTH };

	@Override
	public ResourceResponse apply(Response t) {
		if (t.getAddress() instanceof TeamsAddressable) {		
			TeamsAddressable ta = (TeamsAddressable) t.getAddress();

			try {
				if (t instanceof MessageResponse) {
					MessageResponse mr = (MessageResponse)t;
					Attachment attachment = null;
					MarkupAndEntities mae = messageTemplater.template(mr);
					String content = mae.getContents();
					List<Entity> entities = mae.getEntities();
					
					if (t instanceof AttachmentResponse) {
						attachment = attachmentHandler.formatAttachment((AttachmentResponse) mr);
					}
					
					return sendXMLResponse(content, attachment, ta, entities, mr.getData())
						.handle(handleErrorAndStorage(content, ta, mr.getData(), t)).get();
					
				} else if (t instanceof WorkResponse) {
					WorkResponse wr = (WorkResponse) t;
					TemplateType tt = getTemplateType(wr);
 					 
					if (tt == TemplateType.ADAPTIVE_CARD) {
						JsonNode cardJson = workTemplater.template(wr);
						return sendCardResponse(cardJson, ta, wr.getData())
							.handle(handleErrorAndStorage(cardJson, ta, wr.getData(), t)).get();
					} else {
						MarkupAndEntities mae = displayTemplater.template(wr);
						String content = mae.getContents();
						List<Entity> entities = mae.getEntities();
						return sendXMLResponse(content, null, ta, entities, wr.getData())
							.handle(handleButtonsIfNeeded(tt, wr))
							.handle(handleErrorAndStorage(content, ta, wr.getData(), t)).get();
						
					}
				}
			} catch (Exception e) {
				throw new TeamsException("Couldn't handle response " +t, e);
			}
		}
		
		return null;
	}


	protected TemplateType getTemplateType(WorkResponse wr) {
		TemplateType tt;
		if (displayTemplater.hasTemplate(wr)) {
			tt = TemplateType.THYMELEAF;
		} else if (workTemplater.hasTemplate(wr) || AdaptiveCardPassthrough.isAdaptiveCard(wr)) {
			tt = TemplateType.ADAPTIVE_CARD;
		} else if (wr.getMode() == WorkMode.EDIT) {
			tt = TemplateType.ADAPTIVE_CARD;
		} else if (ThymeleafTemplateProvider.needsButtons(wr)) {
			tt = TemplateType.BOTH;
		} else {
			tt = TemplateType.THYMELEAF;
		}
		
		return tt;
	}

	protected CompletableFuture<ResourceResponse> sendXMLResponse(String xml, Attachment attachment, TeamsAddressable address, List<Entity> entities, Map<String, Object> data) throws Exception {
		Activity out = Activity.createMessageActivity();
		if(Objects.nonNull(attachment)) {
			out.getAttachments().add(attachment);
		}else {
			out.setEntities(entities);
			out.setTextFormat(TextFormatTypes.XML);
		}
		out.setText(xml);
		return ah.handleActivity(out, address);
	}

	private BiFunction<? super ResourceResponse, Throwable, ResourceResponse> handleButtonsIfNeeded(TemplateType tt, WorkResponse wr) {
		return (rr, e) -> {
			try {
				if (e == null) {
					if (tt == TemplateType.BOTH) {
						// we also need to send the buttons.  
						JsonNode buttonsJson = workTemplater.template(null);
						wr.getData().put(AdaptiveCardTemplateProvider.FORMID_KEY, "just-buttons");
						JsonNode expandedJson = workTemplater.applyTemplate(buttonsJson, wr);
						return sendCardResponse(expandedJson, (TeamsAddressable) wr.getAddress(), wr.getData()).get();
					} else {						
						return rr;
					}

				} else {
					throw e;
				}
			} catch (Throwable e1) {
				if (e instanceof CompletionException
						&& ((CompletionException) e1).getCause() instanceof ErrorResponseException) {
					throw (ErrorResponseException) ((CompletionException) e1).getCause();
				}
				throw new RuntimeException("Passing on exception", e1);
			}
		};
	}
 
	private BiFunction<? super ResourceResponse, Throwable, ResourceResponse> handleErrorAndStorage(Object out, TeamsAddressable address, Map<String, Object> data, Response t) {
		return (rr, e) -> {
				if (e != null) {					
					LOG.error("Error message for stream id {} , message: {} ", address.getKey() , e.getMessage());
					if (out instanceof ObjectNode){
						try {
							LOG.error("json:\n"+new ObjectMapper().writeValueAsString(out));
						} catch (JsonProcessingException e1) {
						}
					} else {
						LOG.error("message:\n"+out);
					} 
					
					if(!(t  instanceof ErrorResponse)) {
						Action.CURRENT_ACTION.set(new ErrorAction(address, data));
					}
					
					initErrorHandler();				
					eh.handleError(e);	
					Action.CURRENT_ACTION.set(Action.NULL_ACTION);
					rr = new TeamsErrorResourceResponse(address.getKey(), e);
				} else if(rr != null) {
					performStorage(address, data, teamsState);
				}
				
				return rr;
			};
	}
	
	protected CompletableFuture<ResourceResponse> sendCardResponse(JsonNode json, TeamsAddressable address, Map<String, Object> data) throws Exception {
		Activity out = Activity.createMessageActivity();
		Attachment body = new Attachment();
		body.setContentType("application/vnd.microsoft.card.adaptive");
		body.setContent(json);
		if(data.containsKey(WorkResponse.SUMMARY_KEY) && Objects.nonNull(data.get(WorkResponse.SUMMARY_KEY))) {
			out.setSummary(data.get(WorkResponse.SUMMARY_KEY).toString());
		}
		out.getAttachments().add(body);
		return ah.handleActivity(out, address);
	}

	public static void performStorage(TeamsAddressable address, Map<String, Object> data, TeamsStateStorage teamsState) {
		String dataKey = (String) data.get(StorageIDResponseHandler.STORAGE_ID_KEY);
		if (dataKey != null) {
			// first, store data for message
			Map<String, String> tags = createStorageTags(data, address);
			String file = address.getKey()+"/"+dataKey;
			teamsState.store(file, tags, data);			
		}
	}
	
	public static Map<String, String> createStorageTags(Map<String, Object> data, TeamsAddressable address) {
		Map<String, String> out = new HashMap<>();
		HeaderDetails h = (HeaderDetails) data.get(HeaderDetails.KEY);
		if (h != null) {
			h.getTags().forEach(t -> out.put(t, TeamsStateStorage.PRESENT));
		}
		
		out.put(TeamsStateStorage.ADDRESSABLE_KEY, address.getKey());
		out.put(TeamsHistory.TIMESTAMP_KEY, ""+System.currentTimeMillis());
		return out;
	}
	
	

	@Override
	public int getOrder() {
		return LOWEST_PRECEDENCE;
	}


	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.ctx = applicationContext;
	}

}
