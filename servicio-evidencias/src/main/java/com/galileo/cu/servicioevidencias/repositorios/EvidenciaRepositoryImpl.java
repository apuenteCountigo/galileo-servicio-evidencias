package com.galileo.cu.servicioevidencias.repositorios;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.galileo.cu.commons.models.Conexiones;
import com.galileo.cu.commons.models.Objetivos;
import com.galileo.cu.commons.models.Posiciones;
import com.galileo.cu.commons.models.Progresos;
import com.google.common.base.Strings;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
public class EvidenciaRepositoryImpl implements EvidenciaRepository {

    @Autowired
    ActaRepository actaRepo;

    @Autowired
    ProgresosRepository proRepo;

    String fi;
    String ff;

    @Override
    public FTPClient ConectarFTP(Conexiones con) throws IOException {
        FTPClient ftp = new FTPClient();
        try {
            ftp.connect(con.getIpServicio(),
                    Integer.parseInt(((!Strings.isNullOrEmpty(con.getPuerto())) ? con.getPuerto() : "21")));
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

            if (!Strings.isNullOrEmpty(fileName)) {
                File f = new File(fileName);
                if (f.delete()) {
                    log.info("Fichero " + fileName + " Eliminado");
                } else {
                    log.info("No se Pudo Eliminar el Fichero " + fileName);
                }
            }

            DesconectarFTP(error);
            throw new RuntimeException(error);
        }
    }

    @Override
    public void SubirFichero(String nombre, String camino) {
        boolean si = false;
        try {
            si = ProgEvidens.ftp.changeWorkingDirectory(camino);
        } catch (IOException e) {
            DesconectarFTP("Fallo al Generar Evidencias, Cambiando al Directorio " + camino);
            EliminarFichero(nombre);
            log.error("Fallo al Generar Evidencias, Cambiando al Directorio " + camino, e);
            throw new RuntimeException("Fallo al Generar Evidencias, Cambiando al Directorio " + camino);
        }

        if (!si) {
            DesconectarFTP("Fallo al Generar Evidencias, Cambiando al Directorio " + camino);
            EliminarFichero(nombre);
            log.error("Fallo al Generar Evidencias, Cambiando al Directorio " + camino);
            throw new RuntimeException("Fallo al Generar Evidencias, Cambiando al Directorio " + camino);
        }

        FileInputStream fis = null;
        try {
            ProgEvidens.ftp.setBufferSize(2048);
            ProgEvidens.ftp.enterLocalPassiveMode();
            ProgEvidens.ftp.setFileType(FTP.BINARY_FILE_TYPE);

            fis = new FileInputStream(nombre);
            boolean uploadFile = ProgEvidens.ftp.storeFile(nombre, fis);
            if (!uploadFile) {
                DesconectarFTP("Fallo al Generar Evidencias, Subiendo el Fichero " + nombre + " al FTP ");
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
                    DesconectarFTP("Fallo al Generar Evidencias, Subiendo el Fichero " + nombre + " al FTP ");
                    EliminarFichero(nombre);
                    log.error("Fallo al Generar Evidencias, Subiendo el Fichero " + nombre + " al FTP ", er);
                    throw new RuntimeException(
                            "Fallo al Generar Evidencias, Subiendo el Fichero " + nombre + " al FTP ");
                }
            }

            DesconectarFTP("Fallo al Generar Evidencias, Subiendo el Fichero " + nombre + " al FTP ");
            throw new RuntimeException("Fallo al Generar Evidencias, Subiendo el Fichero " + nombre + " al FTP ");
        }
    }

    @Override
    public void DesconectarFTP(String er) {
        try {
            ProgEvidens.ftp.disconnect();
        } catch (IOException e) {
            log.error("Fallo al Generar Evidencias, Desconectando el FTP ", e);
            throw new RuntimeException(er);
        }
    }

    @Override
    public String BuildFiles(Objetivos obj, List<Posiciones> pos, String tipoPrecision, String finicio, String ffin,
            String pathOperacion, String tip, long idAuth, int incre) {
        String pendientesFirma[] = { "" };

        fi = finicio.replace("T", " ");
        ff = ffin.replace("T", " ");

        String nombreFichero = obj.getOperaciones().getDescripcion() + "_" + obj.getDescripcion() + "(" + fi + "-"
                + ff + ")";
        String nombreFicheroKML = obj.getOperaciones().getDescripcion() + "_" + obj.getDescripcion() + "(" + fi + "-"
                + ff + ")";

        log.info("Fecha Inicio 3-BuildFiles:: " + fi);
        log.info("Fecha Fin:: 3-BuildFiles" + ff);
        FileWriter csv = null;

        try {
            if (pos.size() == 0 && obj.getBalizas() != null) {
                nombreFichero = obj.getBalizas().getClave() + "(" + fi + "-"
                        + ff + ")";
                csv = new FileWriter(nombreFichero + "(Vacio)" + ".csv");
                csv.close();
                /*
                 * CrearDirectorio(ftp, pathOperacion + obj.getBalizas().getClave(),
                 * "Fallo al Generar Evidencias, Creando el Directorio " + pathOperacion
                 * + obj.getBalizas().getClave(),
                 * nombreFichero + "(Vacio)" + ".csv");
                 * SubirFichero(ftp, nombreFichero + "(Vacio)" + ".csv", pathOperacion +
                 * obj.getBalizas().getClave());
                 */
                List<String> ls = ProgEvidens.ficherosPendientes.get(idAuth);
                ls.add(obj.getBalizas().getClave() + "®" + nombreFichero + "(Vacio).csv");
                // ls.add(nombreFichero + "(Vacio).csv");
                ProgEvidens.ficherosPendientes.replace(idAuth, ls);
                pendientesFirma[0] += nombreFichero + "(Vacio),";
                return pendientesFirma[0];
            } else if (pos.size() == 0) {
                /*
                 * ProgEvidens.progEvi.remove(idAuth);
                 * ProgEvidens.ficherosPendientes.remove(idAuth);
                 * log.
                 * error("Fallo en el Proceso de Evidencias, Objetivo sin Baliza y sin Posiciones en la BD "
                 * );
                 * throw new RuntimeException("Objetivo sin Baliza y sin Posiciones en la BD");
                 */
                String adv = ProgEvidens.advertencias.get(idAuth) + ", " + obj.getDescripcion();
                ProgEvidens.advertencias.replace(idAuth, adv);
                return pendientesFirma[0];
            }
        } catch (Exception e) {
            ProgEvidens.progEvi.remove(idAuth);
            ProgEvidens.ficherosPendientes.remove(idAuth);
            if (csv != null) {
                try {
                    csv.close();
                    EliminarFichero(nombreFichero + "(Vacio)" + ".csv");
                } catch (IOException er) {
                    EliminarFichero(nombreFichero + "(Vacio)" + ".csv");
                    log.error("Fallo cerrando Fichero csv: ", er);
                }
            }
            log.error("Fallo Creando Ficheros KML y CSV: ", e);
            throw new RuntimeException(e.getMessage());
        }

        int incremento = incre / 10;

        String head = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document><Style id=\"0\"><IconStyle><scale>0.7</scale><Icon><href>http://www.google.com/mapfiles/ms/micons/red.png</href></Icon></IconStyle></Style><Style id=\"1\"><IconStyle><scale>0.7</scale><Icon><href>http://www.google.com/mapfiles/ms/micons/green.png</href></Icon></IconStyle></Style><Style id=\"2\"><IconStyle><scale>0.7</scale><Icon><href>http://maps.gstatic.com/mapfiles/ridefinder-images/mm_20_black.png</href></Icon></IconStyle></Style><Style id=\"3\"><IconStyle><scale>0.7</scale><Icon><href>http://www.google.com/mapfiles/ms/micons/yellow.png</href></Icon></IconStyle></Style><Style id=\"4\"><IconStyle><scale>0.7</scale><Icon><href>http://www.google.com/mapfiles/ms/micons/red.png</href></Icon></IconStyle></Style>";
        String csvContent = "Dispositivo,FechadeCaptacion,Latitud,Longitud,ServidorTimestamp,Satelites,Precision,Evento,Velocidad,Rumbo,CeldaID\n";

        int[] indice = { 0, 0, 0 };// [0]=Cantidad; [1]=indice;[2]=Cantidad de Incrementos Realizados
        indice[0] = pos.size() / 10;
        String[] bClave = { "", "", "" };// [0]=Clave Blaiza, [1]=contenido KML, [2]=contenido csv
        pos.forEach((Posiciones p) -> {
            indice[1]++;
            String tp = tipoPrecision;
            if (!p.getClave().equals(bClave[0]) && !bClave[0].equals("")) {
                String nf = bClave[0] + "(" + fi + "-"
                        + ff + ")";

                try {
                    WriteFiles(nf + ".csv", csvContent + bClave[2], pathOperacion + "/" + bClave[0],
                            "Fallo Creando Fichero CSV: ", idAuth);
                    pendientesFirma[0] += nf + ",";
                    List<String> ls = ProgEvidens.ficherosPendientes.get(idAuth);
                    ls.add(bClave[0] + "®" + nf + ".csv");
                    ProgEvidens.ficherosPendientes.replace(idAuth, ls);
                } catch (Exception e) {
                    log.error("Fallo Creando Fichero CSV: ", e.getMessage());
                    ProgEvidens.progEvi.remove(idAuth);
                    ProgEvidens.ficherosPendientes.remove(idAuth);
                    throw new RuntimeException(e.getMessage());
                }

                try {
                    WriteFiles(nombreFicheroKML + ".kml", head + bClave[1] + "</Document></kml>",
                            pathOperacion + "/KMLS", "Fallo Creando Fichero KML: ", idAuth);
                    List<String> ls = ProgEvidens.ficherosPendientes.get(idAuth);
                    ls.add(nombreFicheroKML + ".kml");
                    ProgEvidens.ficherosPendientes.replace(idAuth, ls);
                } catch (Exception e) {
                    log.error("Fallo Creando Fichero KML: ", e.getMessage());
                    ProgEvidens.progEvi.remove(idAuth);
                    ProgEvidens.ficherosPendientes.remove(idAuth);
                    throw new RuntimeException(e.getMessage());
                }

                String acta = "Acta " + bClave[0] + ".pdf";
                try {
                    actaRepo.GenerarActa(acta, obj, bClave[0], tip, fi, ff);
                    List<String> ls = ProgEvidens.ficherosPendientes.get(idAuth);
                    ls.add(bClave[0] + "®" + acta);
                    ProgEvidens.ficherosPendientes.replace(idAuth, ls);
                } catch (Exception e) {
                    log.error("Fallo Creando Acta: ", e.getMessage());
                    ProgEvidens.progEvi.remove(idAuth);
                    ProgEvidens.ficherosPendientes.remove(idAuth);
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
                    + ((p.getPrecision().equals("Celda"))
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

            if (indice[1] == indice[0]) {
                indice[1] = 0;
                indice[2]++;
                if (indice[2] < 10) {
                    int por = ProgEvidens.progEvi.get(idAuth);
                    ProgEvidens.progEvi.replace(idAuth, por + incremento);
                }
            }
        });

        if (bClave[0] != "" && bClave[1] != "" && bClave[2] != "") {
            String nf = bClave[0] + "(" + fi + "-"
                    + ff + ")";

            try {
                WriteFiles(nf + ".csv", csvContent + bClave[2], pathOperacion + "/" + bClave[0],
                        "Fallo Creando Fichero CSV: ", idAuth);
                pendientesFirma[0] += nf;
                List<String> ls = ProgEvidens.ficherosPendientes.get(idAuth);
                ls.add(bClave[0] + "®" + nf + ".csv");
                ProgEvidens.ficherosPendientes.replace(idAuth, ls);
            } catch (Exception e) {
                log.error("Fallo Creando Fichero CSV: ", e);
                ProgEvidens.progEvi.remove(idAuth);
                ProgEvidens.ficherosPendientes.remove(idAuth);
                throw new RuntimeException(e.getMessage());
            }

            try {
                WriteFiles(nombreFicheroKML + ".kml", head + bClave[1] + "</Document></kml>", pathOperacion + "/KMLS",
                        "Fallo Creando Fichero KML: ", idAuth);
                List<String> ls = ProgEvidens.ficherosPendientes.get(idAuth);
                ls.add(nombreFicheroKML + ".kml");
                ProgEvidens.ficherosPendientes.replace(idAuth, ls);
            } catch (Exception e) {
                log.error("Fallo Creando Fichero KML: ", e);
                ProgEvidens.progEvi.remove(idAuth);
                ProgEvidens.ficherosPendientes.remove(idAuth);
                throw new RuntimeException(e.getMessage());
            }

            String acta = "Acta " + bClave[0] + ".pdf";
            try {
                actaRepo.GenerarActa(acta, obj, bClave[0], tip, fi, ff);
                List<String> ls = ProgEvidens.ficherosPendientes.get(idAuth);
                ls.add(bClave[0] + "®" + acta);
                ProgEvidens.ficherosPendientes.replace(idAuth, ls);
            } catch (Exception e) {
                log.error("Fallo Creando Acta: ", e.getMessage());
                ProgEvidens.progEvi.remove(idAuth);
                ProgEvidens.ficherosPendientes.remove(idAuth);
                throw new RuntimeException(e.getMessage());
            }

            /*
             * try {
             * SubirFichero(ftp, acta, pathOperacion + bClave[0]);
             * } catch (Exception e) {
             * log.error("Fallo Subiendo Acta: ", e.getMessage());
             * ProgEvidens.progEvi.remove(idAuth);
             * throw new RuntimeException(e.getMessage());
             * }
             */

            bClave[0] = "";
            bClave[1] = "";
            bClave[2] = "";

            int por = ProgEvidens.progEvi.get(idAuth);
            ProgEvidens.progEvi.replace(idAuth, por + incremento);
        }
        return pendientesFirma[0];
    }

    @Override
    public void WriteFiles(String nombreFichero, String contenido, String camino, String error,
            long idAuth) {
        FileWriter f;
        try {
            f = new FileWriter(nombreFichero);
            f.write(contenido);
            f.close();
            /*
             * CrearDirectorio(ftp, camino,
             * "Fallo al Generar Evidencias, Creando el Directorio " + camino,
             * nombreFichero);
             * SubirFichero(ftp, nombreFichero, camino);
             */
        } catch (IOException e) {
            EliminarFichero(nombreFichero);
            log.error(error, e);
            ProgEvidens.progEvi.remove(idAuth);
            ProgEvidens.ficherosPendientes.remove(idAuth);
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

    @Override
    public void EliminarProgEvidens(Long idUsu, Boolean... keepZipPendiente) {
        ProgEvidens.progEvi.remove(idUsu);
        ProgEvidens.ficherosPendientes.remove(idUsu);
        ProgEvidens.advertencias.remove(idUsu);
        ProgEvidens.operacion.remove(idUsu);

        if (keepZipPendiente != null && keepZipPendiente.length > 0 && !keepZipPendiente[0]) {
            log.info("zipPendiente.remove");
            ProgEvidens.zipPendiente.remove(idUsu);
        } else {
            log.info("removed ALL");
        }
    }
}
