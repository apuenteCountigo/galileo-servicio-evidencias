package com.galileo.cu.servicioevidencias.repositorios;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

import javax.swing.plaf.basic.BasicSplitPaneUI.BasicHorizontalLayoutManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.WebProperties.Resources;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;

import com.galileo.cu.commons.models.Objetivos;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.TabStop.Alignment;
import com.itextpdf.text.pdf.PdfWriter;

@Repository
public class ActaRepositoryImpl implements ActaRepository {

        @Override
        public void GenerarActa(String nombre, Objetivos obj, String clave, String tip, String fi, String ff) {
                System.out.println("Fecha Inicio 2-GenerarActa:: " + fi);
                System.out.println("Fecha Fin 2-GenerarActa:: " + ff);

                Document document = new Document(PageSize.LETTER, 80, 40, 30, 30);
                try {
                        PdfWriter.getInstance(document, new FileOutputStream(nombre));
                        document.open();
                        Font ftitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, BaseColor.BLACK);
                        Font ftexto = FontFactory.getFont(FontFactory.HELVETICA, 14, BaseColor.BLACK);

                        Paragraph titulo = new Paragraph("ACTA DE APORTACIÓN DE GEOLOCALIZACIONES", ftitulo);

                        ZoneId zoneId = ZoneId.systemDefault();
                        ZonedDateTime zonedDateTime = ZonedDateTime.now(zoneId);
                        String hora = DateTimeFormatter.ofPattern("HH:mm:ss")
                                        .format(zonedDateTime);

                        System.out.println("HORA ZONIFICADA:: " + hora);

                        // String hora = DateTimeFormatter.ofPattern("hh:mm:ss")
                        // .format(LocalDateTime.now());
                        String fecha = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                                        .format(LocalDateTime.now());

                        String fIni = fi.substring(0, 10);
                        String fFin = ff.substring(0, 10);

                        String fHi = fi.substring(11, 13);
                        String fMi = fi.substring(14, 16);
                        String fSi = fi.substring(17, 19);
                        String HIni = fHi + ":" + fMi + ":" + fSi;

                        String fHf = ff.substring(11, 13);
                        String fMf = ff.substring(14, 16);
                        String fSf = ff.substring(17, 19);
                        String HFin = fHf + ":" + fMf + ":" + fSf;

                        Paragraph parrafo1 = new Paragraph(
                                        "En la localidad de " + obj.getOperaciones().getUnidades().getLocalidad()
                                                        + ", provincia de "
                                                        + obj.getOperaciones().getUnidades().getProvincia()
                                                                        .getDescripcion()
                                                        + " siendo las " + hora
                                                        + " del día "
                                                        + fecha + ", el agente con TIP " + tip
                                                        + ", por medio de la presente ACTA, hace constar:",
                                        ftexto);
                        parrafo1.setAlignment(Element.ALIGN_JUSTIFIED);

                        Paragraph parrafo2 = new Paragraph("Que se aportan a las diligencias "
                                        + obj.getOperaciones().getDiligencias() + ", instruidas por el Juzgado "
                                        + obj.getOperaciones().getJuzgado().getDescripcion()
                                        + ", el presente acta donde se relacionan los puntos capturados por el dispositivo del sistema de seguimiento global por satélite SSGS, número "
                                        + clave + ", comprendiendo desde las " + HIni + ", del día " + fIni
                                        + " hasta las " + HFin + " del día " + fFin, ftexto);
                        parrafo2.setAlignment(Element.ALIGN_JUSTIFIED);

                        Paragraph parrafo3 = new Paragraph(
                                        "A la presente acta se adjunta soporte de almacenamiento digital de datos que incluyen los archivos digitales originales como copia auténtica.",
                                        ftexto);
                        parrafo3.setAlignment(Element.ALIGN_JUSTIFIED);

                        Paragraph parrafo4 = new Paragraph(
                                        "Y para que conste se firma el presente acta, en prueba de conformidad, por parte del agente actuante.",
                                        ftexto);
                        parrafo4.setAlignment(Element.ALIGN_JUSTIFIED);

                        Paragraph pieFirma = new Paragraph("El agente interviniente.", ftexto);
                        pieFirma.setAlignment(Element.ALIGN_RIGHT);

                        Paragraph ptip = new Paragraph(tip, ftexto);
                        ptip.setAlignment(Element.ALIGN_RIGHT);

                        Resource res1 = new ClassPathResource("EscudoSuperior.png");
                        Resource res2 = new ClassPathResource("EscudoInferior.png");

                        InputStream input1 = res1.getInputStream();
                        InputStream input2 = res2.getInputStream();

                        System.out.println(res1.getURI());

                        Image imagen1 = Image.getInstance(input1.readAllBytes());
                        Image imagen2 = Image.getInstance(input2.readAllBytes());

                        document.add(new Paragraph(" "));
                        document.add(titulo);
                        document.add(new Paragraph(" "));
                        document.add(new Paragraph(" "));
                        document.add(parrafo1);
                        document.add(new Paragraph(" "));
                        document.add(new Paragraph(" "));
                        document.add(parrafo2);
                        document.add(new Paragraph(" "));
                        document.add(parrafo3);
                        document.add(new Paragraph(" "));
                        document.add(parrafo4);

                        document.add(new Paragraph(" "));
                        document.add(new Paragraph(" "));
                        document.add(new Paragraph(" "));
                        document.add(new Paragraph(" "));
                        document.add(new Paragraph(" "));
                        document.add(pieFirma);

                        document.add(new Paragraph(" "));
                        document.add(new Paragraph(" "));
                        document.add(new Paragraph(" "));
                        document.add(new Paragraph(" "));
                        document.add(ptip);

                        imagen1.setAbsolutePosition(3f, 670f);
                        // imagen1.setWidthPercentage(10);
                        // imagen1.setScaleToFitHeight(true);
                        // imagen1.scaleAbsoluteHeight(80);
                        document.add(imagen1);
                        imagen2.setAbsolutePosition(3f, 5f);
                        // imagen2.setWidthPercentage(10);
                        // imagen2.setScaleToFitHeight(true);
                        // imagen2.scaleAbsoluteHeight(30);
                        document.add(imagen2);
                        document.close();
                } catch (Exception e) {
                        System.out.println("Fallo al Generar Acta " + e.getMessage());
                        throw new RuntimeException("Fallo al Generar Acta ");
                }
        }

}
