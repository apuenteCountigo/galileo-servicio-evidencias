package com.galileo.cu.servicioevidencias.repositorios;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.galileo.cu.commons.models.Operaciones;
import com.galileo.cu.servicioevidencias.dtos.PendientesFirma;

public class ProgEvidens {
    // Mapas para gestionar el progreso de forma concurrente, identificados por el
    // token
    public static final Map<String, Integer> progEvi = new ConcurrentHashMap<>();
    public static final Map<String, List<String>> ficherosPendientes = new ConcurrentHashMap<>();
    public static final Map<String, String> advertencias = new ConcurrentHashMap<>();
    public static final Map<String, Operaciones> operacion = new ConcurrentHashMap<>();
    public static final Map<String, String> zipPendiente = new ConcurrentHashMap<>();
    public static final Map<String, Boolean> isBuildingPackage = new ConcurrentHashMap<>();
    public static final Map<String, PendientesFirma> pendientesFirma = new ConcurrentHashMap<>();
}