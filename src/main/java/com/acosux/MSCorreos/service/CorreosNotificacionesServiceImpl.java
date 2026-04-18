/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.acosux.MSCorreos.service;

import com.acosux.MSCorreos.dao.CorreosNotificacionDao;
import com.acosux.MSCorreos.entidades.CorreosNotificaciones;
import com.acosux.MSCorreos.entidades.TipoNotificacion;
import com.acosux.MSCorreos.util.CorreosUtil;
import com.acosux.MSCorreos.util.UtilsJSON;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author mario
 */
@Service
public class CorreosNotificacionesServiceImpl implements CorreosNotificacionesService {

    @Autowired
    private CorreosNotificacionDao notificacionDao;
    
    @Autowired
    private BlacklistEmailService blacklistService;

    @Override
    public void insertarNotificacion(String json) throws Exception {
        Map<String, Object> map = UtilsJSON.jsonToMap(json);
        //confrmacion
        String confirmacion = UtilsJSON.jsonToObjeto(String.class, map.get("SubscribeURL"));
        if (confirmacion != null) {
            System.out.println("SubscribeURL:\n\n" + confirmacion + "\n\n");
        }
        //extraemos mensaje para procesar la insercion
        Map<String, Object> mensaje = UtilsJSON.jsonToMap(UtilsJSON.jsonToObjeto(String.class, map.get("Message")));
//        Map<String, Object> mensaje = (Map<String, Object>) map.get("Message");
        if (mensaje != null) {
            String tipoEvento = (String) mensaje.get("eventType");
            Map<String, Object> mail = (Map<String, Object>) mensaje.get("mail");
            if (mail != null) {
                Map<String, Object> tags = (Map<String, Object>) mail.get("tags");
                String claveAcceso = tags == null ? null : (tags.get("ows-clave-acceso") == null ? null : tags.get("ows-clave-acceso").toString());
                String tipoNotificacion = "";
                if (tags != null) {
                    ArrayList<String> tipoNotificaciones = tags.get("ows-tipo-notificacion") == null ? new ArrayList<>() : (ArrayList<String>) tags.get("ows-tipo-notificacion");
                    if (tipoNotificaciones != null && !tipoNotificaciones.isEmpty()) {
                        tipoNotificacion = tipoNotificaciones.get(0);
                    }
                }
                if (TipoNotificacion.getTipoNotificacion(tipoNotificacion) != null) {
                    insertarNotificaciones(tipoEvento, json, tags, mail, mensaje, tipoNotificacion);
                } else {
                    if (claveAcceso == null) {
                    } else {
                        if (claveAcceso.substring(9, 11).equalsIgnoreCase("07")) {
                            tipoNotificacion = TipoNotificacion.NOTIFICAR_COMPRA_ELECTRONICA_EMITIDA.getNombre();
                        } else if (claveAcceso.substring(9, 11).equalsIgnoreCase("03")) {
                            tipoNotificacion = TipoNotificacion.NOTIFICAR_LIQUIDACION_COMPRA.getNombre();
                        } else if (claveAcceso.substring(9, 11).equalsIgnoreCase("06")) {
                            tipoNotificacion = TipoNotificacion.NOTIFICAR_GUIA_REMISION.getNombre();
                        } else {
                            tipoNotificacion = TipoNotificacion.NOTIFICAR_VENTA_ELECTRONICA_EMITIDA.getNombre();
                        }
                        insertarNotificaciones(tipoEvento, json, tags, mail, mensaje, tipoNotificacion);
                    }
                }
            }
        }
    }

    private void insertarNotificaciones(String tipoEvento, String json, Map<String, Object> tags, Map<String, Object> mail, Map<String, Object> mensaje, String tipoNotificacion) throws Exception {
        CorreosNotificaciones notificacionVenta = new CorreosNotificaciones();
        String empresa = obtenerAtributo((ArrayList<Object>) tags.get("ows-empresa"));
        String ruc = obtenerAtributo((ArrayList<Object>) tags.get("ows-ruc"));
        String clave = obtenerAtributo((ArrayList<Object>) tags.get("ows-clave"));

        notificacionVenta.setnTipo(tipoEvento);
        notificacionVenta.setnEmpresa(empresa);
        notificacionVenta.setnRuc(ruc);
        notificacionVenta.setnClave(clave);
        notificacionVenta.setnInforme(json);
        String tipoNot = TipoNotificacion.obtenerValorTipoNotificacion(tipoNotificacion);
        notificacionVenta.setnTipoNotificacion(tipoNot);

        Map<String, Object> commonheaders = (Map<String, Object>) mail.get("commonHeaders");

        switch (tipoEvento) {
            case "Send":
                String destinatarioSend = commonheaders.get("to").toString();
                // Verificar blacklist antes de registrar
                if (blacklistService.isEmailBloqueado(destinatarioSend)) {
                    System.out.println("Email en blacklist, no se procesará: " + destinatarioSend);
                    return;
                }
                notificacionVenta.setnDestinatario(destinatarioSend);
                String f = (String) mail.get("timestamp");
                notificacionVenta.setnFecha(CorreosUtil.timestamp(f));
                notificacionDao.insertar(notificacionVenta);
                break;
            case "Delivery":
                Map<String, Object> delivery = (Map<String, Object>) mensaje.get("delivery");
                String destinatarioDelivery = delivery.get("recipients").toString();
                if (blacklistService.isEmailBloqueado(destinatarioDelivery)) {
                    System.out.println("Email en blacklist, no se procesará: " + destinatarioDelivery);
                    return;
                }
                notificacionVenta.setnDestinatario(destinatarioDelivery);
                String f2 = (String) delivery.get("timestamp");
                notificacionVenta.setnFecha(CorreosUtil.timestamp(f2));
                notificacionDao.insertar(notificacionVenta);
                break;
            case "Open":
                Map<String, Object> open = (Map<String, Object>) mensaje.get("open");
                String destinatarioOpen = commonheaders.get("to").toString();
                if (blacklistService.isEmailBloqueado(destinatarioOpen)) {
                    System.out.println("Email en blacklist, no se procesará: " + destinatarioOpen);
                    return;
                }
                notificacionVenta.setnDestinatario(destinatarioOpen);
                String f3 = (String) open.get("timestamp");
                notificacionVenta.setnFecha(CorreosUtil.timestamp(f3));
                notificacionDao.insertar(notificacionVenta);
                break;
            case "Bounce":
                Map<String, Object> bounce = (Map<String, Object>) mensaje.get("bounce");
                if (bounce != null) {
                    List<Map<String, Object>> bouncedRecipients = (List<Map<String, Object>>) bounce.get("bouncedRecipients");
                    if (bouncedRecipients != null && !bouncedRecipients.isEmpty()) {
                        for (Map<String, Object> bouncedRecipient : bouncedRecipients) {
                            CorreosNotificaciones notificacion = new CorreosNotificaciones();
                            notificacion.setnEmpresa(empresa);
                            notificacion.setnRuc(ruc);
                            notificacion.setnClave(clave);
                            notificacion.setnInforme(json);
                            notificacion.setnTipoNotificacion(tipoNot);
                            notificacion.setnTipo(tipoEvento + bounce.get("bounceType").toString());
                            String f4 = (String) bounce.get("timestamp");
                            notificacion.setnFecha(CorreosUtil.timestamp(f4));
                            Map<String, Object> recipiente = bouncedRecipient;
                            String emailAddress = recipiente.get("emailAddress").toString();
                            notificacion.setnDestinatario(emailAddress);
                            notificacion.setnObservacion(recipiente.get("diagnosticCode").toString());
                            
                            // Auto-agregar a blacklist si es bounce permanente
                            String bounceType = bounce.get("bounceType").toString();
                            if ("permanent".equalsIgnoreCase(bounceType)) {
                                try {
                                    blacklistService.agregarEmail(emailAddress, "Bounce permanente: " + bounce.get("bounceType"), "sistema");
                                    System.out.println("Email agregado a blacklist por bounce permanente: " + emailAddress);
                                } catch (Exception e) {
                                    System.out.println("Error al agregar a blacklist: " + e.getMessage());
                                }
                            }
                            
                            notificacionDao.insertar(notificacion);
                        }
                    }
                }
                break;
            case "Complaint":
                Map<String, Object> complaint = (Map<String, Object>) mensaje.get("complaint");
                if (complaint != null) {
                    List<Map<String, Object>> complaintRecipients = (List<Map<String, Object>>) complaint.get("complainedRecipients");
                    ArrayList<String> emisores = (ArrayList<String>) commonheaders.get("replyTo");
                    if (complaintRecipients != null && !complaintRecipients.isEmpty()) {
                        for (Map<String, Object> complaintRecipient : complaintRecipients) {
                            CorreosNotificaciones notificacion = new CorreosNotificaciones();
                            notificacion.setnEmpresa(empresa);
                            notificacion.setnRuc(ruc);
                            notificacion.setnClave(clave);
                            notificacion.setnInforme(json);
                            notificacion.setnTipo(tipoEvento);
                            notificacion.setnTipoNotificacion(tipoNot);
                            String f4 = (String) complaint.get("timestamp");
                            notificacion.setnFecha(CorreosUtil.timestamp(f4));
                            Map<String, Object> recipiente = complaintRecipient;
                            String emailAddress = recipiente.get("emailAddress").toString();
                            notificacion.setnDestinatario(emailAddress);
                            
                            // Auto-agregar a blacklist por complaint
                            try {
                                blacklistService.agregarEmail(emailAddress, "Complaint (reclamo)", "sistema");
                                System.out.println("Email agregado a blacklist por complaint: " + emailAddress);
                            } catch (Exception e) {
                                System.out.println("Error al agregar a blacklist: " + e.getMessage());
                            }
                            
                            notificacionDao.insertar(notificacion);
                        }
                    }
                }
                break;
        }
    }

    private String obtenerAtributo(ArrayList<Object> get) {
        return (String) get.get(0);
    }

}
