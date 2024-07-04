package org.finos.springbot.tests.form;

import java.util.ArrayList;
import java.util.List;

import org.finos.springbot.workflow.annotations.Work;

import jakarta.validation.Valid;

@Work()
public class Collection {

	@Valid
	private List<Primitives> items = new ArrayList<>();

	public Collection() {
		super();
	}

	public List<Primitives> getItems() {
		return items;
	}

	public void setItems(List<Primitives> items) {
		this.items = items;
	}
}
