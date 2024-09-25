package com.galileo.cu.servicioevidencias.servicios;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.commons.net.ftp.FTPClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Service;

import com.galileo.cu.commons.models.Objetivos;
import com.galileo.cu.commons.models.Posiciones;

@Service
public interface EvidenciaService {
	void GenerarKML(
			List<Objetivos> objs,
			String tipoPrecision,
			String fechaInicio,
			String fechaFin,
			String token);

	FTPClient ConectarFTP(long idAuth, Boolean... passiveMode) throws Exception;

	void Desconectar(FTPClient ftp);
}
