package com.galileo.cu.servicioevidencias.repositorios;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.net.ftp.FTPClient;

import com.galileo.cu.commons.models.Conexiones;
import com.galileo.cu.commons.models.Objetivos;
import com.galileo.cu.commons.models.Posiciones;
import com.galileo.cu.commons.models.Progresos;

public interface EvidenciaRepository {
    FTPClient ConectarFTP(Conexiones con) throws IOException;

    void DesconectarFTP(FTPClient ftpClient, String er);

    void CrearDirectorio(FTPClient ftp, String camino, String error, String fileName);

    void SubirFichero(FTPClient ftpClient, String nombre, String camino);

    String BuildFiles(Objetivos obj, List<Posiciones> pos, String tipoPrecision, String finicio, String ffin,
            String pathOperacion, String tip, long idAuth, String token);

    void WriteFiles(String nombreFichero, String contenido, String camino, String error);

    void EliminarFichero(String nombre);

    void EliminarProgEvidens(String token, Boolean... keppzipPendiente);
}
