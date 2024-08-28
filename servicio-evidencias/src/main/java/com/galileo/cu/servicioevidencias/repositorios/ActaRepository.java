package com.galileo.cu.servicioevidencias.repositorios;

import org.springframework.stereotype.Repository;

import com.galileo.cu.commons.models.Objetivos;

@Repository
public interface ActaRepository {
    void GenerarActa(String nombre, Objetivos obj, String clave, String tip, String fi, String ff);
}
