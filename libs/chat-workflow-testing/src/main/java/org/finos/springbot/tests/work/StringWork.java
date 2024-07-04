package org.finos.springbot.tests.work;

import org.finos.springbot.workflow.annotations.Work;
import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.Pattern;

@Work
public class StringWork {

	@Length(min = 4, max = 15)
	@Pattern(regexp = "[a-z]*")
	String s;

	public String getS() {
		return s;
	}

	public void setS(String s) {
		this.s = s;
	}
	
	
}
