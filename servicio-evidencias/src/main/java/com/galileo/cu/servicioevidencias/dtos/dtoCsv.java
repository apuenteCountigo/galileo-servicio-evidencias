package com.galileo.cu.servicioevidencias.dtos;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor
@Value
public class dtoCsv {
    public String dispositivo;
    public String fechaCaptacion;
    public String latitud;
    public String longitud;
    public String servidor;
    public String timestamp;
    public String satelites;
    public String presicion;
    public String evento;
    public String velocidad;
    public String rumbo;
    public String celdaId;
}
