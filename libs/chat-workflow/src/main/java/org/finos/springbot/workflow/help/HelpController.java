package org.finos.springbot.workflow.help;

import org.finos.springbot.workflow.annotations.ChatRequest;
import org.finos.springbot.workflow.content.Addressable;
import org.finos.springbot.workflow.content.User;
import org.finos.springbot.workflow.java.mapping.ChatHandlerMapping;
import org.finos.springbot.workflow.java.mapping.ChatHandlerMethod;
import org.finos.springbot.workflow.java.mapping.ChatMapping;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.ResolvableType;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Builds help menu from the @ChatRequest annotations.
 * 
 * @author rob@kite9.com
 *
 */
public class HelpController implements ApplicationContextAware {

    private static final String DEFAULT_FORMATTER_PATTERN = "(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])";

	List<ChatHandlerMapping<ChatRequest>> exposedHandlers;

	public HelpController() {
		super();
	}
	
	
	protected String defaultDescription(Method m) {
		return Arrays.stream(Optional.ofNullable(m.getName()).orElse("").split(DEFAULT_FORMATTER_PATTERN))
			.map(word -> null != word && !word.trim().isEmpty() ? Character.toUpperCase(word.charAt(0)) + word.substring(1)
					: "")
			.collect(Collectors.joining(" "));
	}

	@ChatRequest(value = "help", description="Display this help page")
	public HelpPage handleHelp(Addressable a, User u) {
		initExposedHandlers();
		List<CommandDescription> commands = exposedHandlers
				.stream()
				.flatMap(ec -> ec.getAllHandlers(a, u).stream())
				.filter(this::includeInHelp)
				.map(this::convertToCommandDescriptions)
				.sorted(Comparator.comparing(CommandDescription::getHelpOrder)
						.thenComparing(CommandDescription::getDescription))
				.collect(Collectors.toList());

		return new HelpPage(commands);
	}

	private boolean includeInHelp(ChatMapping<ChatRequest> hm) {
		ChatRequest e = hm.getMapping();

		return !!e.addToHelp();
	}

	private CommandDescription convertToCommandDescriptions(ChatMapping<ChatRequest> hm) {
		ChatRequest e = hm.getMapping();
		ChatHandlerMethod m = hm.getHandlerMethod();
		String description = StringUtils.hasText(e.description()) ? e.description() : defaultDescription(m.getMethod());

		boolean addButton = e.isButtonOnHelpPage();
		if(addButton) {
			addButton = Arrays.stream(e.value())
					.flatMap(cmd -> Arrays.stream(cmd.split("\\s")))
					.map(word -> word.startsWith("{") && word.endsWith("}"))
					.noneMatch(Boolean::booleanValue);
		}

		return new CommandDescription(addButton, e.value()[0], description, e.helpOrder(), Arrays.asList(e.value()));
	}

	@SuppressWarnings("unchecked")
	public void initExposedHandlers() {
		if (exposedHandlers == null) {
			ResolvableType rt = ResolvableType.forClassWithGenerics(ChatHandlerMapping.class, ChatRequest.class);
			exposedHandlers = Arrays.stream(ctx.getBeanNamesForType(rt))
				.map(bn -> (ChatHandlerMapping<ChatRequest>) ctx.getBean(bn))
				.collect(Collectors.toList());
				
		}
	}
	
	private ApplicationContext ctx;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.ctx = applicationContext;
	}

}
