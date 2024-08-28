package com.galileo.cu.servicioevidencias.repositorios;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.format.annotation.DateTimeFormat;

import com.galileo.cu.commons.models.HistoricoObjetivosBalizas;

@RestResource(exported = false)
public interface HistoricoObjetivosBalizasRepository extends CrudRepository<HistoricoObjetivosBalizas, Long> {
    @Query("SELECT hob FROM HistoricoObjetivosBalizas hob WHERE hob.objetivo.id=:idObjetivo "
    		+ "AND ("
    		+ "	( "
    		+ "		(hob.fecha between :fi AND :ff AND :fi!=null AND ff!=null) "
    		+ "		OR (:idBaliza=0 OR hob.baliza.id=:idBaliza) "
    		+ "		OR (:fi=null and :ff=null) "
    		+ "	) "
    		+ ") ")
    List<HistoricoObjetivosBalizas> tomarBalizasHistorico(int idObjetivo,int idBaliza,@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fi,@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime ff);
}
