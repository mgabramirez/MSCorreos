/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.acosux.MSCorreos.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.ResponseBody;
import com.acosux.MSCorreos.service.CorreosNotificacionesService;

/**
 *
 * @author mario
 */
@RestController
@RequestMapping("/correosController/")
public class CorreosController {

    @Autowired
    CorreosNotificacionesService correosService;

    @RequestMapping("/amazonSNS")
    public @ResponseBody
    String amazonSNS(HttpServletResponse response, @RequestBody String json) {
        try {
            correosService.insertarNotificacion(json);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        return null;
    }

}
