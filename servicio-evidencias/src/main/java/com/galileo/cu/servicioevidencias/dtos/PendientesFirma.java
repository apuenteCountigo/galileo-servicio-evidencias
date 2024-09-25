package com.galileo.cu.servicioevidencias.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PendientesFirma {
    public String ficheros;
    public int idDMA;
    public int idElement;
}
