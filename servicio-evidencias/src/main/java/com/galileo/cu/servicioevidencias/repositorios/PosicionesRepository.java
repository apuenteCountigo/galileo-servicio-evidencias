package com.galileo.cu.servicioevidencias.repositorios;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestParam;

import com.galileo.cu.commons.models.Balizas;
import com.galileo.cu.commons.models.Estados;
import com.galileo.cu.commons.models.Posiciones;
import com.galileo.cu.commons.models.dto.PosicionesDTO;

@RepositoryRestResource(collectionResourceRel = "posiciones", path = "listar")
public interface PosicionesRepository extends PagingAndSortingRepository<Posiciones, Long> {
        @Query(value = "CALL posUnidad (:idUnidad, :idBalizas, :fechaInicio, :fechaFin)", nativeQuery = true)
        public List<Posiciones> filtrar(String idUnidad, String idBalizas,
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaInicio,
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaFin);
        // http://localhost:60587/listar/search/filtrar?idUnidad=148&idBalizas=(67)&fechaInicio=2022-08-20T00:00:00-04:00&fechaFin=2022-08-28T00:00:00-04:00

        @Query(value = "CALL getPosiciones (:idUnidad, :objetivo, :fechaInicio, :fechaFin, :precision, :signo)", nativeQuery = true)
        public List<Posiciones> tomarPosiciones(String idUnidad, String objetivo,
                        LocalDateTime fechaInicio,
                        LocalDateTime fechaFin, String precision, String signo);
}
