package workhourscontrol.service;

import java.util.List;

import workhourscontrol.entity.RegistroHora;
import workhourscontrol.exception.ControleHorasException;

public interface ControleHoras {

	void registrarHoras(List<RegistroHora> registros) throws ControleHorasException;

	void fecharConexao() throws ControleHorasException;

	double obterSaldoHoras() throws ControleHorasException;

}
