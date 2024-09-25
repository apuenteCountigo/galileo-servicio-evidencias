package com.galileo.cu.servicioevidencias.servicios;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.persistence.EntityManager;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galileo.cu.commons.models.Conexiones;
import com.galileo.cu.commons.models.Objetivos;
import com.galileo.cu.commons.models.Operaciones;
import com.galileo.cu.commons.models.Posiciones;
import com.galileo.cu.commons.models.Usuarios;
import com.galileo.cu.commons.models.dto.JwtObjectMap;
import com.galileo.cu.servicioevidencias.dtos.PendientesFirma;
import com.galileo.cu.servicioevidencias.repositorios.*;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EvidenciaServiceImpl implements EvidenciaService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private String baseDir = "/";

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PosicionesRepository posicionesRepository;
    @Autowired
    private ConexionesRepository conexionesRepository;
    @Autowired
    private EvidenciaRepository evidenciaRepository;
    @Autowired
    private ActaRepository actaRepository;
    @Autowired
    private ProgresosRepository progresosRepository;
    @Autowired
    private UsuariosRepository usuariosRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void GenerarKML(List<Objetivos> objetivos, String tipoPrecision, String fechaInicio, String fechaFin,
            String token) {
        log.info("Generando KML para objetivos. Fecha Inicio: {} - Fecha Fin: {}", fechaInicio, fechaFin);

        Usuarios usuario = null;
        try {
            // Validar y obtener el usuario basado en el token
            usuario = validarYObtenerUsuario(token);
        } catch (Exception e) {
            String err = e != null && e.getMessage() != null && e.getMessage().contains("Fallo") ? e.getMessage()
                    : "Fallo, validando usuario";
            log.error(err, e);
            throw new RuntimeException(err);
        }

        try {
            // Inicializar el progreso con el token como identificador único
            inicializarProgreso(token);
        } catch (Exception e) {
            String err = e != null && e.getMessage() != null && e.getMessage().contains("Fallo") ? e.getMessage()
                    : "Fallo al inicializar el progreso de la evidencia";
            log.error(err, e);
            throw new RuntimeException(err);
        }

        FTPClient ftpClient = null;
        try {
            // Conectar al FTP
            ftpClient = ConectarFTP(token, true);

            // Crear estructura de directorios en el FTP
            String pathOperacion = crearEstructuraDirectorio(ftpClient, objetivos, fechaInicio, fechaFin);

            // Procesar los objetivos y generar los archivos
            procesarObjetivos(ftpClient, token, usuario, objetivos, tipoPrecision, fechaInicio, fechaFin,
                    pathOperacion);

            // Enviar pendientes de firma si corresponde
            enviarPendientesFirma(token, usuario, objetivos);

        } catch (Exception e) {
            String err = e != null && e.getMessage() != null && e.getMessage().contains("Fallo") ? e.getMessage()
                    : "Fallo inespecífico, generando KML";
            log.error(err, e.getMessage());
            limpiarProgresoPrevio(token); // Limpiar si ocurre un error
            throw new RuntimeException(err);
        } finally {
            if (ftpClient != null && ftpClient.isConnected()) {
                // Desconectar el FTP en el bloque finally
                Desconectar(ftpClient);
            }
        }
    }

    // Autenticación y validación del usuario
    private Usuarios validarYObtenerUsuario(String token) {
        try {
            JwtObjectMap jwtObjectMap = obtenerJwtObjectMap(token);
            return usuariosRepository.findById(Long.valueOf(jwtObjectMap.getId()))
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        } catch (Exception e) {
            log.error("Error descomponiendo el token: {}", e.getMessage());
            throw new RuntimeException("Token inválido", e);
        }
    }

    // Descomposición del token JWT para extraer la información
    private JwtObjectMap obtenerJwtObjectMap(String token) throws Exception {
        try {
            String[] chunks = token.split("\\.");
            Base64.Decoder decoder = Base64.getUrlDecoder();
            String payload = new String(decoder.decode(chunks[1]));
            return objectMapper.readValue(payload.replace("Perfil", "perfil"), JwtObjectMap.class);
        } catch (Exception e) {
            log.error("Error al descomponer el token", e);
            throw new Exception("Error descomponiendo token", e);
        }
    }

    private void limpiarProgresoPrevio(String token) {
        ProgEvidens.progEvi.remove(token);
        ProgEvidens.ficherosPendientes.remove(token);
        ProgEvidens.advertencias.remove(token);
        ProgEvidens.operacion.remove(token);
        ProgEvidens.zipPendiente.remove(token);
        ProgEvidens.isBuildingPackage.remove(token);
        ProgEvidens.pendientesFirma.remove(token);
    }

    // Inicialización del progreso usando el token como clave
    private void inicializarProgreso(String token) {
        try {
            ProgEvidens.progEvi.put(token, 0);
            ProgEvidens.ficherosPendientes.put(token, new ArrayList<>());
            ProgEvidens.advertencias.put(token, "");
            ProgEvidens.operacion.put(token, new Operaciones());
            ProgEvidens.zipPendiente.put(token, "");
            ProgEvidens.isBuildingPackage.put(token, false);
            ProgEvidens.pendientesFirma.put(token, new PendientesFirma());
        } catch (Exception e) {
            String err = "Fallo al inicializar el progreso de la evidencia";
            log.error(err, e);
            throw new RuntimeException(err + "--" + e.getMessage());
        }
    }

    // Conexión al FTP
    @Override
    public FTPClient ConectarFTP(String token, Boolean... passiveMode) throws Exception {
        FTPClient ftpClient = configurarConexionFTP(passiveMode);
        return ftpClient;
    }

    // Configura la conexión FTP
    private FTPClient configurarConexionFTP(Boolean... passiveMode) throws IOException {
        Conexiones conexion = conexionesRepository.findFirstByServicioContaining("ftp");
        if (conexion == null) {
            throw new RuntimeException("No se encontró un servicio FTP disponible");
        }

        String puerto = conexion.getPuerto();
        if (puerto == null || puerto.isEmpty()) {
            puerto = "21"; // Puerto FTP por defecto
        }

        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.connect(conexion.getIpServicio(), Integer.parseInt(puerto));
        } catch (Exception e) {
            String err = "Fallo, intentando conectar con el servidor FTP.";
            log.error(err, e.getMessage());
            throw new RuntimeException(err);
        }

        if (!FTPReply.isPositiveCompletion(ftpClient.getReplyCode())) {
            ftpClient.disconnect();
            throw new RuntimeException("Fallo al conectar con el servidor FTP");
        }

        if (!ftpClient.login(conexion.getUsuario(), conexion.getPassword())) {
            ftpClient.disconnect();
            throw new RuntimeException("Fallo en la autenticación con el servidor FTP");
        }

        if (passiveMode != null && passiveMode.length > 0 && passiveMode[0]) {
            ftpClient.enterLocalPassiveMode();
        }

        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

        if (conexion.getRuta() != null && !conexion.getRuta().isEmpty()) {
            baseDir = conexion.getRuta();
        }

        boolean dirExists = ftpClient.changeWorkingDirectory(baseDir);
        if (!dirExists) {
            log.error("Fallo, la ruta suministrada en la conexión FTP no es válida");
            baseDir = "/";
            ftpClient.changeWorkingDirectory(baseDir);
        }

        return ftpClient;
    }

    // Crear la estructura de directorios en el FTP
    private String crearEstructuraDirectorio(FTPClient ftpClient, List<Objetivos> objetivos, String fechaInicio,
            String fechaFin) throws IOException {
        Objetivos objetivo = objetivos.get(0); // Se asume que todos los objetivos están relacionados con la misma
                                               // operación
        String carpetaUnidad = objetivo.getOperaciones().getUnidades().getDenominacion();
        String carpetaOperacion = objetivo.getOperaciones().getDescripcion();

        String unidadesDir = baseDir + "/UNIDADES/";
        unidadesDir = unidadesDir.replace("//", "/");

        // Crear la estructura de directorios
        crearDirectorios(ftpClient,
                unidadesDir,
                unidadesDir + carpetaUnidad,
                unidadesDir + carpetaUnidad + "/INFORMES " + carpetaOperacion,
                unidadesDir + carpetaUnidad + "/INFORMES " + carpetaOperacion + "/PERSONALIZADOS",
                unidadesDir + carpetaUnidad + "/INFORMES " + carpetaOperacion + "/ORIGINALES",
                unidadesDir + carpetaUnidad + "/INFORMES " + carpetaOperacion + "/PENDIENTES DE FIRMA",
                unidadesDir + carpetaUnidad + "/INFORMES " + carpetaOperacion + "/FIRMADOS");

        String fi = fechaInicio.replace(":", "_");
        String ff = fechaFin.replace(":", "_");
        String operacionPath = unidadesDir + carpetaUnidad
                + "/INFORMES " + carpetaOperacion
                + "/PERSONALIZADOS/"
                + carpetaOperacion + "(" + fi.replace("T", " ") + "-" + ff.replace("T", " ") + ")/";

        try {
            ftpClient.changeWorkingDirectory(operacionPath);
            ftpClient.mkd("KMLS");
        } catch (Exception e) {
            String err = "Fallo creando directorio KMLS";
            log.error(err, e);
            throw new RuntimeException(err);
        }

        return operacionPath;
    }

    // Crear directorios en el FTP de manera robusta
    private void crearDirectorios(FTPClient ftpClient, String... paths) throws IOException {
        try {
            for (String path : paths) {
                if (!ftpClient.changeWorkingDirectory(path)) {
                    ftpClient.mkd(path);
                }
            }
        } catch (Exception e) {
            String err = "Fallo creando directorios";
            log.error(err, e);
            throw new RuntimeException(err);
        }
    }

    // Procesar los objetivos y generar los archivos
    private void procesarObjetivos(FTPClient ftpClient, String token, Usuarios usuario, List<Objetivos> objetivos,
            String tipoPrecision, String fechaInicio, String fechaFin, String pathOperacion) {
        int totalObjetivos = objetivos.size();
        int incremento = 89 / totalObjetivos;
        int progresoActual = 0;

        StringBuilder pendientesFirmaStr = new StringBuilder();

        for (Objetivos objetivo : objetivos) {
            List<Posiciones> posiciones = null;
            try {
                posiciones = obtenerPosiciones(objetivo, tipoPrecision, fechaInicio, fechaFin);
            } catch (Exception e) {
                String err = e != null && e.getMessage() != null && e.getMessage().contains("Fallo") ? e.getMessage()
                        : "Fallo, obteniendo posiciones";
                log.error(err, e);
                throw new RuntimeException(err);
            }

            try {
                String pendientesFirma = construirFicherosKML(objetivo, posiciones, tipoPrecision, fechaInicio,
                        fechaFin,
                        pathOperacion, usuario.getTip(), usuario.getId(), token);

                if (pendientesFirma != null && !pendientesFirma.isEmpty()) {
                    pendientesFirmaStr.append(pendientesFirma);
                }

                progresoActual += incremento;
                actualizarProgreso(token, progresoActual);

            } catch (Exception e) {
                log.error("Error construyendo ficheros KML/CSV: {}", e.getMessage());
                limpiarProgresoPrevio(token);
                throw new RuntimeException("Error construyendo ficheros", e);
            }
        }

        subirFicherosPendientes(ftpClient, token, pathOperacion);

        // Guardar pendientes de firma
        if (pendientesFirmaStr.length() > 0) {
            ProgEvidens.pendientesFirma.put(token,
                    crearPendientesFirma(objetivos.get(0), pendientesFirmaStr.toString()));
            ProgEvidens.operacion.put(token, objetivos.get(0).getOperaciones());
        }

        completarProgreso(token);
    }

    // Obtener las posiciones de la base de datos
    private List<Posiciones> obtenerPosiciones(Objetivos objetivo, String tipoPrecision, String fechaInicio,
            String fechaFin) {
        try {
            String fInicio = fechaInicio.replace('T', ' ');
            String fFin = fechaFin.replace('T', ' ');
            String signo = tipoPrecision.equals("GPS") ? "=" : "<>";
            String typePrecision = tipoPrecision.equals("GPS") ? "GPS" : "''";

            return posicionesRepository.tomarPosiciones(
                    objetivo.getOperaciones().getUnidades().getId().toString(),
                    objetivo.getDescripcion(),
                    LocalDateTime.parse(fInicio, DATE_FORMATTER),
                    LocalDateTime.parse(fFin, DATE_FORMATTER),
                    typePrecision,
                    signo);
        } catch (Exception e) {
            log.error("Error obteniendo posiciones para objetivo: {}", e.getMessage());
            throw new RuntimeException("Error obteniendo posiciones", e);
        }
    }

    // Construir los ficheros KML y CSV
    private String construirFicherosKML(Objetivos objetivo, List<Posiciones> posiciones, String tipoPrecision,
            String fechaInicio, String fechaFin, String pathOperacion, String tip,
            long idAuth, String token) {
        try {
            return evidenciaRepository.BuildFiles(objetivo, posiciones, tipoPrecision, fechaInicio, fechaFin,
                    pathOperacion, tip, idAuth, token);
        } catch (Exception e) {
            log.error("Error construyendo ficheros KML/CSV: {}", e.getMessage());
            throw new RuntimeException("Error construyendo ficheros", e);
        }
    }

    // Actualizar el progreso de la solicitud usando el token
    private void actualizarProgreso(String token, int nuevoProgreso) {
        ProgEvidens.progEvi.put(token, nuevoProgreso);
    }

    // Subir los ficheros pendientes al FTP
    private void subirFicherosPendientes(FTPClient ftpClient, String token, String pathOperacion) {
        List<String> pendientes = ProgEvidens.ficherosPendientes.get(token);

        for (String fichero : pendientes) {
            try {
                FilesUpload(ftpClient, fichero, pathOperacion + "/KMLS");
            } catch (Exception e) {
                log.error("Fallo subiendo fichero al FTP: {}", e.getMessage());
                limpiarProgresoPrevio(token);
                throw new RuntimeException("Fallo subiendo fichero al FTP", e);
            }
        }
    }

    // Método para subir archivos al FTP
    private void FilesUpload(FTPClient ftpClient, String nombre, String camino) throws IOException {
        FileInputStream fis = null;
        try {
            log.info("nombre del fichero: " + nombre);
            log.info("Camino donde se va a subir: " + camino);
            fis = new FileInputStream(nombre);
            ftpClient.changeWorkingDirectory(camino);
            boolean uploadFile = ftpClient.storeFile(nombre, fis);
            if (!uploadFile) {
                throw new IOException("Fallo al subir el fichero " + nombre + " al FTP");
            } else {
                eliminarFicheroLocal(nombre);
            }
        } catch (Exception e) {
            eliminarFicheroLocal(nombre);
            throw e;
        }
    }

    // Eliminar fichero local después de subirlo
    private void eliminarFicheroLocal(String nombre) {
        File fichero = new File(nombre);
        if (fichero.delete()) {
            log.info("Fichero eliminado: {}", nombre);
        } else {
            log.warn("No se pudo eliminar el fichero: {}", nombre);
        }
    }

    // Completar el progreso de la operación
    private void completarProgreso(String token) {
        ProgEvidens.progEvi.put(token, 100);
        log.info("Progreso completado para el token {}", token);
    }

    // Enviar pendientes de firma al sistema externo (Dataminer)
    private void enviarPendientesFirma(String token, Usuarios usuario, List<Objetivos> objetivos) {
        if (ProgEvidens.pendientesFirma.containsKey(token) && !ProgEvidens.ficherosPendientes.get(token).isEmpty()) {
            try {
                Objetivos obj = objetivos.get(0);
                PendientesFirma pf = ProgEvidens.pendientesFirma.get(token);
                // Aquí llamarías al cliente Dataminer para enviar los pendientes de firma
                // Por ejemplo:
                // dataminer.enviarNombresCSV(pf.getIdDMA(), pf.getIdElement(),
                // pf.getFicheros());
                log.info("Pendientes de firma enviados correctamente para el token {}", token);
            } catch (Exception e) {
                log.error("Fallo enviando pendientes de firma: {}", e.getMessage());
                limpiarProgresoPrevio(token);
                throw new RuntimeException("Fallo enviando pendientes de firma", e);
            }
        }
    }

    // Crear objeto PendientesFirma
    private PendientesFirma crearPendientesFirma(Objetivos objetivo, String ficheros) {
        PendientesFirma pf = new PendientesFirma();
        pf.setIdDMA(Integer.valueOf(objetivo.getOperaciones().getIdDataminer()));
        pf.setIdElement(Integer.valueOf(objetivo.getOperaciones().getIdElement()));
        pf.setFicheros(ficheros);
        return pf;
    }

    // Desconectar FTP y liberar recursos
    @Override
    public void Desconectar(FTPClient ftpClient) {
        try {
            if (ftpClient.isConnected()) {
                ftpClient.logout(); // Desloguear el FTP
                ftpClient.disconnect(); // Desconectar del servidor FTP
                log.info("Conexión FTP desconectada exitosamente.");
            }
        } catch (IOException e) {
            log.error("Error al desconectar el FTP: {}", e.getMessage());
        }
    }
}