package com.acosux.MSCorreos.valueobjects;

import java.io.Serializable;
import java.util.Objects;

/**
 * Value Object que representa una Clave de Acceso de comprobante electrónico.
 * La clave de acceso es un identificador único de 49 dígitos numéricos utilizado
 * en el sistema de facturación electrónica ecuatoriano (SRI).
 * 
 * Estructura de la clave de acceso (49 dígitos):
 * - Posiciones 1-8: Fecha de emisión (ddmmaaaa)
 * - Posiciones 9-10: Tipo de comprobante
 * - Posiciones 11-23: RUC del emisor (13 dígitos)
 * - Posiciones 24-25: Tipo de ambiente (01=Pruebas, 02=Producción)
 * - Posiciones 26-34: Serie del comprobante (establecimiento + punto emisión + secuencial)
 * - Posiciones 35-42: Número del comprobante (8 dígitos)
 * - Posiciones 43-48: Código numérico (6 dígitos aleatorios)
 * - Posición 49: Dígito verificador (módulo 11)
 * 
 * Este objeto es inmutable y garantiza que solo contenga claves de acceso válidas.
 * 
 * @author MSCorreos Team
 * @version 1.0
 */
public class ClaveAcceso implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Longitud requerida de la clave de acceso según normativa SRI.
     */
    public static final int LONGITUD_CLAVE = 49;
    
    private final String value;
    
    /**
     * Constructor que crea una ClaveAcceso validando el formato.
     * 
     * @param clave La clave de acceso a validar (49 dígitos)
     * @throws IllegalArgumentException si la clave es nula, no tiene 49 dígitos o contiene caracteres no numéricos
     */
    public ClaveAcceso(String clave) {
        if (clave == null) {
            throw new IllegalArgumentException("Clave de acceso no puede ser nula");
        }
        
        String trimmedClave = clave.trim();
        
        if (trimmedClave.length() != LONGITUD_CLAVE) {
            throw new IllegalArgumentException(
                String.format("Clave de acceso debe tener exactamente %d dígitos. Recibido: %d dígitos", 
                    LONGITUD_CLAVE, trimmedClave.length())
            );
        }
        
        if (!trimmedClave.matches("\\d{" + LONGITUD_CLAVE + "}")) {
            throw new IllegalArgumentException(
                "Clave de acceso debe contener solo dígitos numéricos (0-9)"
            );
        }
        
        this.value = trimmedClave;
    }
    
    /**
     * Obtiene el valor completo de la clave de acceso.
     * 
     * @return La clave de acceso de 49 dígitos
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Obtiene la fecha de emisión del comprobante (posiciones 1-8).
     * Formato: ddmmaaaa
     * 
     * @return La fecha de emisión como String de 8 dígitos
     */
    public String getFechaEmision() {
        return value.substring(0, 8);
    }
    
    /**
     * Obtiene el tipo de comprobante (posiciones 9-10).
     * 
     * Códigos comunes:
     * - 01: Factura
     * - 03: Liquidación de compra
     * - 04: Nota de crédito
     * - 05: Nota de débito
     * - 06: Guía de remisión
     * - 07: Comprobante de retención
     * 
     * @return El código de tipo de comprobante (2 dígitos)
     */
    public String getTipoComprobante() {
        return value.substring(8, 10);
    }
    
    /**
     * Obtiene el RUC del emisor (posiciones 11-23).
     * 
     * @return El RUC del emisor (13 dígitos)
     */
    public String getRucEmisor() {
        return value.substring(10, 23);
    }
    
    /**
     * Obtiene el tipo de ambiente (posiciones 24-25).
     * 
     * @return El tipo de ambiente: "01" (Pruebas) o "02" (Producción)
     */
    public String getTipoAmbiente() {
        return value.substring(23, 25);
    }
    
    /**
     * Verifica si la clave corresponde a un ambiente de producción.
     * 
     * @return true si es ambiente de producción (02), false si es pruebas (01)
     */
    public boolean esProduccion() {
        return "02".equals(getTipoAmbiente());
    }
    
    /**
     * Obtiene la serie del comprobante (posiciones 26-34).
     * Incluye: establecimiento (3) + punto emisión (3) + secuencial (3)
     * 
     * @return La serie del comprobante (9 dígitos)
     */
    public String getSerie() {
        return value.substring(25, 34);
    }
    
    /**
     * Obtiene el establecimiento (primeros 3 dígitos de la serie).
     * 
     * @return El código de establecimiento (3 dígitos)
     */
    public String getEstablecimiento() {
        return value.substring(25, 28);
    }
    
    /**
     * Obtiene el punto de emisión (dígitos 4-6 de la serie).
     * 
     * @return El código de punto de emisión (3 dígitos)
     */
    public String getPuntoEmision() {
        return value.substring(28, 31);
    }
    
    /**
     * Obtiene el secuencial del comprobante (posiciones 35-42).
     * 
     * @return El número secuencial del comprobante (8 dígitos)
     */
    public String getSecuencial() {
        return value.substring(34, 42);
    }
    
    /**
     * Obtiene el código numérico aleatorio (posiciones 43-48).
     * 
     * @return El código numérico de 6 dígitos
     */
    public String getCodigoNumerico() {
        return value.substring(42, 48);
    }
    
    /**
     * Obtiene el dígito verificador (posición 49).
     * 
     * @return El dígito verificador calculado con módulo 11
     */
    public String getDigitoVerificador() {
        return value.substring(48, 49);
    }
    
    /**
     * Valida el dígito verificador usando el algoritmo módulo 11.
     * 
     * @return true si el dígito verificador es correcto
     */
    public boolean validarDigitoVerificador() {
        try {
            String claveBase = value.substring(0, 48);
            int digitoCalculado = calcularDigitoVerificador(claveBase);
            int digitoActual = Integer.parseInt(getDigitoVerificador());
            return digitoCalculado == digitoActual;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Calcula el dígito verificador usando el algoritmo módulo 11.
     * 
     * @param claveBase Los primeros 48 dígitos de la clave
     * @return El dígito verificador calculado
     */
    private int calcularDigitoVerificador(String claveBase) {
        int suma = 0;
        int factor = 2;
        
        // Recorrer de derecha a izquierda
        for (int i = claveBase.length() - 1; i >= 0; i--) {
            int digito = Character.getNumericValue(claveBase.charAt(i));
            suma += digito * factor;
            factor = (factor == 7) ? 2 : factor + 1;
        }
        
        int residuo = suma % 11;
        int resultado = 11 - residuo;
        
        // Si el resultado es 11, el dígito verificador es 0
        // Si el resultado es 10, el dígito verificador es 1
        if (resultado == 11) {
            return 0;
        } else if (resultado == 10) {
            return 1;
        } else {
            return resultado;
        }
    }
    
    /**
     * Genera una representación formateada de la clave de acceso.
     * Formato: XXXXXXXX-XX-XXXXXXXXXXXXX-XX-XXXXXXXXX-XXXXXXXX-XXXXXX-X
     * 
     * @return La clave de acceso formateada con guiones
     */
    public String toFormattedString() {
        return String.format("%s-%s-%s-%s-%s-%s-%s-%s",
            getFechaEmision(),
            getTipoComprobante(),
            getRucEmisor(),
            getTipoAmbiente(),
            getSerie(),
            getSecuencial(),
            getCodigoNumerico(),
            getDigitoVerificador()
        );
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClaveAcceso that = (ClaveAcceso) o;
        return Objects.equals(value, that.value);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
    
    @Override
    public String toString() {
        return value;
    }
    
    /**
     * Método estático para validar una clave de acceso sin crear una instancia.
     * 
     * @param clave La clave a validar
     * @return true si la clave es válida (49 dígitos numéricos)
     */
    public static boolean isValidClaveAcceso(String clave) {
        try {
            new ClaveAcceso(clave);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
