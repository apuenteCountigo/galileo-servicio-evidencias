package com.galileo.cu.servicioevidencias.controladores;

import java.io.FileNotFoundException;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.core.annotation.HandleBeforeCreate;
import org.springframework.data.rest.core.annotation.RepositoryEventHandler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.galileo.cu.commons.models.Posiciones;
import com.galileo.cu.servicioevidencias.helpers.ActaPDF;
import com.galileo.cu.servicioevidencias.repositorios.PosicionesRepository;
import com.itextpdf.text.DocumentException;

@Component
@RepositoryEventHandler(Posiciones.class)
public class PosicionesEventHandler {
    @Autowired
    PosicionesRepository posRepo;

    @Autowired
    HttpServletRequest req;

    @HandleBeforeCreate
	public void handleObjetivosCreate(Posiciones pos) throws FileNotFoundException, DocumentException{
        System.out.println("ActaPDF");
        ActaPDF acta= new ActaPDF();
        System.out.println("fin ActaPDF");
        throw new ResponseStatusException(HttpStatus.ACCEPTED, "Acta Completada", null);
    }
}
