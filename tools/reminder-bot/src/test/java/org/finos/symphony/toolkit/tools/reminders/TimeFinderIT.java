package org.finos.symphony.toolkit.tools.reminders;

import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;

import org.finos.symphony.toolkit.workflow.actions.Action;
import org.finos.symphony.toolkit.workflow.actions.SimpleMessageAction;
import org.finos.symphony.toolkit.workflow.content.Addressable;
import org.finos.symphony.toolkit.workflow.content.Content;
import org.finos.symphony.toolkit.workflow.content.Message;
import org.finos.symphony.toolkit.workflow.content.OrderedContent;
import org.finos.symphony.toolkit.workflow.content.User;
import org.finos.symphony.toolkit.workflow.history.History;
import org.finos.symphony.toolkit.workflow.response.Response;
import org.finos.symphony.toolkit.workflow.response.WorkResponse;
import org.finos.symphony.toolkit.workflow.response.handlers.ResponseHandlers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.ErrorHandler;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;

@ExtendWith(MockitoExtension.class)
public class TimeFinderIT {

	@Mock
	StanfordCoreNLP stanfordCoreNLP;

	@Mock
	ReminderProperties reminderProperties;

	@Mock
	History history;

	@Mock
	ErrorHandler eh;

	@Mock
	ResponseHandlers responseHandlers;

	@InjectMocks
	TimeFinder timefinder;
	
	private SimpleMessageAction getAction() {
		SimpleMessageAction simpleMessageAction = new SimpleMessageAction(getAddressable(), getUser(), getMessage(),
				null);
		Action.CURRENT_ACTION.set(simpleMessageAction);
		return simpleMessageAction;
	}

	@SuppressWarnings("unchecked")
	@Test
	public void applyTest() {
		SimpleMessageAction action = getAction();
		when(history.getLastFromHistory(Mockito.any(Class.class), Mockito.any(Addressable.class)))
				.thenReturn(reminderList());

		timefinder.initializingStanfordProperties();
		timefinder.accept(action);

		ArgumentCaptor<Response> args = ArgumentCaptor.forClass(Response.class);
		Mockito.verify(responseHandlers).accept(args.capture());

		Assertions.assertEquals(args.getAllValues().size(), 1);
		WorkResponse fr = (WorkResponse) args.getValue();
		Reminder r = (Reminder) fr.getFormObject();
		Calendar c = Calendar.getInstance();
		int year = c.get(Calendar.YEAR);
		int month = c.get(Calendar.MONTH);
		int day = c.get(Calendar.DAY_OF_MONTH);
		Assertions.assertEquals(r.getLocalTime(), LocalDateTime.of(year, month + 1, day, 21, 20, 0));

	}

	private Optional<ReminderList> reminderList() {
		Reminder reminder = new Reminder();
		reminder.setDescription("Check at 9:30 pm");
		reminder.setLocalTime(LocalDateTime.now());
		reminder.setAuthor(getUser());
		List<Reminder> reminders = new ArrayList<>();
		reminders.add(reminder);
		ReminderList rl = new ReminderList();
		rl.setRemindBefore(10);
		rl.setTimeZone(ZoneId.of("Asia/Calcutta"));

		rl.setReminders(reminders);
		Optional<ReminderList> rrl = Optional.of(rl);
		return rrl;
	}

	private User getUser() {
		User user = new User() {
			@Override
			public String getEmailAddress() {
				return "New Address";
			}

			@Override
			public Type getTagType() {
				return null;
			}

			@Override
			public String getName() {
				return "Sherlock Holmes";
			}

			@Override
			public String getText() {
				return null;
			}
		};
		return user;

	}

	private Message getMessage() {
		Message m = new Message() {
			@Override
			public List<Content> getContents() {
				return null;
			}

			@Override
			public OrderedContent<Content> buildAnother(List<Content> contents) {
				return null;
			}

			@Override
			public String getText() {
				return "check at 9:30 pm";
			}
		};
		return m;
	}

	private Addressable getAddressable() {
		Addressable a = new Addressable() {

			@Override
			public int hashCode() {
				return super.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return super.equals(obj);
			}

			@Override
			protected Object clone() throws CloneNotSupportedException {
				return super.clone();
			}

			@Override
			public String toString() {
				return super.toString();
			}

			@Override
			protected void finalize() throws Throwable {
				super.finalize();
			}
		};
		return a;

	}

}