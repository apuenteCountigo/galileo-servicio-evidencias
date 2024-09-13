package com.galileo.cu.servicioevidencias.servicios;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Array;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.persistence.EntityManager;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galileo.cu.commons.models.Balizas;
import com.galileo.cu.commons.models.Conexiones;
import com.galileo.cu.commons.models.HistoricoObjetivosBalizas;
import com.galileo.cu.commons.models.Objetivos;
import com.galileo.cu.commons.models.Operaciones;
import com.galileo.cu.commons.models.Posiciones;
import com.galileo.cu.commons.models.Progresos;
import com.galileo.cu.commons.models.Usuarios;
import com.galileo.cu.commons.models.dto.JwtObjectMap;
import com.galileo.cu.servicioevidencias.clientes.Dataminer;
import com.galileo.cu.servicioevidencias.repositorios.ActaRepository;
import com.galileo.cu.servicioevidencias.repositorios.ConexionesRepository;
import com.galileo.cu.servicioevidencias.repositorios.EvidenciaRepository;
import com.galileo.cu.servicioevidencias.repositorios.HistoricoObjetivosBalizasRepository;
import com.galileo.cu.servicioevidencias.repositorios.PosicionesRepository;
import com.galileo.cu.servicioevidencias.repositorios.ProgEvidens;
import com.galileo.cu.servicioevidencias.repositorios.ProgresosRepository;
import com.galileo.cu.servicioevidencias.repositorios.UsuariosRepository;
import com.google.common.base.Strings;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EvidenciaServiceImpl implements EvidenciaService {
    @Autowired
    EntityManager em;

    @Autowired
    PosicionesRepository posRepo;

    @Autowired
    ConexionesRepository conRepo;

    @Autowired
    EvidenciaRepository eviRepo;

    @Autowired
    ActaRepository actaRepo;

    @Autowired
    Dataminer dataminer;

    @Autowired
    ObjectMapper objMap;

    @Autowired
    ProgresosRepository proRep;

    @Autowired
    UsuariosRepository usuRepo;

    @Autowired
    HistoricoObjetivosBalizasRepository hobjbalRepo;

    public String body = "";
    public String csvContent = "";
    public FTPClient ftp;

    int totalObj = 0;
    int porcientoActual = 0;
    int incremento = 0;

    @Override
    public void GenerarKML(List<Objetivos> objs,
            String tipoPrecision,
            String fechaInicio,
            String fechaFin,
            String token) {

        log.info("Fecha Inicio 2-GenerarKML:: " + fechaInicio);
        log.info("Fecha Fin 2-GenerarKML:: " + fechaFin);

        JwtObjectMap autenticado;
        Usuarios usu;
        String pathOperacion;
        String baseDir;
        String currentDir;

        try {
            autenticado = TomarAutenticado(token);
            usu = usuRepo.findById(Long.valueOf(autenticado.getId())).get();
        } catch (Exception e) {
            log.error("Fallo Descomponiendo el Token, es Inválido: " + e.getMessage());
            throw new RuntimeException("Fallo Descomponiendo el Token, es Inválido");
        }

        // Iniciando Progreso de evidencias
        if (!ProgEvidens.progEvi.isEmpty() && ProgEvidens.progEvi.containsKey(usu.getId())) {
            eviRepo.EliminarProgEvidens(usu.getId());
            // ProgEvidens.progEvi.remove(usu.getId());
        }

        // Inicializando Progreso
        ProgEvidens.progEvi.put(usu.getId(), 0);
        ProgEvidens.ficherosPendientes.put(usu.getId(), new ArrayList<String>());
        ProgEvidens.advertencias.put(usu.getId(), new String());
        ProgEvidens.operacion.put(usu.getId(), new Operaciones());
        ProgEvidens.zipPendiente.put(usu.getId(), "");

        // Inbuilt format
        // DateTimeFormatter format = DateTimeFormatter.ISO_DATE_TIME;
        // Custom format if needed
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        /*
         * String fi = fechaInicio.format(formatter).replace(":", "_");
         * String ff = fechaFin.format(formatter).replace(":", "_");
         */
        String fi = fechaInicio.replace(":", "_");
        String ff = fechaFin.replace(":", "_");
        String fechaI = new SimpleDateFormat("yyyy-MM-dd").format(objs.get(0).getOperaciones().getFechaInicio());
        String fechaF = new SimpleDateFormat("yyyy-MM-dd").format(objs.get(0).getOperaciones().getFechaFin());

        try {
            if (ProgEvidens.ftp == null || !ProgEvidens.ftp.isConnected())
                ProgEvidens.ftp = ConectarFTP(usu.getId());
        } catch (Exception e) {
            if (e.getMessage().contains("Fallo")) {
                throw new RuntimeException(e.getMessage());
            } else {
                throw new RuntimeException("Fallo Conectando al FTP");
            }
        }

        try {
            baseDir = ProgEvidens.ftp.printWorkingDirectory();
            currentDir = baseDir;
            currentDir = currentDir + "/UNIDADES";
            currentDir = currentDir.replace("//", "/");
            ProgEvidens.ftp.changeWorkingDirectory(currentDir + "/"
                    + objs.get(0).getOperaciones().getUnidades().getDenominacion()
                    + "/INFORMES " + objs.get(0).getOperaciones().getDescripcion()
                    + "/PERSONALIZADOS/");

            ProgEvidens.ftp
                    .mkd(objs.get(0).getOperaciones().getDescripcion()
                            + "(" + fi.replace("T", " ") + "-"
                            + ff.replace("T", " ") + ")");

            log.info("Directorio Actual L171: " + ProgEvidens.ftp.printWorkingDirectory());

            pathOperacion = currentDir + "/" + objs.get(0).getOperaciones().getUnidades().getDenominacion()
                    + "/INFORMES "
                    + objs.get(0).getOperaciones().getDescripcion() + "/PERSONALIZADOS/"
                    + objs.get(0).getOperaciones().getDescripcion() + "(" + fi.replace("T", " ") + "-"
                    + ff.replace("T", " ") + ")/";

            ProgEvidens.ftp.changeWorkingDirectory(pathOperacion);
            ProgEvidens.ftp.mkd("KMLS");

            log.info("Directorio Actual L182: " + ProgEvidens.ftp.printWorkingDirectory());

            if (ProgEvidens.ftp.isConnected()) {
                try {
                    ProgEvidens.ftp.disconnect();
                } catch (IOException er) {
                    log.error("Error desconectando ftp");
                }
            }
        } catch (Exception e) {
            eviRepo.EliminarProgEvidens(usu.getId());
            /*
             * ProgEvidens.progEvi.remove(usu.getId());
             * ProgEvidens.ficherosPendientes.remove(usu.getId());
             */
            log.error("Fallo Creando Carpetas en FTP " + e.getMessage());
            throw new RuntimeException("Fallo Creando Carpetas en FTP");
        }

        String pendientesFirma[] = { "" };

        totalObj = objs.size();
        incremento = 90 / (totalObj * 2);

        log.info("Total Objetivos: " + totalObj);
        log.info("incremento = 100 / (totalObj + 2): " + incremento);

        objs.forEach((Objetivos obj) -> {

            List<Posiciones> pos;
            try {
                String typePrecision = "GPS";
                String signo = "=";
                if (!tipoPrecision.equals("GPS")) {
                    signo = "<>";
                    typePrecision = "''";
                }
                String fIni = fechaInicio.replace('T', ' ');
                String fFin = fechaFin.replace('T', ' ');
                DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                pos = posRepo.tomarPosiciones(obj.getOperaciones().getUnidades().getId().toString(),
                        obj.getDescripcion(), LocalDateTime.parse(fIni, format),
                        LocalDateTime.parse(fFin, format), typePrecision, signo);
            } catch (Exception e) {
                log.error(
                        "Fallo en la Generación de la Evidencia, Tomando las Posiciones en la BD, Causado por: "
                                + e.getMessage(),
                        e);
                eviRepo.EliminarProgEvidens(usu.getId());
                throw new RuntimeException("Fallo en la Generación de la Evidencia, Tomando las Posiciones en la BD");
            }

            int por = ProgEvidens.progEvi.get(usu.getId());
            ProgEvidens.progEvi.replace(usu.getId(), por + incremento);

            try {
                pendientesFirma[0] += eviRepo.BuildFiles(obj, pos, tipoPrecision, fi, ff, pathOperacion,
                        autenticado.getTip(), usu.getId(), incremento);
            } catch (Exception e) {
                eviRepo.EliminarProgEvidens(usu.getId());
                log.error("Fallo en la Generación de la Evidencia, Construyendo Ficheros KML y CSV, Causado por: ",
                        e.getMessage());
                throw new RuntimeException("Fallo en la Generación de la Evidencia, " + e.getMessage());
            }

            por = ProgEvidens.progEvi.get(usu.getId());
            ProgEvidens.progEvi.replace(usu.getId(), por + incremento);

            /*
             * try {
             * pro[0].setValor(pro[0].getValor() + incremento);
             * proRep.save(pro[0]);
             * log.info("Progreso..." + pro[0].getValor());
             * } catch (Exception e) {
             * proRep.delete(pro[0]);
             * log.error("Fallo Insertando Progreso de Evidencias: ", e.getMessage());
             * throw new RuntimeException("Fallo Insertando Progreso de Evidencias");
             * }
             */

        });

        if (!ProgEvidens.ficherosPendientes.isEmpty() && ProgEvidens.ficherosPendientes.size() > 0
                && ProgEvidens.ficherosPendientes.containsKey(usu.getId())) {

            int inc = 4 / ProgEvidens.ficherosPendientes.size();

            ProgEvidens.ficherosPendientes.get(usu.getId()).forEach((v) -> {
                log.info(v);
                String[] arrVal = null;

                if (ProgEvidens.ftp == null || !ProgEvidens.ftp.isConnected()) {
                    try {
                        ProgEvidens.ftp = ConectarFTP(usu.getId());
                    } catch (Exception e1) {
                        eviRepo.EliminarProgEvidens(usu.getId());
                        Desconectar(ProgEvidens.ftp);
                        log.error("Fallo intentando conectar FTP para subir ficheros pendientes " + e1.getMessage());
                        throw new RuntimeException("Fallo Conectando al FTP");
                    }
                }

                if (v.contains("®")) {
                    arrVal = v.split("®");
                    try {
                        ProgEvidens.ftp.changeWorkingDirectory(baseDir);
                        ProgEvidens.ftp.mkd(pathOperacion + arrVal[0]);
                        FilesUpload(arrVal[1], pathOperacion + arrVal[0]);
                    } catch (IOException e) {
                        eviRepo.EliminarProgEvidens(usu.getId());
                        log.error("Fallo al crear directorio o subir " + pathOperacion + arrVal[0]);
                        log.error(e.getMessage());
                        Desconectar(ProgEvidens.ftp);
                        throw new RuntimeException(e.getMessage());
                    }
                    v = v.replace(arrVal[0] + "®", "");
                } else {
                    try {
                        FilesUpload(v, pathOperacion + "/KMLS");
                    } catch (Exception e) {
                        eviRepo.EliminarProgEvidens(usu.getId());
                        log.error(e.getMessage());
                        Desconectar(ProgEvidens.ftp);
                        throw new RuntimeException(e.getMessage());
                    }
                }
                int vl = ProgEvidens.progEvi.get(usu.getId());
                ProgEvidens.progEvi.replace(usu.getId(), vl + inc);
            });
            ProgEvidens.progEvi.replace(usu.getId(), 94);
            if (ProgEvidens.ftp != null && ProgEvidens.ftp.isConnected()) {
                Desconectar(ProgEvidens.ftp);
            }
        }

        if (pendientesFirma.length > 0 && !pendientesFirma[0].isEmpty() && objs != null && objs.size() > 0) {
            Objetivos obj = objs.get(0);
            try {
                dataminer.enviarNombresCSV(Integer.valueOf(obj.getOperaciones().getIdDataminer()),
                        Integer.valueOf(obj.getOperaciones().getIdElement()), pendientesFirma[0]);
                log.info("Se envio los nombres de fichero a firmar correctamente");
                ProgEvidens.operacion.replace(usu.getId(), obj.getOperaciones());
            } catch (Exception e) {
                log.error("Fallo Enviando a Dataminer Nombres de Ficheros a Firmar: ", e.getMessage());
                log.info("pendientesFirma=" + pendientesFirma[0]);
                eviRepo.EliminarProgEvidens(usu.getId());
                throw new RuntimeException("Fallo Enviando a Dataminer Nombres de Ficheros a Firmar");
            }

            ProgEvidens.progEvi.replace(usu.getId(), 95);
        }

        // Eliminar Ficheros Basura
        /*
         * String f = "";
         * String pendientesFirma = "";
         * try {
         * List<String> files = ListarFicheros("./", 1);
         * for (String file : files) {
         * f = file;
         * if (file.contains(".kml") || file.contains(".csv") || file.contains(".pdf")
         * || file.contains(".zip")) {
         * File fichero = new File(file);
         * if (fichero.delete()) {
         * System.out.println("Fue Eliminado el Fichero: " + file);
         * } else {
         * System.out.println("No se Pudo Eliminar el Fichero: " + file);
         * }
         * }
         * 
         * if (file.contains(".csv")) {
         * if (pendientesFirma == "")
         * pendientesFirma = file;
         * else
         * pendientesFirma = "," + file;
         * }
         * }
         * } catch (Exception e) {
         * proRep.delete(pro);
         * System.out.println("Fallo en la Eliminación del Fichero " + f + ": " +
         * e.getMessage());
         * throw new RuntimeException("Fallo en la Eliminación del Fichero: " + f);
         * }
         * try {
         * pro[0].setValor(pro[0].getValor() + incremento);
         * proRep.save(pro);
         * } catch (Exception e) {
         * proRep.delete(pro);
         * System.out.println("Fallo Insertando Progreso de Evidencias: " +
         * e.getMessage());
         * throw new RuntimeException("Fallo Insertando Progreso de Evidencias");
         * }
         * 
         * try {
         * if (pendientesFirma != "" && objs != null && objs.size() > 0) {
         * Objetivos obj = objs.get(1);
         * dataminer.enviarNombresCSV(Integer.valueOf(obj.getOperaciones().
         * getIdDataminer()),
         * Integer.valueOf(obj.getOperaciones().getIdElement()), pendientesFirma);
         * }
         * } catch (Exception e) {
         * proRep.delete(pro);
         * System.out.println("Fallo el Envio al Dataminer de Nombres para Firmar: " +
         * e.getMessage());
         * throw new
         * RuntimeException("Fallo el Envio al Dataminer de Nombres para Firmar");
         * }
         * try {
         * pro[0].setValor(pro[0].getValor() + incremento);
         * proRep.save(pro);
         * } catch (Exception e) {
         * proRep.delete(pro);
         * System.out.println("Fallo Insertando Progreso de Evidencias: " +
         * e.getMessage());
         * throw new RuntimeException("Fallo Insertando Progreso de Evidencias");
         * }
         */
    }

    private void FilesUpload(String nombre, String camino) {
        FileInputStream fis = null;

        boolean si = false;
        try {
            si = ProgEvidens.ftp.changeWorkingDirectory(camino);
        } catch (IOException e) {
            Desconectar(ProgEvidens.ftp);
            EliminarFichero(nombre);
            log.error("Fallo al Generar Evidencias, Cambiando al Directorio " + camino, e);
            throw new RuntimeException("Fallo al Generar Evidencias, Cambiando al Directorio " + camino);
        }

        if (!si) {
            Desconectar(ProgEvidens.ftp);
            EliminarFichero(nombre);
            log.error("Fallo al Generar Evidencias, Cambiando al Directorio " + camino);
            throw new RuntimeException("Fallo al Generar Evidencias, Cambiando al Directorio " + camino);
        }

        try {
            ProgEvidens.ftp.setBufferSize(2048);
            ProgEvidens.ftp.enterLocalPassiveMode();
            ProgEvidens.ftp.setFileType(FTP.BINARY_FILE_TYPE);

            fis = new FileInputStream(nombre);
            boolean uploadFile = ProgEvidens.ftp.storeFile(nombre, fis);
            if (!uploadFile) {
                Desconectar(ProgEvidens.ftp);
                fis.close();
                EliminarFichero(nombre);
                log.error("Fallo al Generar Evidencias, Subiendo el Fichero " + nombre + " al FTP ");
                throw new RuntimeException("Fallo al Generar Evidencias, Subiendo el Fichero " + nombre + " al FTP ");
            } else {
                fis.close();
                EliminarFichero(nombre);
            }
        } catch (Exception e) {
            log.error("Fallo al Generar Evidencias, Subiendo el Fichero " + nombre + " al FTP ", e);

            if (fis != null) {
                try {
                    fis.close();
                    EliminarFichero(nombre);
                } catch (IOException er) {
                    Desconectar(ProgEvidens.ftp);
                    EliminarFichero(nombre);
                    log.error("Fallo al Generar Evidencias, Subiendo el Fichero " + nombre + " al FTP ", er);
                    throw new RuntimeException(
                            "Fallo al Generar Evidencias, Subiendo el Fichero " + nombre + " al FTP ");
                }
            }

            Desconectar(ProgEvidens.ftp);
            log.error("Fallo al Generar Evidencias, Subiendo el Fichero " + nombre + " al FTP ");
            throw new RuntimeException("Fallo al Generar Evidencias, Subiendo el Fichero " + nombre + " al FTP ");
        }
    }

    private void EliminarFichero(String nombre) {
        File fichero = new File(nombre);
        if (fichero.delete()) {
            log.info("Fue Eliminado el Fichero: " + nombre);
        } else {
            log.info("No se Pudo Eliminar el Fichero: " + nombre);
        }
    }

    public List<String> ListarFicheros(String dir, int depth) throws IOException {
        try (Stream<Path> stream = Files.walk(Paths.get(dir), depth)) {
            return stream
                    .filter(file -> !Files.isDirectory(file))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toList());
        }
    }

    public JwtObjectMap TomarAutenticado(String token) throws Exception {
        JwtObjectMap jwtObjectMap;

        try {
            String[] chunks = token.split("\\.");
            Base64.Decoder decoder = Base64.getUrlDecoder();
            String header = new String(decoder.decode(chunks[0]));
            String payload = new String(decoder.decode(chunks[1]));

            jwtObjectMap = objMap.readValue(payload.toString().replace("Perfil", "perfil"),
                    JwtObjectMap.class);

            return jwtObjectMap;
        } catch (Exception e) {
            log.error("Fallo la Obtención del TIP, al descomponer el token", e.getMessage());
            throw new Exception("Fallo la Obtención del TIP, al descomponer el token");
        }
    }

    @Override
    public FTPClient ConectarFTP(long idAuth, Boolean... passiveMode) throws Exception {
        FTPClient ftp = new FTPClient();
        Conexiones con = conRepo.findFirstByServicioContaining("ftp");
        if (con == null) {
            if (idAuth > 0)
                eviRepo.EliminarProgEvidens(idAuth);
            log.error("Fallo, no Existe un Servicio entre las Conexiones, que Contenga la Palabra FTP");
            throw new Exception("Fallo, no Existe un Servicio entre las Conexiones, que Contenga la Palabra FTP");
        }
        try {
            ftp.connect(con.getIpServicio(),
                    Integer.parseInt(((!Strings.isNullOrEmpty(con.getPuerto())) ? con.getPuerto() : "21")));
        } catch (Exception e) {
            ftp = null;
            log.error("Fallo, conexión Fallida al servidor FTP " + con.getIpServicio() + ":" + con.getPuerto(), e);
            throw new IOException(
                    "Fallo, conexión Fallida al servidor FTP " + con.getIpServicio() + ":" + con.getPuerto());
        }
        int reply = ftp.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            ftp.disconnect();
            ftp = null;
            log.error("getReplyCode: Conexión Fallida al servidor FTP " + con.getIpServicio() + ":" + con.getPuerto());
            throw new IOException(
                    "Fallo, conexión Fallida al servidor FTP " + con.getIpServicio() + ":" + con.getPuerto());
        }

        boolean successLogin = ftp.login(con.getUsuario(), con.getPassword());
        int replyCode = ftp.getReplyCode();
        if (successLogin) {
            log.info("La autenticación fue satizfactoria.");
        } else {
            log.info("Fallo intentando la autenticación con el servidor ftp");
            Desconectar(ftp);
            throw new IOException("Fallo intentando la autenticación con el servidor ftp");
        }

        try {

            if (passiveMode != null && passiveMode.length > 0 && passiveMode[0]) {
                log.info("Creando conexión pasiva al ftp");
                ftp.enterLocalPassiveMode();
            }

            ftp.setControlKeepAliveTimeout(1000);
        } catch (Exception e) {
            ftp.disconnect();
            ftp = null;
            log.error("Usuario o Contraseña Incorrecto, al Intentar Autenticarse en Servidor FTP " + con.getIpServicio()
                    + ":" + con.getPuerto(), e);
            throw new IOException("Fallo, Usuario o Contraseña Incorrecto, al Intentar Autenticarse en Servidor FTP "
                    + con.getIpServicio() + ":" + con.getPuerto());
        }

        return ftp;
    }

    @Override
    public void Desconectar(FTPClient ftp) {
        try {
            if (ftp.isConnected()) {
                ftp.logout();
                ftp.disconnect();
                if (ftp != null && ftp.isConnected())
                    ftp = null;
            }
        } catch (IOException e) {
            log.error("Fallo Desconectando FTP: ", e.getMessage());
            throw new RuntimeException("Fallo Desconectando FTP");
        }
    }
}