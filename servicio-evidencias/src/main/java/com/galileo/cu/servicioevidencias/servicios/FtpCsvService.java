package com.galileo.cu.servicioevidencias.servicios;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.galileo.cu.commons.models.Conexiones;
import com.galileo.cu.servicioevidencias.repositorios.ConexionesRepository;
import com.galileo.cu.servicioevidencias.repositorios.ProgEvidens;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;

@Slf4j
@Service
public class FtpCsvService {

    private final ConexionesRepository conRepo;

    private static final String DEFAULT_DIRECTORY = "/";
    private static final int DEFAULT_FTP_PORT = 21;

    @Autowired
    public FtpCsvService(ConexionesRepository conRepo) {
        this.conRepo = conRepo;
    }

    public Page<String> listCsvFiles(Pageable pageable) throws IOException {
        String baseDir = DEFAULT_DIRECTORY;

        Conexiones con = obtenerConexionFTP()
                .orElseThrow(() -> new IOException("No existe un servicio FTP entre las conexiones"));

        conectarFTP(con);

        if (!Strings.isNullOrEmpty(con.getRuta())) {
            baseDir = con.getRuta();
        } else {
            baseDir = obtenerDirectorioFTP();
        }
        log.info(baseDir);

        return ListFiles(pageable); // Aquí deberías implementar la lógica para listar los archivos CSV.
    }

    private Optional<Conexiones> obtenerConexionFTP() {
        try {
            return Optional.ofNullable(conRepo.findFirstByServicioContaining("ftp"));
        } catch (Exception e) {
            log.error("Error al consultar las conexiones FTP en la base de datos", e);
            return Optional.empty();
        }
    }

    private void conectarFTP(Conexiones con) throws IOException {
        if (ProgEvidens.ftpCSV == null || !ProgEvidens.ftpCSV.isConnected()) {
            try {
                ProgEvidens.ftpCSV = crearConexionFTP(con);
            } catch (Exception e) {
                log.error("Error al conectar con el servidor FTP", e);
                throw new IOException("Error al conectar con el servidor FTP: " + e.getMessage(), e);
            }
        }
    }

    private String obtenerDirectorioFTP() throws IOException {
        try {
            return ProgEvidens.ftpCSV.printWorkingDirectory();
        } catch (Exception e) {
            String err = "Error al obtener el directorio predeterminado del servidor FTP";
            log.error(err, e);
            throw new IOException(err, e);
        }
    }

    private FTPClient crearConexionFTP(Conexiones con) throws IOException {
        FTPClient ftp = new FTPClient();
        try {
            int puerto = Strings.isNullOrEmpty(con.getPuerto()) ? DEFAULT_FTP_PORT : Integer.parseInt(con.getPuerto());
            ftp.connect(con.getIpServicio(), puerto);
        } catch (Exception e) {
            log.error("Error al intentar conectarse al servidor FTP {}:{}", con.getIpServicio(), con.getPuerto(), e);
            throw new IOException("Error al conectarse con el servidor FTP: " + con.getIpServicio(), e);
        }

        int reply = ftp.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            ftp.disconnect();
            String err = "Conexión fallida con el servidor FTP " + con.getIpServicio();
            log.error(err);
            throw new IOException(err);
        }

        autenticarFTP(ftp, con);

        configurarModoPasivo(ftp);

        return ftp;
    }

    private void autenticarFTP(FTPClient ftp, Conexiones con) throws IOException {
        try {
            boolean successLogin = ftp.login(con.getUsuario(), con.getPassword());
            if (!successLogin) {
                throw new IOException("Autenticación fallida con el servidor FTP");
            }
            log.info("Autenticación exitosa con el servidor FTP");
        } catch (IOException e) {
            String err = "Error al autenticar con el servidor FTP " + con.getIpServicio();
            log.error(err, e);
            throw new IOException(err, e);
        }
    }

    private void configurarModoPasivo(FTPClient ftp) throws IOException {
        try {
            ftp.enterLocalPassiveMode();
            ftp.setControlKeepAliveTimeout(1000);
        } catch (Exception e) {
            log.error("Error al configurar el modo pasivo en la conexión FTP", e);
            desconectarFTP(ftp);
            throw new IOException("Error al configurar el modo pasivo", e);
        }
    }

    public Page<String> ListFiles(Pageable pageable) throws IOException {
        FTPFile[] files = ProgEvidens.ftpCSV.listFiles();
        List<String> csvFiles = Arrays.stream(files)
                .filter(file -> file.getName().toLowerCase().endsWith(".csv"))
                .map(FTPFile::getName)
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), csvFiles.size());

        return new PageImpl<>(csvFiles.subList(start, end), pageable, csvFiles.size());
    }

    private void desconectarFTP(FTPClient ftp) throws IOException {
        if (ftp != null && ftp.isConnected()) {
            try {
                ftp.disconnect();
            } catch (IOException e) {
                log.error("Error al desconectar el servidor FTP", e);
                throw e;
            }
        }
    }
}
