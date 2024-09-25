package com.galileo.cu.servicioevidencias.controladores;

import java.io.BufferedInputStream;
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
import org.apache.commons.net.ftp.FTPClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galileo.cu.commons.models.Objetivos;
import com.galileo.cu.servicioevidencias.clientes.Dataminer;
import com.galileo.cu.servicioevidencias.dtos.PendientesFirma;
import com.galileo.cu.servicioevidencias.dtos.TreeNode;
import com.galileo.cu.servicioevidencias.repositorios.EvidenciaRepository;
import com.galileo.cu.servicioevidencias.repositorios.UsuariosRepository;
import com.galileo.cu.servicioevidencias.servicios.EvidenciaService;
import com.galileo.cu.servicioevidencias.servicios.FtpCsvService;
import com.galileo.cu.servicioevidencias.repositorios.ProgEvidens;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ExecutionError;

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

    Dictionary<String, String> errores = new Hashtable<>();

    @PostMapping("/generarKMLS")
    public ResponseEntity<String> generarKMLS(
            @RequestBody List<Objetivos> objs,
            @RequestParam("tipoPrecision") String tipoPrecision,
            @RequestHeader("Authorization") String token,
            @RequestParam("fechaInicio") String fechaInicio,
            @RequestParam("fechaFin") String fechaFin) {

        log.info("Fecha Inicio 1-generarKMLS:: " + fechaInicio);
        log.info("Fecha Fin 1-generarKMLS:: " + fechaFin);

        try {
            token = token.replace("Bearer ", "");
            eviServ.GenerarKML(objs, tipoPrecision, fechaInicio, fechaFin, token);
        } catch (Exception e) {
            log.error("Fallo al generar KML L82", e);
            // errores.put(token, e.getMessage());
            return ResponseEntity.badRequest().body("{\"message\":\"" + e.getMessage() + "\"}");
        }

        return ResponseEntity.ok().body("{\"message\":\"Completado\"}");
    }

    @GetMapping("/toBuildPackage")
    public ResponseEntity<String> toBuildPackage(@RequestHeader("Authorization") String token) {
        token = token.replace("Bearer ", "");
        if (ProgEvidens.progEvi != null
                && !ProgEvidens.progEvi.isEmpty()
                && ProgEvidens.progEvi.containsKey(token)) {
            ProgEvidens.isBuildingPackage.replace(token, true);
            return ResponseEntity.ok("{\"message\":\"Fue iniciada la construcción del paquete de evidencias\"}");
        }
        return ResponseEntity.badRequest()
                .body("{\"message\":\"Fallo, usted no tiene ningún procesamiento de evidencias pendiente\"}");
    }

    @GetMapping("/stopProgress")
    public ResponseEntity<String> stopProgress(@RequestHeader("Authorization") String token) {
        token = token.replace("Bearer ", "");
        if (ProgEvidens.progEvi != null
                && !ProgEvidens.progEvi.isEmpty()
                && ProgEvidens.progEvi.containsKey(token)
                && !ProgEvidens.isBuildingPackage.get(token)
                && ProgEvidens.progEvi.get(token) == 95) {
            errores.remove(token);
            eviRepo.EliminarProgEvidens(token);
            return ResponseEntity.ok("{\"message\":\"Fue detenida la generación de evidencias\"}");
        }
        return ResponseEntity.badRequest()
                .body("{\"message\":\"Fallo, usted no tiene ningún procesamiento de evidencias pendiente\"}");
    }

    @GetMapping("/progreso")
    public ResponseEntity<String> progreso(@RequestHeader("Authorization") String token) {
        token = token.replace("Bearer ", "");
        if (!errores.isEmpty() && errores.get(token) != null) {
            String msg = errores.get(token);
            errores.remove(token);
            eviRepo.EliminarProgEvidens(token);
            return ResponseEntity.badRequest().body("{\"message\":\"" + msg + "\"}");
        }
        try {
            int v = 0;
            if (ProgEvidens.progEvi != null && !ProgEvidens.progEvi.isEmpty()
                    && ProgEvidens.progEvi.containsKey(token)) {
                v = ProgEvidens.progEvi.get(token);

                // En el 95% se inicia el testeo del parámetro 3001, en espera del ZIP
                if (v == 95) {
                    if (ProgEvidens.isBuildingPackage.get(token)) {
                        PendientesFirma pf = ProgEvidens.pendientesFirma.get(token);
                        try {
                            dataminer.enviarNombresCSV(pf.getIdDMA(), pf.getIdElement(), pf.getFicheros());
                            ProgEvidens.progEvi.replace(token, 96);
                        } catch (Exception e) {
                            String err = "Fallo, enviando a DMA, nombres de ficheros a firmar";
                            log.error(err, e);
                            throw new RuntimeException(err);
                        }
                    }
                } else if (v == 96) {
                    if (ProgEvidens.isBuildingPackage.get(token)) {
                        try {
                            String zip = dataminer.testZip(
                                    Integer.valueOf(ProgEvidens.operacion.get(token).getIdDataminer()),
                                    Integer.valueOf(ProgEvidens.operacion.get(token).getIdElement()));
                            log.info("-*****Se consultó el parámetro 3001 de la operación: "
                                    + ProgEvidens.operacion.get(token).getDescripcion());
                            log.info("-*****Valor del parámetro 3001== " + zip);

                            if (!Strings.isNullOrEmpty(zip)) {
                                log.info("-*****Parámetro 3001 con valores ");
                                String ls = zip.split("\"Value\":\"")[1].split("\"")[0];
                                log.info("ls con split=" + ls);
                                if (!Strings.isNullOrEmpty(ls)) {
                                    log.info("zip Value con valor=" + ls);
                                    ProgEvidens.progEvi.replace(token, 100);
                                    ProgEvidens.zipPendiente.replace(token, ls);
                                    log.info("ProgEvidens.zipPendiente==" + ProgEvidens.zipPendiente.get(token));
                                } else {
                                    log.info("zip Value sin valor");
                                }
                            }
                        } catch (Exception e) {
                            log.error("Fallo consultando en Dataminer el parámetro 3001 de la operación "
                                    + e.getMessage());
                            eviRepo.EliminarProgEvidens(token);
                            throw new RuntimeException(
                                    "Fallo consultando en Dataminer el parámetro 3001 de la operación");
                        }
                    }
                } else if (v == 100) {
                    ProgEvidens.isBuildingPackage.replace(token, false);
                    if (!ProgEvidens.advertencias.get(token).isEmpty()) {
                        String msg = "Fallo, la evidencia fue completada con errores. Los siguientes objetivos no tienen balizas ni posiciones";
                        msg += ProgEvidens.advertencias.get(token);

                        if (!Strings.isNullOrEmpty(ProgEvidens.zipPendiente.get(token)))
                            eviRepo.EliminarProgEvidens(token, true);
                        else
                            eviRepo.EliminarProgEvidens(token);

                        return ResponseEntity.badRequest().body("{\"message\":\"" + msg + "\"}");
                    }

                    log.info("100% ProgEvidens.zipPendiente==" + ProgEvidens.zipPendiente.get(token));

                    if (!Strings.isNullOrEmpty(ProgEvidens.zipPendiente.get(token)))
                        eviRepo.EliminarProgEvidens(token, true);
                    else
                        eviRepo.EliminarProgEvidens(token);
                }
                return ResponseEntity.ok("{\"valor\":\"" + v + "\"}");
            }
        } catch (Exception e) {
            log.error("ERROR, Consultando el Progreso: " + e.getMessage());
            eviRepo.EliminarProgEvidens(token);
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
        return ftpCsv.listCsvFiles(pageable, unidadName, operacionName, fechaInicio, fechaFin);
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

    @GetMapping("/pathZip")
    public ResponseEntity<String> pathZip(@RequestHeader("Authorization") String token) {
        token = token.replace("Bearer ", "");
        return ResponseEntity.ok("{\"path\":\"" + ProgEvidens.zipPendiente.get(token) + "\"}");
    }

    @RequestMapping(value = "/zip", method = RequestMethod.GET, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public void downloadFile(@RequestHeader("Authorization") String token, @RequestParam("zipPath") String zipPath,
            HttpServletResponse response) throws IOException {
        token = token.replace("Bearer ", "");
        FTPClient ftp = new FTPClient();
        try {
            if (!ftp.isConnected()) {
                ftp = eviServ.ConectarFTP(token, true);
            }

            if (ftp.isConnected())
                log.info("EvidenciaController FTP ZIP Conectado con éxito");

        } catch (Exception e) {
            String msg = e.getMessage();
            if (!e.getMessage().contains("Fallo"))
                msg = "Fallo general conectando al FTP ZIP";

            log.info("@@@@ EvidenciaController /zip " + msg);
            response.sendError(500, "{\"message\":\"" + msg + "\"}");
            return;
        }

        Path path = Paths.get(zipPath);
        Path fileName = path.getFileName();

        log.info("EvidenciaController downloadFile Path=" + path + " filename=" + fileName);

        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName.toString() + "\"");

        int readBytes = 0;
        byte[] toDownload = new byte[100];

        OutputStream downloadStream = null;

        try {
            downloadStream = response.getOutputStream();
        } catch (IOException e) {
            log.info("@@@@ EvidenciaController /zip Error al obtener OutputStream: " + e.getMessage());
            response.sendError(500, "{\"message\":\"Fallo al intentar descargar el zip\"}");
            return;
        }

        ftp.setFileType(FTPClient.BINARY_FILE_TYPE);

        InputStream ftpReader = null;

        try {
            ftpReader = ftp.retrieveFileStream(zipPath);
        } catch (IOException e) {
            log.info("@@@@ EvidenciaController /zip Error al leer el fichero en ftp: " + e.getMessage());
            if (e.getMessage().contains("FTP response 421 received")) {
                log.info("El FTP dio Error 421. Intentando reconectar...");
                if (ftp != null && ftp.isConnected()) {
                    log.info("Procediendo a desconectar el FTP");
                    ftp.logout();
                    ftp.disconnect();
                }
                if (ftp == null || !ftp.isConnected()) {
                    log.info("Intentando reconectar FTP ZIP");
                    try {
                        ftp = eviServ.ConectarFTP(token);
                        ftpReader = ftp.retrieveFileStream(zipPath);
                    } catch (Exception e1) {
                        if (downloadStream != null)
                            downloadStream.close();
                        log.info("Fallo intentando reconexión al FTP ZIP " + e1.getMessage());
                        response.sendError(500, "{\"message\":\"Fallo intentando reconexión al FTP\"}");
                        return;
                    }
                }
            } else {
                if (downloadStream != null)
                    downloadStream.close();
                log.error("Fallo leyendo el fichero en el FTP " + e.getMessage());
                response.sendError(500, "{\"message\":\"Fallo leyendo el fichero en el FTP\"}");
                return;
            }
        }

        if (ftpReader == null) {
            if (downloadStream != null)
                downloadStream.close();
            log.error("@@@@ EvidenciaController ftpReader == null /zip Error leyendo el fichero en ftp");
            response.sendError(500, "{\"message\":\"Fallo leyendo el fichero en el FTP\"}");
            return;
        }

        try (BufferedInputStream downloadFileIn = new BufferedInputStream(ftpReader, 100)) {
            while ((readBytes = downloadFileIn.read(toDownload, 0, 100)) != -1) {
                downloadStream.write(toDownload, 0, readBytes);
                downloadStream.flush();
            }
        } catch (Exception e) {
            if (downloadStream != null)
                downloadStream.close();
            if (ftpReader != null)
                ftpReader.close();
            log.info("@@@@ EvidenciaController /zip Error descargando el fichero " + e.getMessage());
            response.sendError(500, "{\"message\":\"Fallo descargando el fichero desde el FTP\"}");
            return;
        } finally {
            if (downloadStream != null)
                downloadStream.close();
            if (ftpReader != null)
                ftpReader.close();
            if (ftp != null && ftp.isConnected()) {
                ftp.logout();
                ftp.disconnect();
            }
            eviRepo.EliminarProgEvidens(token);
        }
    }
}