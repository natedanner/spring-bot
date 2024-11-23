package org.finos.springbot.workflow.java.mapping;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.finos.springbot.workflow.actions.Action;
import org.finos.springbot.workflow.actions.FormAction;
import org.finos.springbot.workflow.actions.SimpleMessageAction;
import org.finos.springbot.workflow.annotations.ChatRequest;
import org.finos.springbot.workflow.annotations.ChatVariable;
import org.finos.springbot.workflow.annotations.WorkMode;
import org.finos.springbot.workflow.content.Addressable;
import org.finos.springbot.workflow.content.Content;
import org.finos.springbot.workflow.content.Message;
import org.finos.springbot.workflow.content.User;
import org.finos.springbot.workflow.content.Word;
import org.finos.springbot.workflow.conversations.AllConversations;
import org.finos.springbot.workflow.help.HelpPage;
import org.finos.springbot.workflow.java.converters.ResponseConverters;
import org.finos.springbot.workflow.java.mapping.WildcardContent.Arity;
import org.finos.springbot.workflow.java.resolvers.WorkflowResolversFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;


public class ChatRequestChatHandlerMapping extends AbstractSpringComponentHandlerMapping<ChatRequest> {

	private final WorkflowResolversFactory wrf;
	private final ResponseConverters converters;
	
	public ChatRequestChatHandlerMapping(WorkflowResolversFactory wrf, ResponseConverters converters, AllConversations conversations) {
		super(conversations);
		this.wrf = wrf;
		this.converters = converters;
	}

	@Override
	protected ChatRequest getMappingForMethod(Method method, Class<?> handlerType) {
		if (AnnotatedElementUtils.hasAnnotation(method, ChatRequest.class)) {
			return AnnotatedElementUtils.getMergedAnnotation(method, ChatRequest.class);
		} else {
			return null;
		}
	}

	@Override
	public List<ChatMapping<ChatRequest>> getHandlers(Action a) {
		return getAllHandlers(a.getAddressable(), a.getUser()).stream()
				.filter(m -> m.getExecutor(a) != null)
				.collect(Collectors.toList());
	}

	@Override
	public List<ChatMapping<ChatRequest>> getAllHandlers(Addressable a, User u) {
		mappingRegistry.acquireReadLock();
		List<ChatMapping<ChatRequest>> out = new ArrayList<>(mappingRegistry.getRegistrations().values());
		mappingRegistry.releaseReadLock();
		out = out.stream().filter(r -> canBePerformed(a, u, r.getMapping())).collect(Collectors.toList());
		return out;
	}

	@Override
	public List<ChatHandlerExecutor> getExecutors(Action a) {
		List<ChatMapping<ChatRequest>> allHandlers = getAllHandlers(a.getAddressable(), a.getUser());
		return allHandlers.stream()
				.map(m -> m.getExecutor(a))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
	}

	protected List<MessageMatcher> createMessageMatchers(ChatRequest mapping, List<WildcardContent> chatVariables) {
		return Arrays.stream(mapping.value()).map(str -> createContentPattern(str, chatVariables))
				.map(MessageMatcher::new).collect(Collectors.toList());
	}

	private static final WildcardContent UNBOUND_WILDCARD = new WildcardContent(null, Content.class, Arity.ONE);

	private Content createContentPattern(String str, List<WildcardContent> chatVariables) {
		List<Content> items = Arrays.stream(str.split("\\s")).map(word -> {
			if (word.startsWith("{") && word.endsWith("}")) {
				String pathVariableName = word.substring(1, word.length() - 1);
				return chatVariables.stream()
						.filter(cv -> cv.chatVariable.name().equals(pathVariableName))
						.findFirst()
						.orElse(UNBOUND_WILDCARD);
			} else {
				String trimmedWord = word.trim();
				return new Word() {

					@Override
					public String getText() {
						return trimmedWord;
					}

					@Override
					public String toString() {
						return "Word [" + trimmedWord + "]";
					}
				};
			}
		}).collect(Collectors.toList());

		return new Message.MessageImpl(items);
	}

	protected List<WildcardContent> createWildcardContent(ChatRequest mapping, ChatHandlerMethod method) {
		MethodParameter[] params = method.getMethodParameters();
		return Arrays.stream(params).filter(m -> m.getParameterAnnotation(ChatVariable.class) != null).map(m -> {
			ChatVariable cv = m.getParameterAnnotation(ChatVariable.class);
			Type t = m.getGenericParameterType();

			if ((t instanceof Class) && (Content.class.isAssignableFrom((Class<?>) t))) {
				return new WildcardContent(cv, getContentClassFromType(t), Arity.ONE);
			} else if (t.getTypeName().startsWith(Optional.class.getName())) {
				ParameterizedType pt = (ParameterizedType) t;
				return new WildcardContent(cv, getContentClassFromType(pt.getActualTypeArguments()[0]), Arity.OPTIONAL);
			} else if ((t.getTypeName().startsWith(List.class.getName())) ||
					(t.getTypeName().startsWith(Collection.class.getName()))) {
				ParameterizedType pt = (ParameterizedType) t;
				return new WildcardContent(cv, getContentClassFromType(pt.getActualTypeArguments()[0]), Arity.LIST);
			}
			
			throw new UnsupportedOperationException("Can't set up wildcard for type: "+t.getTypeName()+" on "+mapping);
			
		}).collect(Collectors.toList());
	}

	@SuppressWarnings("unchecked")
	protected Class<? extends Content> getContentClassFromType(Type t) {
		if ((t instanceof Class) && (Content.class.isAssignableFrom((Class<?>) t))) {
			return (Class<? extends Content>) t;
		} else {
			throw new UnsupportedOperationException(
					"ChatVariables can only be used for Content subtypes: " + t.getTypeName());
		}
	}
	
	private boolean canBePerformed(Addressable a, User u, ChatRequest cb) {
		return canBePerformed(a, u, cb.excludeRooms(), cb.rooms(), cb.admin());
	}
	
	@Override
	protected MappingRegistration<ChatRequest> createMappingRegistration(ChatRequest mapping,
			ChatHandlerMethod handlerMethod) {

		List<WildcardContent> wildcards = createWildcardContent(mapping, handlerMethod);
		List<MessageMatcher> matchers = createMessageMatchers(mapping, wildcards);

		return new MappingRegistration<>(mapping, handlerMethod) {

			@Override
			public ChatHandlerExecutor getExecutor(Action a) {

				if (a instanceof SimpleMessageAction) {

					if (!canBePerformedHere((SimpleMessageAction) a)) {
						return null;
					}

					return matchesSimpleMessageAction((SimpleMessageAction) a);
				}

				if (a instanceof FormAction) {

					if (Objects.nonNull(((FormAction)a).getData().get("form")) && !HelpPage.class.isAssignableFrom(((FormAction) a).getData().get("form").getClass())) {
						return null;
					}

					if (!canBePerformedHere((FormAction) a)) {
						return null;
					}

					return matchesFormAction((FormAction) a);
				}

				return null;
			}

			private boolean canBePerformedHere(Action a) {
				ChatRequest cb = getMapping();

				return canBePerformed(a.getAddressable(), a.getUser(), cb);
			}

			private ChatHandlerExecutor matchesSimpleMessageAction(SimpleMessageAction a) {
				return pathMatches(a.getMessage(), a);
			}

			private ChatHandlerExecutor matchesFormAction(FormAction a) {
				return pathMatches(Message.of(Word.of(a.getAction())), a);
			}

			private ChatHandlerExecutor pathMatches(Message words, Action a) {
				MappingRegistration<?> me = this;
				ChatHandlerExecutor bestMatch = null;

				for (MessageMatcher messageMatcher : matchers) {
					Map<ChatVariable, Object> map = new HashMap<>();

					if (messageMatcher.consume(words, map)) {
						if ((bestMatch == null) || (bestMatch.getReplacements().size() < map.size())) {
							bestMatch = new AbstractHandlerExecutor(wrf, converters) {

								@Override
								public Map<ChatVariable, Object> getReplacements() {
									return map;
								}

								@Override
								public Action action() {
									return a;
								}

								@Override
								public ChatMapping<?> getOriginatingMapping() {
									return me;
								}
							};
						}
					}
				}
				return bestMatch;
			}

			@Override
			public boolean isButtonFor(Object o, WorkMode m) {
				return false;
			}
		};
	}
}
