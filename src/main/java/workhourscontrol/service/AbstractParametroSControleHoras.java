package workhourscontrol.service;

import java.util.List;

import workhourscontrol.strategy.AjusteHorasStrategy;

public abstract class AbstractParametroSControleHoras {

	private List<AjusteHorasStrategy> ajusteHoraStrategies;

	public List<AjusteHorasStrategy> getAjusteHoraStrategies() {
		return ajusteHoraStrategies;
	}

	public void setAjusteHoraStrategies(List<AjusteHorasStrategy> ajusteHoraStrategies) {
		this.ajusteHoraStrategies = ajusteHoraStrategies;
	}
	
}
