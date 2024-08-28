package com.galileo.cu.servicioevidencias.repositorios;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.net.ftp.FTPClient;

import com.galileo.cu.commons.models.Operaciones;

public class ProgEvidens {
    public static Map<Long, Integer> progEvi = new HashMap<Long, Integer>();
    public static Map<Long, List<String>> ficherosPendientes = new HashMap<Long, List<String>>();
    public static Map<Long, String> advertencias = new HashMap<Long, String>();
    public static Map<Long, Operaciones> operacion = new HashMap<Long, Operaciones>();
    public static Map<Long, String> zipPendiente = new HashMap<Long, String>();
    public static FTPClient ftp = null;
    public static FTPClient ftpZip = null;
}
