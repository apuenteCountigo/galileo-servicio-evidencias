package com.galileo.cu.servicioevidencias.repositorios;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RestResource;

import com.galileo.cu.commons.models.Progresos;

@RestResource(exported = false)
public interface ProgresosRepository extends CrudRepository<Progresos, Long> {
    @Query("SELECT p FROM Progresos p WHERE p.usuario.id=:idAuth")
    Progresos buscarUsuario(long idAuth);
}
