package org.finos.springbot.tests.work;

import org.finos.springbot.workflow.annotations.Work;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Work
public class IntegerWork {

	@Min(0)
	@Max(100)
	Integer s;

	public Integer getS() {
		return s;
	}

	public void setS(Integer s) {
		this.s = s;
	}
	
	
}
