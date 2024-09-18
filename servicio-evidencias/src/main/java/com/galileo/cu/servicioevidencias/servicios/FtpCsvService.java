package com.galileo.cu.servicioevidencias.servicios;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.galileo.cu.commons.models.Conexiones;
import com.galileo.cu.servicioevidencias.dtos.TreeNode;
import com.galileo.cu.servicioevidencias.repositorios.ConexionesRepository;
import com.galileo.cu.servicioevidencias.repositorios.ProgEvidens;
import com.google.common.base.Strings;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    public Page<String> listCsvFiles(
            Pageable pageable,
            String unidadName,
            String operacionName,
            String fechaInicio,
            String fechaFin) throws IOException {
        String baseDir = DEFAULT_DIRECTORY;

        String fechaInicioFormateada = convertirFecha(fechaInicio);
        String fechaFinFormateada = convertirFecha(fechaFin);

        log.info(fechaInicioFormateada);
        log.info(fechaFinFormateada);

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

        log.info(ftp.printWorkingDirectory());
        // 2. Crear path
        String path = crearPath(unidadName, operacionName, fechaInicioFormateada, fechaFinFormateada);

        log.info(path);

        List<String> directories = getDirectoriesFTP(ftp, baseDir, path, pageable);
        if (directories.size() > 0) {
            log.info("Primer directorio: {}, Cantidad: {}", directories.get(0), directories.size());
        } else {
            String err = "Fallo, no existen evidencias generadas";
            log.error("{}, ", err);
            disconnectFTP(ftp);
            throw new IOException(err);
        }

        Page<String> listFiles = null;
        try {
            listFiles = ListFiles(ftp, pageable);
        } catch (Exception e) {
            String err = "Fallo al obtener listado de ficheros .csv";
            log.error(err, e);
            disconnectFTP(ftp);
            throw new IOException(err, e);

        }
        disconnectFTP(ftp);
        return listFiles;
    }

    private Optional<Conexiones> getFTPConnection() {
        try {
            return Optional.ofNullable(conRepo.findFirstByServicioContaining("ftp"));
        } catch (Exception e) {
            log.error("Fallo al consultar las conexiones FTP en la base de datos", e);
            return Optional.empty();
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
            String err = "Fallo intentando conectar con el servidor FTP " + con.getIpServicio();
            log.error(err);
            disconnectFTP(ftp);
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

    public Page<String> ListFiles(FTPClient ftp, Pageable pageable) throws IOException {
        FTPFile[] files = ftp.listFiles();
        // FTPFile[] dir = ftp.listDirectories();
        List<String> csvFiles = Arrays.stream(files)
                .filter(file -> file.getName().toLowerCase().endsWith(".csv"))
                .map(FTPFile::getName)
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), csvFiles.size());

        return new PageImpl<>(csvFiles.subList(start, end), pageable, csvFiles.size());
    }

    public InputStream downloadFileAsStream(String fileName) throws IOException {
        String baseDir = DEFAULT_DIRECTORY;

        Conexiones con = getFTPConnection()
                .orElseThrow(() -> new IOException("No existe un servicio FTP entre las conexiones"));

        FTPClient ftp = connectFTP(con, null);

        if (!Strings.isNullOrEmpty(con.getRuta())) {
            baseDir = con.getRuta();
        } else {
            baseDir = getFTPDirectory(ftp);
        }
        log.info("Directorio base: {}", baseDir);

        try {
            ftp.changeWorkingDirectory(baseDir);
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
        } catch (Exception e) {
            String err = "Fallo al intentar cambiar al directorio " + baseDir;
            log.error(err, e);
            disconnectFTP(ftp);
            throw new IOException(err);
        }

        // Comprobación de la existencia del fichero
        FTPFile[] files = ftp.listFiles(fileName);
        if (files.length == 0) {
            String err = "Fallo, el fichero {}, no existe en el servidor";
            log.error(err, fileName);
            disconnectFTP(ftp);
            throw new IOException(err);
        } else {
            log.info("El fichero {}, existe en el servidor", fileName);
        }

        try {
            InputStream inputStream = ftp.retrieveFileStream(fileName);
            if (inputStream == null) {
                String err = "Fallo al obtener el stream del fichero: " + fileName;
                log.error(err);
                throw new IOException(err);
            }
            log.info("Inicia la descarga de {} en {}", fileName, baseDir);

            // Devolvemos un InputStream que cierra la conexión FTP cuando se cierra el
            // stream
            return new FilterInputStream(inputStream) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        if (!ftp.completePendingCommand()) {
                            log.warn("Fallo al completar la operación FTP");
                        }
                        disconnectFTP(ftp);
                    }
                }
            };
        } catch (IOException e) {
            disconnectFTP(ftp);
            throw e;
        }
    }

    private String convertirFecha(String fecha) {
        LocalDateTime dateTime = LocalDateTime.parse(fecha, DateTimeFormatter.ISO_DATE_TIME);
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH_mm_ss"));
    }

    private String crearPath(String unidadName, String operacionName, String fechaInicioFormateada,
            String fechaFinFormateada) {
        return "UNIDADES/" + unidadName + "/INFORMES " + operacionName + "/PERSONALIZADOS/" +
                operacionName + "("
                + fechaInicioFormateada + "-" + fechaFinFormateada + ")";
    }

    private List<String> getDirectoriesFTP(FTPClient ftp, String baseDir, String path, Pageable pageable)
            throws IOException {
        List<String> directorios = new ArrayList<>();
        // FTPFile[] dirs = ftp.listDirectories(path);
        ftp.changeWorkingDirectory(path);
        FTPFile[] dirs = ftp.listFiles();
        log.info("dirs.length = {}", dirs.length);
        for (FTPFile dir : dirs) {
            if (dir.isDirectory() && !dir.getName().equals("KMLS") && !dir.getName().equals(".")
                    && !dir.getName().equals("..")) {
                log.info("carpeta:: " + dir.getName());
                directorios.add(dir.getName());
            }
        }

        ftp.changeWorkingDirectory(baseDir);

        int start = (int) pageable.getOffset();
        int end = Math.min((start + 2), directorios.size());
        // int end = Math.min((start + pageable.getPageSize()), directorios.size());
        return directorios.subList(start, end);
    }

    private TreeNode treeBuild(FTPClient ftp, String basePath, List<String> directorios) throws Exception {
        TreeNode root = new TreeNode("root", "0", new ArrayList<>(), true);

        for (int i = 0; i < directorios.size(); i++) {
            String directorio = directorios.get(i);
            TreeNode dirNode = new TreeNode(directorio, String.valueOf(i + 1), new ArrayList<>(), true);

            FTPFile[] archivos = ftp.listFiles(basePath + "/" + directorio);
            for (int j = 0; j < archivos.length; j++) {
                FTPFile archivo = archivos[j];
                if (archivo.getName().toLowerCase().endsWith(".csv")) {
                    TreeNode fileNode = new TreeNode(archivo.getName(), (i + 1) + "-" + (j + 1), null, false);
                    dirNode.getChildren().add(fileNode);
                }
            }

            root.getChildren().add(dirNode);
        }

        return root;
    }
}
