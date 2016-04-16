package workhourscontrol.service;

import java.io.File;
import java.util.ArrayList;

import workhourscontrol.strategy.AjusteHorasStrategy;

public class ControleHorasPlanilhaBuilder implements ControleHorasBuilder{

	ControleHorasPlanilha controleHoras;
	ParametrosControleHorasPlanilha parametros;

	public ControleHorasPlanilhaBuilder(ControleHorasPlanilha controleHoras) {
		parametros = new ParametrosControleHorasPlanilha();
		parametros.setAjusteHoraStrategies(new ArrayList<>());
		this.controleHoras = controleHoras;
	}

	public ControleHorasPlanilhaBuilder setPlanilha(File arquivo) {
		parametros.setFile(arquivo);
		return this;
	}
	
	public ControleHorasPlanilhaBuilder addAjusteHorasStrategy(AjusteHorasStrategy ajusteHorasStrategy) {
		this.parametros.getAjusteHoraStrategies().add(ajusteHorasStrategy);
		return this;
	}

	public ControleHoras build() {
		this.controleHoras.setParametros(parametros);
		return this.controleHoras;
	}
}
