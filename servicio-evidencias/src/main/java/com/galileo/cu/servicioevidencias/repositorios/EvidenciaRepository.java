package com.galileo.cu.servicioevidencias.repositorios;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.apache.commons.net.ftp.FTPClient;

import com.galileo.cu.commons.models.Conexiones;
import com.galileo.cu.commons.models.Objetivos;
import com.galileo.cu.commons.models.Posiciones;
import com.galileo.cu.commons.models.Progresos;

public interface EvidenciaRepository {
    FTPClient ConectarFTP(Conexiones con) throws IOException;

    void DesconectarFTP(String er);

    void CrearDirectorio(FTPClient ftp, String camino, String error, String fileName);

    void SubirFichero(String nombre, String camino);

    String BuildFiles(Objetivos obj, List<Posiciones> pos, String tipoPrecision, String fi, String ff,
            String pathOperacion, String tip, long idAuth, int incre);

    void WriteFiles(String nombreFichero, String contenido, String camino, String error, long idAuth);

    void EliminarFichero(String nombre);

    void EliminarProgEvidens(Long idUsu, Boolean... keppzipPendiente);
}
