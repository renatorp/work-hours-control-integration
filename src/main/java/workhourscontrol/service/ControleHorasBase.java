package workhourscontrol.service;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;

import workhourscontrol.entity.RegistroHora;
import workhourscontrol.exception.ControleHorasException;
import workhourscontrol.strategy.AjusteHorasStrategy;

public abstract class ControleHorasBase implements ControleHoras {

	@Override
	public void registrarHoras(List<RegistroHora> registros) throws ControleHorasException {
		antesDeRegistrar(registros);
		registros = prepararListaRegistros(registros);
		registros = aplicarStrategies(registros);
		salvarRegistrosDeHoras(registros);
	}

	protected void antesDeRegistrar(List<RegistroHora> registros) throws ControleHorasException {
	}

	private List<RegistroHora> aplicarStrategies(List<RegistroHora> registros) {
		if (CollectionUtils.isNotEmpty(getParametros().getAjusteHoraStrategies())) {
			for (AjusteHorasStrategy strategy : getParametros().getAjusteHoraStrategies()) {
				registros = strategy.ajustarRegistros(registros);
			}
		}
		return registros;
	}

	protected List<RegistroHora> prepararListaRegistros(List<RegistroHora> registros) {
		return registros;
	}

	protected abstract void salvarRegistrosDeHoras(List<RegistroHora> registros) throws ControleHorasException;


	protected abstract AbstractParametroSControleHoras getParametros();
	
}
