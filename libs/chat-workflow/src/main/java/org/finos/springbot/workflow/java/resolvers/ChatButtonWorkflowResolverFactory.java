package org.finos.springbot.workflow.java.resolvers;

import java.util.Optional;

import org.finos.springbot.workflow.annotations.ChatButton;
import org.finos.springbot.workflow.java.mapping.ChatHandlerExecutor;

/**
 * Get chat button detail in controller 
 * @author mankvaia
 *
 */
public class ChatButtonWorkflowResolverFactory implements WorkflowResolverFactory {
	

	private final class ChatButtonWorkflowResolver extends AbstractClassWorkflowResolver {
		private final ChatHandlerExecutor che;

		private ChatButtonWorkflowResolver(ChatHandlerExecutor che) {
			this.che = che;
		}

		@Override
		public Optional<Object> resolve(Class<?> cl) {
			if (ChatButton.class.isAssignableFrom(cl) && che.getOriginatingMapping().getMapping() instanceof ChatButton) {
				
				return Optional.of((ChatButton) che.getOriginatingMapping().getMapping());
				
			} else {
				return Optional.empty();
			}
		}
		

		@Override
		public boolean canResolve(Class<?> cl) {
			return  resolve(cl).isPresent();			
		}
	}

	@Override
	public WorkflowResolver createResolver(ChatHandlerExecutor che) {
		return new ChatButtonWorkflowResolver(che);
	}

}
