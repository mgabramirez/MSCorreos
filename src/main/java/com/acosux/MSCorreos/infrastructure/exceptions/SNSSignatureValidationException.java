package com.acosux.MSCorreos.infrastructure.exceptions;

/**
 * Excepción lanzada cuando la validación de firma de un mensaje SNS falla.
 *
 * <p>Se lanza en los siguientes casos:</p>
 * <ul>
 *   <li>La URL del certificado no pertenece al dominio amazonaws.com</li>
 *   <li>La URL del certificado no usa HTTPS</li>
 *   <li>El certificado X.509 no puede descargarse o parsearse</li>
 *   <li>La firma Base64 no puede decodificarse</li>
 *   <li>La verificación SHA1withRSA falla (firma inválida)</li>
 * </ul>
 *
 * <p>Requirements: 13.2, 13.6</p>
 *
 * @author MSCorreos Team
 */
public class SNSSignatureValidationException extends RuntimeException {

    /**
     * Crea una excepción con el mensaje especificado.
     *
     * @param message Descripción del error de validación
     */
    public SNSSignatureValidationException(String message) {
        super(message);
    }

    /**
     * Crea una excepción con mensaje y causa raíz.
     *
     * @param message Descripción del error de validación
     * @param cause   Excepción original que causó el fallo
     */
    public SNSSignatureValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
