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

        Conexiones con = getFTPConnection()
                .orElseThrow(() -> new IOException("No existe un servicio FTP entre las conexiones"));

        FTPClient ftp = connectFTP(con, null);

        if (!Strings.isNullOrEmpty(con.getRuta())) {
            baseDir = con.getRuta();
        } else {
            baseDir = getFTPDirectory(ftp);
        }
        log.info(baseDir);

        try {
            ftp.changeWorkingDirectory(baseDir);
        } catch (Exception e) {
            String err = "Fallo al intentar cambiar al directorio {}";
            log.error(err, baseDir, e);
            disconnectFTP(ftp);
            throw new IOException(err);
        }

        Page<String> listFiles = null;
        try {
            listFiles = ListFiles(pageable);
        } catch (Exception e) {
            String err = "Error al obtener listado de ficheros .csv";
            log.error(err, e);
            disconnectFTP(ftp);
            throw new IOException(err, e);

        }
        disconnectFTP(ftp);
        return listFiles;
    }

    private Optional<Conexiones> obtenerConexionFTP() {
        try {
            return Optional.ofNullable(conRepo.findFirstByServicioContaining("ftp"));
        } catch (Exception e) {
            log.error("Error al consultar las conexiones FTP en la base de datos", e);
            return Optional.empty();
        }
    }

    private Optional<Conexiones> getFTPConnection() {
        try {
            return Optional.ofNullable(conRepo.findFirstByServicioContaining("ftp"));
        } catch (Exception e) {
            log.error("Fallo al consultar las conexiones FTP en la base de datos", e);
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

    private FTPClient connectFTP(Conexiones con, FTPClient ftp) throws IOException {
        if (ftp == null) {
            ftp = new FTPClient();
        }

        if (ftp == null || !ftp.isConnected()) {
            try {
                ftp = makeFTPConnection(con);
            } catch (Exception e) {
                if (e.getMessage().contains("Fallo") || e.getMessage().contains("Falló")) {
                    throw new IOException(e.getMessage());
                }
                String err = "Fallo al conectar con el servidor FTP";
                log.error("{}", err, e);
                throw new IOException(err);
            }
        }
        return ftp;
    }

    private FTPClient makeFTPConnection(Conexiones con) throws IOException {
        FTPClient ftp = new FTPClient();
        try {
            int puerto = Strings.isNullOrEmpty(con.getPuerto()) ? DEFAULT_FTP_PORT : Integer.parseInt(con.getPuerto());
            ftp.connect(con.getIpServicio(), puerto);
        } catch (Exception e) {
            String err = "Fallo al intentar crear una conexión FTP {}:{}";
            log.error(err, con.getIpServicio(), con.getPuerto(), e);
            throw new IOException(err + con.getIpServicio());
        }

        int reply = ftp.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            String err = "Conexión fallida con el servidor FTP " + con.getIpServicio();
            log.error(err);
            desconectarFTP(ftp);
            throw new IOException(err);
        }

        authenticateFTP(ftp, con);

        setUpPassiveMode(ftp);

        return ftp;
    }

    private void authenticateFTP(FTPClient ftp, Conexiones con) throws IOException {
        try {
            boolean successLogin = ftp.login(con.getUsuario(), con.getPassword());
            if (!successLogin) {
                String err = "Fallo intentando autenticarse con el servidor FTP";
                log.error("{}", err);
                disconnectFTP(ftp);
                throw new IOException(err);
            }
            log.info("La Autenticación con el servidor FTP, fue exitosa");
        } catch (IOException e) {
            String err = "Fallo intentando autenticarse con el servidor FTP";
            log.error("{}", err, e);
            disconnectFTP(ftp);
            throw new IOException(err, e);
        }
    }

    private void setUpPassiveMode(FTPClient ftp) throws IOException {
        try {
            ftp.enterLocalPassiveMode();
            ftp.setControlKeepAliveTimeout(1000);
        } catch (Exception e) {
            String err = "Fallo al configurar el modo pasivo en la conexión FTP";
            log.error("{}", err, e);
            disconnectFTP(ftp);
            throw new IOException(err, e);
        }
    }

    private String getFTPDirectory(FTPClient ftp) throws IOException {
        try {
            return ftp.printWorkingDirectory();
        } catch (Exception e) {
            String err = "Fallo al obtener el directorio predeterminado del servidor FTP";
            log.error(err, e);
            disconnectFTP(ftp);
            throw new IOException(err, e);
        }
    }

    private void disconnectFTP(FTPClient ftp) throws IOException {
        if (ftp != null && ftp.isConnected()) {
            try {
                ftp.logout();
                ftp.disconnect();
                ftp = null;
            } catch (IOException e) {
                String err = "Fallo al desconectar el servidor FTP";
                log.error("{}", err, e);
                throw new IOException(err);
            }
        }
    }

    private String obtenerDirectorioFTP() throws IOException {
        try {
            return ProgEvidens.ftpCSV.printWorkingDirectory();
        } catch (Exception e) {
            String err = "Error al obtener el directorio predeterminado del servidor FTP";
            log.error(err, e);
            desconectarFTP(ProgEvidens.ftpCSV);
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
            String err = "Conexión fallida con el servidor FTP " + con.getIpServicio();
            log.error(err);
            desconectarFTP(ftp);
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
                String err = "Autenticación fallida con el servidor FTP, intentando listar .csv";
                log.error("{}", err);
                desconectarFTP(ftp);
                throw new IOException(err);
            }
            log.info("Autenticación exitosa con el servidor FTP");
        } catch (IOException e) {
            String err = "Error al autenticar con el servidor FTP " + con.getIpServicio() + ", intentando listar .csv";
            log.error(err, e);
            desconectarFTP(ftp);
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
        // FTPFile[] dir = ProgEvidens.ftpCSV.listDirectories();
        List<String> csvFiles = Arrays.stream(files)
                .filter(file -> file.getName().toLowerCase().endsWith(".csv"))
                .map(FTPFile::getName)
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), csvFiles.size());

        return new PageImpl<>(csvFiles.subList(start, end), pageable, csvFiles.size());
    }

    public byte[] downloadFile(String fileName) throws IOException {
        FTPClient ftpClient = new FTPClient();
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            boolean success = ProgEvidens.ftpCSV.retrieveFile(fileName, outputStream);

            if (success) {
                return outputStream.toByteArray();
            } else {
                String err = "Fallo descargando el fichero: " + fileName;
                log.error(err);
                throw new IOException(err);
            }
        } finally {
            if (ProgEvidens.ftpCSV.isConnected()) {
                ProgEvidens.ftpCSV.disconnect();
            }
        }
    }

    private void desconectarFTP(FTPClient ftp) throws IOException {
        if (ftp != null && ftp.isConnected()) {
            try {
                ftp.disconnect();
                ftp = null;
                ProgEvidens.ftpCSV = null;
            } catch (IOException e) {
                log.error("Error al desconectar el servidor FTP", e);
                throw e;
            }
        }
    }
}
