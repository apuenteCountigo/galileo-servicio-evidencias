package com.galileo.cu.servicioevidencias.repositorios;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.rest.core.annotation.RestResource;

import com.galileo.cu.commons.models.Conexiones;

@RestResource(exported = false)
public interface ConexionesRepository extends CrudRepository<Conexiones,Long> {
    Conexiones findFirstByServicioContaining(String servicio);
}
