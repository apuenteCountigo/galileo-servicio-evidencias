package com.galileo.cu.servicioevidencias.controladores;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galileo.cu.commons.models.Conexiones;
import com.galileo.cu.commons.models.Objetivos;
import com.galileo.cu.servicioevidencias.clientes.Dataminer;
import com.galileo.cu.servicioevidencias.dtos.DataminerObjectOutput;
import com.galileo.cu.servicioevidencias.dtos.PendientesFirma;
import com.galileo.cu.servicioevidencias.dtos.TreeNode;
import com.galileo.cu.servicioevidencias.repositorios.EvidenciaRepository;
import com.galileo.cu.servicioevidencias.repositorios.ProgEvidens;
import com.galileo.cu.servicioevidencias.repositorios.UsuariosRepository;
import com.galileo.cu.servicioevidencias.servicios.EvidenciaService;
import com.galileo.cu.servicioevidencias.servicios.FtpCsvService;
import com.galileo.cu.servicioevidencias.servicios.FtpZipService;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ExecutionError;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class EvidenciaController {
    @Autowired
    EvidenciaService eviServ;

    @Autowired
    UsuariosRepository usuRepo;

    @Autowired
    Dataminer dataminer;

    @Autowired
    ObjectMapper objMap;

    @Autowired
    EvidenciaRepository eviRepo;

    @Autowired
    FtpCsvService ftpCsv;

    @Autowired
    FtpZipService ftpZip;

    Dictionary errores = new Hashtable();

    DataminerObjectOutput dataMinerObj;

    @PostMapping("/generarKMLS")
    public ResponseEntity<String> generarKMLS(
            @RequestBody List<Objetivos> objs,
            @RequestParam("tipoPrecision") String tipoPrecision,
            @RequestHeader("Authorization") String token,
            @RequestParam("fechaInicio") String fechaInicio,
            @RequestParam("fechaFin") String fechaFin,
            @RequestParam("idAuth") long idAuth) {

        log.info("Fecha Inicio 1-generarKMLS:: " + fechaInicio);
        log.info("Fecha Fin 1-generarKMLS:: " + fechaFin);

        try {
            token = token.replace("Bearer ", "");
            eviServ.GenerarKML(objs, tipoPrecision, fechaInicio, fechaFin, token);
        } catch (Exception e) {
            log.error(e.getMessage());
            errores.put(Long.toString(idAuth), e.getMessage());
            return ResponseEntity.badRequest().body("{\"message\":\"" + e.getMessage() + "\"}");
        }

        return ResponseEntity.ok().body("{\"message\":\"Completado\"}");

    }

    @GetMapping("/toBuildPackage")
    public ResponseEntity<String> toBuildPackage(@RequestParam("idAuth") long idAuth) {
        if (ProgEvidens.progEvi != null
                && !ProgEvidens.progEvi.isEmpty()
                && ProgEvidens.progEvi.containsKey(idAuth)) {
            ProgEvidens.isBuildingPackage.replace(idAuth, true);
            return ResponseEntity.ok("{\"message\":\"Fue iniciada la construcción del paquete de evidencias\"}");
        }
        return ResponseEntity.badRequest()
                .body("{\"message\":\"Fallo, usted no tiene ningún procesamiento de evidencias pendiente\"}");
    }

    @GetMapping("/stopProgress")
    public ResponseEntity<String> stopProgress(@RequestParam("idAuth") long idAuth) {
        if (ProgEvidens.progEvi != null
                && !ProgEvidens.progEvi.isEmpty()
                && ProgEvidens.progEvi.containsKey(idAuth)
                && !ProgEvidens.isBuildingPackage.get(idAuth)
                && ProgEvidens.progEvi.get(idAuth) == 95) {
            errores.remove(Long.toString(idAuth));
            eviRepo.EliminarProgEvidens(idAuth);
            return ResponseEntity.ok("{\"message\":\"Fue detenida la generación de evidencias\"}");
        }
        return ResponseEntity.badRequest()
                .body("{\"message\":\"Fallo, usted no tiene ningún procesamiento de evidencias pendiente\"}");
    }

    @GetMapping("/progreso")
    public ResponseEntity<String> progreso(
            @RequestParam("idAuth") long idAuth) {
        if (!errores.isEmpty() && errores.get(Long.toString(idAuth)) != null) {
            String msg = errores.get(Long.toString(idAuth)).toString();
            errores.remove(Long.toString(idAuth));
            eviRepo.EliminarProgEvidens(idAuth);
            return ResponseEntity.badRequest().body("{\"message\":\"" + msg + "\"}");
        }
        try {
            int v = 0;
            if (ProgEvidens.progEvi != null && !ProgEvidens.progEvi.isEmpty()
                    && ProgEvidens.progEvi.containsKey(idAuth)) {
                v = ProgEvidens.progEvi.get(idAuth);

                // En el 95% se inicia el testeo del parámetro 3001, en espera del ZIP
                if (v == 95) {
                    if (ProgEvidens.isBuildingPackage.get(idAuth)) {
                        PendientesFirma pf = ProgEvidens.pendientesFirma.get(idAuth);
                        try {
                            dataminer.enviarNombresCSV(pf.idDMA, pf.idElement, pf.ficheros);
                            ProgEvidens.progEvi.replace(idAuth, 96);
                        } catch (Exception e) {
                            String err = "Fallo, enviando a DMA, nombres de ficheros a firmar";
                            log.error(err, e);
                            new RuntimeException(err);
                        }
                    }
                } else if (v == 96) {
                    if (ProgEvidens.isBuildingPackage.get(idAuth)) {
                        try {
                            String zip = dataminer.testZip(
                                    Integer.valueOf(ProgEvidens.operacion.get(idAuth).getIdDataminer()),
                                    Integer.valueOf(ProgEvidens.operacion.get(idAuth).getIdElement()));
                            log.info("-*****Se consultó el parámetro 3001 de la operación: "
                                    + ProgEvidens.operacion.get(idAuth).getDescripcion());
                            log.info("-*****Valor del parámetro 3001== " + zip);

                            if (!Strings.isNullOrEmpty(zip)) {
                                log.info("-*****Parámetro 3001 con valores ");
                                String ls = zip.split("\"Value\":\"")[1].split("\"")[0];
                                log.info("ls con split=" + ls);
                                if (!Strings.isNullOrEmpty(ls)) {
                                    log.info("zip Value con valor=" + ls);
                                    ProgEvidens.progEvi.replace(idAuth, 100);
                                    ProgEvidens.zipPendiente.replace(idAuth, ls);
                                    log.info("ProgEvidens.zipPendiente==" + ProgEvidens.zipPendiente.get(idAuth));
                                } else {
                                    log.info("zip Value sin valor");
                                }
                            }
                        } catch (Exception e) {
                            log.error("Fallo consultando en Dataminer el parámetro 3001 de la operación "
                                    + e.getMessage());
                            eviRepo.EliminarProgEvidens(idAuth);
                            throw new RuntimeException(
                                    "Fallo consultando en Dataminer el parámetro 3001 de la operación");
                        }
                    }
                } else if (v == 100) {
                    ProgEvidens.isBuildingPackage.replace(idAuth, false);
                    if (!ProgEvidens.advertencias.get(idAuth).isEmpty() && ProgEvidens.advertencias.get(idAuth) != "") {
                        String msg = "Fallo, la evidencia fue completada con errores. Los siguientes objetivos no tienen balizas ni posiciones";
                        msg += ProgEvidens.advertencias.get(idAuth);

                        if (!Strings.isNullOrEmpty(ProgEvidens.zipPendiente.get(idAuth)))
                            eviRepo.EliminarProgEvidens(idAuth, true);
                        else
                            eviRepo.EliminarProgEvidens(idAuth);

                        return ResponseEntity.badRequest().body("{\"message\":\"" + msg + "\"}");
                    }

                    log.info("100%% ProgEvidens.zipPendiente==" + ProgEvidens.zipPendiente.get(idAuth));

                    if (!Strings.isNullOrEmpty(ProgEvidens.zipPendiente.get(idAuth)))
                        eviRepo.EliminarProgEvidens(idAuth, true);
                    else
                        eviRepo.EliminarProgEvidens(idAuth);
                }
                return ResponseEntity.ok("{\"valor\":\"" + v + "\"}");
            }
        } catch (Exception e) {
            log.error("ERROR, Consultando el Progreso: " + e.getMessage());
            eviRepo.EliminarProgEvidens(idAuth);
            return ResponseEntity.badRequest().body("{\"message\":\"Fallo Consultando el Progreso de la Evidencia\"}");
        }
        return ResponseEntity.badRequest()
                .body("{\"message\":\"Fallo desconocido analizando el progreso de la evidencia\"}");
    }

    @GetMapping("/listCSV")
    public Page<TreeNode> listCSV(
            @RequestParam(defaultValue = "") String unidadName,
            @RequestParam(defaultValue = "") String objetivoName,
            @RequestParam(defaultValue = "") String operacionName,
            @RequestParam(defaultValue = "") String fechaInicio,
            @RequestParam(defaultValue = "") String fechaFin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,asc") String[] sort) throws IOException {
        if (StringUtils.isEmpty(unidadName) ||
                StringUtils.isEmpty(operacionName) ||
                StringUtils.isEmpty(fechaInicio) ||
                StringUtils.isEmpty(fechaFin)) {
            String err = "Fallo, faltan parámetros en la petición";
            log.error("{}, intentando listar los csv", err);
            throw new ExecutionError(err, null);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(sort));
        // ftpCsv.listCsvFiles(pageable);
        return ftpCsv.listCsvFiles(pageable, unidadName, operacionName, fechaInicio, fechaFin);
        // return ResponseEntity.ok("downloadCSV");
    }

    @GetMapping("/downloadCSV/{fileName}")
    public ResponseEntity<InputStreamResource> downloadCSV(@RequestParam String path, @PathVariable String fileName)
            throws IOException {
        try {
            InputStream fileStream = ftpCsv.downloadFileAsStream(path, fileName);
            InputStreamResource resource = new InputStreamResource(fileStream);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName);

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (IOException e) {
            if (e.getMessage().contains("Fallo")) {
                throw new IOException(e.getMessage());
            }
            String err = "Fallo general descargando el fichero: ";
            log.error(err, e);
            throw new IOException(err);
        }
    }

    @GetMapping("/listZIP")
    public Page<TreeNode> listZIP(
            @RequestParam(defaultValue = "") String unidadName,
            @RequestParam(defaultValue = "") String objetivoName,
            @RequestParam(defaultValue = "") String operacionName,
            @RequestParam(defaultValue = "") String fechaInicio,
            @RequestParam(defaultValue = "") String fechaFin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id,asc") String[] sort) throws IOException {
        if (StringUtils.isEmpty(unidadName) ||
                StringUtils.isEmpty(operacionName) ||
                StringUtils.isEmpty(fechaInicio) ||
                StringUtils.isEmpty(fechaFin)) {
            String err = "Fallo, faltan parámetros en la petición";
            log.error("{}, intentando listar los zip", err);
            throw new ExecutionError(err, null);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(sort));
        // ftpCsv.listCsvFiles(pageable);
        return ftpZip.listZipFiles(pageable, unidadName, operacionName, fechaInicio, fechaFin);
        // return ResponseEntity.ok("downloadCSV");
    }

    @GetMapping("/pathZip")
    public ResponseEntity<String> pathZip(
            @RequestParam("idAuth") long idAuth) {
        return ResponseEntity.ok("{\"path\":\"" + ProgEvidens.zipPendiente.get(idAuth) + "\"}");
    }

    @RequestMapping(value = "/zip", method = RequestMethod.GET, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public void downloadFile(@RequestParam("idAuth") long idAuth, @RequestParam("zipPath") String zipPath,
            HttpServletResponse response) throws IOException {
        FTPClient ftp = new FTPClient();
        try {
            if (!ftp.isConnected()) {
                ftp = eviServ.ConectarFTP(idAuth, true);
            }

            if (ftp.isConnected())
                log.info("EvidenciaController FTP ZIP Conectado con exito");

        } catch (Exception e) {
            String msg = e.getMessage();
            if (!e.getMessage().contains("Fallo"))
                msg = "Fallo general conectando al FTP ZIP";

            log.info("@@@@ EvidenciaController /zip " + msg);
            response.sendError(500, "{\"message\":\"" + msg + "\"}");
        }

        // create object of Path
        Path path = Paths.get(zipPath);

        // call getFileName() and get FileName path object
        Path fileName = path.getFileName();

        log.info("EvidenciaController downloadFile Path=" + path + " filename=" + fileName);

        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName.toString() + "\"");

        int readBytes = 0;
        byte[] toDownload = new byte[100];

        OutputStream downloadStream = null;

        try {
            downloadStream = response.getOutputStream();
        } catch (IOException e) {
            log.info("@@@@ EvidenciaController /zip Error downloadStream = response.getOutputStream() "
                    + e.getMessage());
            response.sendError(500, "{\"message\":\"Fallo al intentar descargar el zip\"}");
        }

        ftp.setFileType(FTPClient.BINARY_FILE_TYPE);

        // Lee el fichero en el FTP como Stream
        InputStream ftpReader = null;

        try {
            ftpReader = ftp.retrieveFileStream(zipPath);
        } catch (IOException e) {
            log.info("@@@@ EvidenciaController /zip Error línea 276 Leyendo el fichero en ftp " + e.getMessage());
            if (e.getMessage().contains("FTP response 421 received")) {
                log.info("El FTP dio Error 421 Intentaremos reconectar");
                if (ftp != null && ftp.isConnected()) {
                    log.info("Se Procede a Desloguear y desconectar el FTP");
                    ftp.logout();
                    ftp.disconnect();
                }
                if (ftp == null || !ftp.isConnected()) {
                    log.info("Intentando reconectar FTP ZIP");
                    try {
                        ftp = eviServ.ConectarFTP(idAuth);
                        ftpReader = ftp.retrieveFileStream(zipPath);
                    } catch (Exception e1) {
                        if (downloadStream != null)
                            downloadStream.close();
                        log.info("Fallo intentando reconexión al FTP ZIP " + e1.getMessage());
                        response.sendError(500, "{\"message\":\"Fallo intentando reconexión al FTP\"}");
                    }
                }
            } else {
                if (downloadStream != null)
                    downloadStream.close();
                log.error("Fallo leyendo el fichero en el FTP " + e.getMessage());
                response.sendError(500, "{\"message\":\"Fallo leyendo el fichero en el FTP\"}");
            }
        }

        if (ftpReader == null) {
            if (downloadStream != null)
                downloadStream.close();
            log.error("@@@@ EvidenciaController ftpReader == null /zip Error Leyendo el fichero en ftp");
            response.sendError(500, "{\"message\":\"Fallo leyendo el fichero en el FTP\"}");
        }

        BufferedInputStream downloadFileIn = new BufferedInputStream(ftpReader, 100);

        try {
            while ((readBytes = downloadFileIn.read(toDownload, 0, 100)) != -1) {
                downloadStream.write(toDownload, 0, readBytes);
                downloadStream.flush();
            }
        } catch (Exception e) {
            if (downloadStream != null)
                downloadStream.close();
            if (downloadFileIn != null)
                downloadFileIn.close();
            if (ftpReader != null)
                ftpReader.close();
            log.info("@@@@ EvidenciaController while /zip Error Descargando el fichero " + e.getMessage());
            response.sendError(500, "{\"message\":\"Fallo descargando el fichero desde el FTP\"}");
        }
        if (downloadStream != null)
            downloadStream.close();
        if (downloadFileIn != null)
            downloadFileIn.close();
        if (ftpReader != null)
            ftpReader.close();

        if (ftp != null && ftp.isConnected()) {
            ftp.logout();
            ftp.disconnect();
        }

        eviRepo.EliminarProgEvidens(idAuth);
    }
}