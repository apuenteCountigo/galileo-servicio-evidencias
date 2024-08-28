package com.galileo.cu.servicioevidencias.clientes;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "servicio-apis")
public interface Dataminer {
    @PostMapping("/enviarNombZip")
    public String enviarNombresCSV(@RequestParam Integer idDataminer, @RequestParam Integer idElement,
            @RequestParam String nombreZip);

    @PostMapping("/estadoEnvioNombZip")
    public String testZip(@RequestParam Integer idDataminer, @RequestParam Integer idElement);
}
