package com.acosux.MSCorreos.enums;

/**
 * Enumeración que representa las acciones que se pueden realizar sobre la lista negra.
 * 
 * Estas acciones se registran en la tabla cor_lista_negra_historial para mantener
 * un historial completo de auditoría de todos los cambios realizados en la lista negra.
 */
public enum AccionListaNegra {
    
    /**
     * Agregar un correo electrónico a la lista negra.
     * 
     * Esta acción puede ser:
     * - Automática: Generada por el sistema al recibir eventos de Bounce Permanent,
     *   Complaint, o al alcanzar 3+ Soft Bounces en 30 días
     * - Manual: Realizada por un administrador mediante la API REST
     * 
     * Efecto: El correo queda bloqueado y no se deben enviar correos a esa dirección.
     */
    AGREGAR("Agregar a lista negra"),
    
    /**
     * Remover un correo electrónico de la lista negra.
     * 
     * Esta acción:
     * - Siempre es manual: Realizada por un administrador mediante la API REST
     * - Desactiva el bloqueo: Marca el registro como activo=false en lugar de eliminarlo
     * - Preserva historial: El registro permanece en la base de datos para auditoría
     * 
     * Efecto: El correo queda desbloqueado y se pueden enviar correos nuevamente.
     * 
     * Nota: La remoción debe hacerse con precaución, especialmente para correos
     * bloqueados por Hard Bounce o Complaint, ya que pueden afectar la reputación
     * del remitente en Amazon SES.
     */
    REMOVER("Remover de lista negra");
    
    private final String descripcion;
    
    AccionListaNegra(String descripcion) {
        this.descripcion = descripcion;
    }
    
    /**
     * Obtiene la descripción legible de la acción.
     * 
     * @return Descripción de la acción
     */
    public String getDescripcion() {
        return descripcion;
    }
    
    /**
     * Determina si la acción es de tipo agregado.
     * 
     * @return true si la acción es AGREGAR
     */
    public boolean esAgregar() {
        return this == AGREGAR;
    }
    
    /**
     * Determina si la acción es de tipo remoción.
     * 
     * @return true si la acción es REMOVER
     */
    public boolean esRemover() {
        return this == REMOVER;
    }
    
    /**
     * Obtiene el valor booleano de 'activo' correspondiente a la acción.
     * 
     * @return true si la acción es AGREGAR (activo=true), false si es REMOVER (activo=false)
     */
    public boolean getValorActivo() {
        return this == AGREGAR;
    }
}
