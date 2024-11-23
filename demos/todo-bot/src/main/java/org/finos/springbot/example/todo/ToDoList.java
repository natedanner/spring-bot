/**
 * 
 */
package org.finos.springbot.example.todo;

import java.util.ArrayList;
import java.util.List;

import org.finos.springbot.workflow.annotations.RequiresUserList;
import org.finos.springbot.workflow.annotations.Work;

import jakarta.validation.Valid;

/**
 * @author rupnsur
 *
 */
@Work
@RequiresUserList
public class ToDoList {

	@Valid
	private List<ToDoItem> items = new ArrayList<>();

	public ToDoList() {
		super();
	}

	public ToDoList(List<ToDoItem> items) {
		super();
		this.items = items;
	}

	public List<ToDoItem> getItems() {
		return items;
	}

	public void setItems(List<ToDoItem> items) {
		this.items = items;
	}

	
	
	
}
