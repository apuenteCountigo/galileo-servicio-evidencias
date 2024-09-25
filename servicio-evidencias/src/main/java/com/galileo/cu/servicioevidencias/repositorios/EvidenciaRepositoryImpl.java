package com.galileo.cu.servicioevidencias.repositorios;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.galileo.cu.commons.models.Conexiones;
import com.galileo.cu.commons.models.Objetivos;
import com.galileo.cu.commons.models.Posiciones;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
public class EvidenciaRepositoryImpl implements EvidenciaRepository {

    @Autowired
    ActaRepository actaRepo;

    String fi;
    String ff;

    @Override
    public FTPClient ConectarFTP(Conexiones con) throws IOException {
        FTPClient ftp = new FTPClient();
        try {
            ftp.connect(con.getIpServicio(),
                    Integer.parseInt((con.getPuerto() != null && !con.getPuerto().isEmpty()) ? con.getPuerto() : "21"));
        } catch (Exception e) {
            log.error("Conexión Fallida al servidor FTP " + con.getIpServicio() + ":" + con.getPuerto(), e);
            throw new IOException("Conexión Fallida al servidor FTP " + con.getIpServicio() + ":" + con.getPuerto());
        }
        int reply = ftp.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            ftp.disconnect();
            log.error("getReplyCode: Conexión Fallida al servidor FTP " + con.getIpServicio() + ":" + con.getPuerto());
            throw new IOException("Conexión Fallida al servidor FTP " + con.getIpServicio() + ":" + con.getPuerto());
        }

        try {
            ftp.login(con.getUsuario(), con.getPassword());
            ftp.setControlKeepAliveTimeout(200);
        } catch (Exception e) {
            log.error("Usuario o Contraseña Incorrecto, al Intentar Autenticarse en Servidor FTP " + con.getIpServicio()
                    + ":" + con.getPuerto(), e);
            throw new IOException("Usuario o Contraseña Incorrecto, al Intentar Autenticarse en Servidor FTP "
                    + con.getIpServicio() + ":" + con.getPuerto());
        }
        return ftp;
    }

    @Override
    public void CrearDirectorio(FTPClient ftp, String camino, String error, String fileName) {
        try {
            ftp.changeWorkingDirectory("/");
            ftp.mkd(camino);
        } catch (IOException e) {
            log.error(error, e);

            if (fileName != null && !fileName.isEmpty()) {
                File f = new File(fileName);
                if (f.delete()) {
                    log.info("Fichero " + fileName + " Eliminado");
                } else {
                    log.info("No se Pudo Eliminar el Fichero " + fileName);
                }
            }

            DesconectarFTP(ftp, error);
            throw new RuntimeException(error);
        }
    }

    @Override
    public void SubirFichero(FTPClient ftpClient, String nombre, String camino) {
        boolean si = false;
        try {
            si = ftpClient.changeWorkingDirectory(camino);
        } catch (IOException e) {
            DesconectarFTP(ftpClient, "Fallo al Generar Evidencias, Cambiando al Directorio " + camino);
            EliminarFichero(nombre);
            log.error("Fallo al Generar Evidencias, Cambiando al Directorio " + camino, e);
            throw new RuntimeException("Fallo al Generar Evidencias, Cambiando al Directorio " + camino);
        }

        if (!si) {
            DesconectarFTP(ftpClient, "Fallo al Generar Evidencias, Cambiando al Directorio " + camino);
            EliminarFichero(nombre);
            log.error("Fallo al Generar Evidencias, Cambiando al Directorio " + camino);
            throw new RuntimeException("Fallo al Generar Evidencias, Cambiando al Directorio " + camino);
        }

        try (FileInputStream fis = new FileInputStream(nombre)) {
            ftpClient.setBufferSize(2048);
            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            boolean uploadFile = ftpClient.storeFile(nombre, fis);
            if (!uploadFile) {
                DesconectarFTP(ftpClient, "Fallo al Generar Evidencias, Subiendo el Fichero " + nombre + " al FTP ");
                EliminarFichero(nombre);
                log.error("Fallo al Generar Evidencias, Subiendo el Fichero " + nombre + " al FTP ");
                throw new RuntimeException("Fallo al Generar Evidencias, Subiendo el Fichero " + nombre + " al FTP ");
            } else {
                EliminarFichero(nombre);
            }
        } catch (Exception e) {
            EliminarFichero(nombre);
            DesconectarFTP(ftpClient, "Fallo al Generar Evidencias, Subiendo el Fichero " + nombre + " al FTP ");
            log.error("Fallo al Generar Evidencias, Subiendo el Fichero " + nombre + " al FTP ", e);
            throw new RuntimeException("Fallo al Generar Evidencias, Subiendo el Fichero " + nombre + " al FTP ");
        }
    }

    @Override
    public void DesconectarFTP(FTPClient ftpClient, String er) {
        try {
            ftpClient.disconnect();
        } catch (IOException e) {
            log.error("Fallo al Generar Evidencias, Desconectando el FTP ", e);
            throw new RuntimeException(er);
        }
    }

    @Override
    public String BuildFiles(Objetivos obj, List<Posiciones> pos, String tipoPrecision, String finicio, String ffin,
            String pathOperacion, String tip, long idAuth, String token) {

        StringBuilder pendientesFirma = new StringBuilder();

        fi = finicio.replace("T", " ");
        ff = ffin.replace("T", " ");

        String nombreFichero = obj.getOperaciones().getDescripcion() + "_" + obj.getDescripcion() + "(" + fi + "-"
                + ff + ")";
        String nombreFicheroKML = obj.getOperaciones().getDescripcion() + "_" + obj.getDescripcion() + "(" + fi + "-"
                + ff + ")";

        log.info("Fecha Inicio 3-BuildFiles:: " + fi);
        log.info("Fecha Fin:: 3-BuildFiles" + ff);

        try {
            if (pos.size() == 0 && obj.getBalizas() != null) {
                nombreFichero = obj.getBalizas().getClave() + "(" + fi + "-" + ff + ")";
                try (FileWriter csv = new FileWriter(nombreFichero + "(Vacio)" + ".csv")) {
                    // Archivo CSV vacío creado
                }
                ProgEvidens.ficherosPendientes.get(token)
                        .add(obj.getBalizas().getClave() + "®" + nombreFichero + "(Vacio).csv");
                pendientesFirma.append(nombreFichero).append("(Vacio),");
                return pendientesFirma.toString();
            } else if (pos.size() == 0) {
                String adv = ProgEvidens.advertencias.get(token) + ", " + obj.getDescripcion();
                ProgEvidens.advertencias.replace(token, adv);
                return pendientesFirma.toString();
            }
        } catch (Exception e) {
            limpiarProgresoPrevio(token);
            log.error("Fallo Creando Ficheros KML y CSV: ", e);
            throw new RuntimeException(e.getMessage());
        }

        String head = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document><Style id=\"0\"><IconStyle><scale>0.7</scale><Icon><href>http://www.google.com/mapfiles/ms/micons/red.png</href></Icon></IconStyle></Style><Style id=\"1\"><IconStyle><scale>0.7</scale><Icon><href>http://www.google.com/mapfiles/ms/micons/green.png</href></Icon></IconStyle></Style><Style id=\"2\"><IconStyle><scale>0.7</scale><Icon><href>http://maps.gstatic.com/mapfiles/ridefinder-images/mm_20_black.png</href></Icon></IconStyle></Style><Style id=\"3\"><IconStyle><scale>0.7</scale><Icon><href>http://www.google.com/mapfiles/ms/micons/yellow.png</href></Icon></IconStyle></Style><Style id=\"4\"><IconStyle><scale>0.7</scale><Icon><href>http://www.google.com/mapfiles/ms/micons/red.png</href></Icon></IconStyle></Style>";
        String csvContent = "Dispositivo,FechadeCaptacion,Latitud,Longitud,ServidorTimestamp,Satelites,Precision,Evento,Velocidad,Rumbo,CeldaID\n";

        String[] bClave = { "", "", "" }; // [0]=Clave Baliza, [1]=contenido KML, [2]=contenido csv
        for (Posiciones p : pos) {
            String tp = tipoPrecision;
            if (!p.getClave().equals(bClave[0]) && !bClave[0].equals("")) {
                String nf = bClave[0] + "(" + fi + "-" + ff + ")";

                try {
                    WriteFiles(nf + ".csv", csvContent + bClave[2], pathOperacion + "/" + bClave[0],
                            "Fallo Creando Fichero CSV: ");
                    pendientesFirma.append(nf).append(",");
                    ProgEvidens.ficherosPendientes.get(token).add(bClave[0] + "®" + nf + ".csv");
                } catch (Exception e) {
                    log.error("Fallo Creando Fichero CSV: ", e.getMessage());
                    limpiarProgresoPrevio(token);
                    throw new RuntimeException(e.getMessage());
                }

                try {
                    WriteFiles(nombreFicheroKML + ".kml", head + bClave[1] + "</Document></kml>",
                            pathOperacion + "/KMLS", "Fallo Creando Fichero KML: ");
                    ProgEvidens.ficherosPendientes.get(token).add(nombreFicheroKML + ".kml");
                } catch (Exception e) {
                    log.error("Fallo Creando Fichero KML: ", e.getMessage());
                    limpiarProgresoPrevio(token);
                    throw new RuntimeException(e.getMessage());
                }

                String acta = "Acta " + bClave[0] + ".pdf";
                try {
                    actaRepo.GenerarActa(acta, obj, bClave[0], tip, fi, ff);
                    ProgEvidens.ficherosPendientes.get(token).add(bClave[0] + "®" + acta);
                } catch (Exception e) {
                    log.error("Fallo Creando Acta: ", e.getMessage());
                    limpiarProgresoPrevio(token);
                    throw new RuntimeException(e.getMessage());
                }

                bClave[1] = "";
                bClave[2] = "";
            }

            int tipoestilo = 1;
            if (!p.getPrecision().equals("GPS")) {
                tipoestilo = 3;
            }

            bClave[1] += "<Placemark><name>" + p.getClave() + " " + p.getFechaCaptacion()
                    + "</name><description>" + p.getVelocidad() + "km/h\r\nTipo Posicion: " + p.getPrecision()
                    + (p.getPrecision().equals("Celda")
                            ? ": " + p.getMmcBts() + " " + p.getMncBts() + " " + p.getLacBts()
                            : "");

            bClave[1] += "</description><Point><coordinates>";
            bClave[1] += p.getLongitud() + "," + p.getLatitud() + "</coordinates></Point><styleUrl>#";
            bClave[1] += tipoestilo + "</styleUrl></Placemark>";

            bClave[2] += p.getBalizas().getClave() + "," + p.getFechaCaptacion() + "," + p.getLatitud()
                    + "," + p.getLongitud() + "," + p.getTimestampServidor() + "," + p.getSatelites() + ","
                    + p.getPrecision() + "," + p.getEvento() + "," + p.getVelocidad() + "," + p.getRumbo()
                    + "," + p.getPrecision() + "\n";
            bClave[0] = p.getClave();
        }

        if (!bClave[0].isEmpty() && !bClave[1].isEmpty() && !bClave[2].isEmpty()) {
            String nf = bClave[0] + "(" + fi + "-" + ff + ")";

            try {
                WriteFiles(nf + ".csv", csvContent + bClave[2], pathOperacion + "/" + bClave[0],
                        "Fallo Creando Fichero CSV: ");
                pendientesFirma.append(nf);
                ProgEvidens.ficherosPendientes.get(token).add(bClave[0] + "®" + nf + ".csv");
            } catch (Exception e) {
                log.error("Fallo Creando Fichero CSV: ", e);
                limpiarProgresoPrevio(token);
                throw new RuntimeException(e.getMessage());
            }

            try {
                WriteFiles(nombreFicheroKML + ".kml", head + bClave[1] + "</Document></kml>", pathOperacion + "/KMLS",
                        "Fallo Creando Fichero KML: ");
                ProgEvidens.ficherosPendientes.get(token).add(nombreFicheroKML + ".kml");
            } catch (Exception e) {
                log.error("Fallo Creando Fichero KML: ", e);
                limpiarProgresoPrevio(token);
                throw new RuntimeException(e.getMessage());
            }

            String acta = "Acta " + bClave[0] + ".pdf";
            try {
                actaRepo.GenerarActa(acta, obj, bClave[0], tip, fi, ff);
                ProgEvidens.ficherosPendientes.get(token).add(bClave[0] + "®" + acta);
            } catch (Exception e) {
                log.error("Fallo Creando Acta: ", e.getMessage());
                limpiarProgresoPrevio(token);
                throw new RuntimeException(e.getMessage());
            }

            bClave[0] = "";
            bClave[1] = "";
            bClave[2] = "";
        }
        return pendientesFirma.toString();
    }

    @Override
    public void WriteFiles(String nombreFichero, String contenido, String camino, String error) {
        try (FileWriter f = new FileWriter(nombreFichero)) {
            f.write(contenido);
        } catch (IOException e) {
            EliminarFichero(nombreFichero);
            log.error(error, e);
            throw new RuntimeException(error);
        }
    }

    @Override
    public void EliminarFichero(String nombre) {
        File fichero = new File(nombre);
        if (fichero.delete()) {
            log.info("Fue Eliminado el Fichero: " + nombre);
        } else {
            log.info("No se Pudo Eliminar el Fichero: " + nombre);
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

    @Override
    public void EliminarProgEvidens(String token, Boolean... keepZipPendiente) {
        ProgEvidens.progEvi.remove(token);
        ProgEvidens.ficherosPendientes.remove(token);
        ProgEvidens.advertencias.remove(token);
        ProgEvidens.operacion.remove(token);
        ProgEvidens.isBuildingPackage.remove(token);
        ProgEvidens.pendientesFirma.remove(token);

        if (keepZipPendiente != null && keepZipPendiente.length > 0 && !keepZipPendiente[0]) {
            log.info("zipPendiente.remove");
            ProgEvidens.zipPendiente.remove(token);
        } else {
            log.info("removed ALL");
        }
    }
}