package com.galileo.cu.servicioevidencias.repositorios;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.rest.core.annotation.RestResource;

import com.galileo.cu.commons.models.Usuarios;

@RestResource(exported = false)
public interface UsuariosRepository extends CrudRepository<Usuarios, Long> {
    
}
