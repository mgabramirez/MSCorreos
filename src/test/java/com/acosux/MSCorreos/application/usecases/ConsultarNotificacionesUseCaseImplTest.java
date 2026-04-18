package com.acosux.MSCorreos.application.usecases;

import com.acosux.MSCorreos.dtos.FiltrosNotificacion;
import com.acosux.MSCorreos.dtos.NotificacionDTO;
import com.acosux.MSCorreos.dtos.NotificacionDetalleDTO;
import com.acosux.MSCorreos.entidades.CorreosNotificaciones;
import com.acosux.MSCorreos.infrastructure.exceptions.NotificacionNotFoundException;
import com.acosux.MSCorreos.repositories.NotificacionesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConsultarNotificacionesUseCaseImpl
 * 
 * Tests cover:
 * - Filtering by empresa, ruc, tipo_notificacion, fechas, destinatario, tipo
 * - Pagination
 * - DTO mapping
 * - Detail retrieval with complete JSON
 * - Error handling
 * 
 * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5
 */
@ExtendWith(MockitoExtension.class)
class ConsultarNotificacionesUseCaseImplTest {
    
    @Mock
    private NotificacionesRepository notificacionesRepository;
    
    @InjectMocks
    private ConsultarNotificacionesUseCaseImpl useCase;
    
    private CorreosNotificaciones notificacion1;
    private CorreosNotificaciones notificacion2;
    private Pageable pageable;
    
    @BeforeEach
    void setUp() {
        // Configurar datos de prueba
        notificacion1 = new CorreosNotificaciones();
        notificacion1.setnSecuencial(1);
        notificacion1.setnDestinatario("test1@example.com");
        notificacion1.setnFecha(new Date());
        notificacion1.setnTipo("Send");
        notificacion1.setnObservacion("Test observation 1");
        notificacion1.setnInforme("{\"eventType\":\"Send\"}");
        notificacion1.setnEmpresa("ACOSUX");
        notificacion1.setnRuc("1234567890001");
        notificacion1.setnClave("2024_01_001");
        notificacion1.setnTipoNotificacion("NOTIFICAR_VENTA_ELECTRONICA_EMITIDA");
        
        notificacion2 = new CorreosNotificaciones();
        notificacion2.setnSecuencial(2);
        notificacion2.setnDestinatario("test2@example.com");
        notificacion2.setnFecha(new Date());
        notificacion2.setnTipo("Delivery");
        notificacion2.setnObservacion("Test observation 2");
        notificacion2.setnInforme("{\"eventType\":\"Delivery\"}");
        notificacion2.setnEmpresa("ACOSUX");
        notificacion2.setnRuc("1234567890001");
        notificacion2.setnClave("2024_01_002");
        notificacion2.setnTipoNotificacion("NOTIFICAR_VENTA_ELECTRONICA_EMITIDA");
        
        pageable = PageRequest.of(0, 10);
    }
    
    @Test
    void testEjecutar_sinFiltros_retornaTodas() {
        // Arrange
        FiltrosNotificacion filtros = new FiltrosNotificacion();
        List<CorreosNotificaciones> notificaciones = Arrays.asList(notificacion1, notificacion2);
        Page<CorreosNotificaciones> page = new PageImpl<>(notificaciones, pageable, 2);
        
        when(notificacionesRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(page);
        
        // Act
        Page<NotificacionDTO> resultado = useCase.ejecutar(filtros, pageable);
        
        // Assert
        assertNotNull(resultado);
        assertEquals(2, resultado.getTotalElements());
        assertEquals(2, resultado.getContent().size());
        
        NotificacionDTO dto1 = resultado.getContent().get(0);
        assertEquals(1, dto1.getId());
        assertEquals("test1@example.com", dto1.getDestinatario());
        assertEquals("Send", dto1.getTipo());
        assertEquals("ACOSUX", dto1.getEmpresa());
        
        verify(notificacionesRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }
    
    @Test
    void testEjecutar_filtrarPorEmpresa_retornaFiltradas() {
        // Arrange
        FiltrosNotificacion filtros = FiltrosNotificacion.builder()
            .empresa("ACOSUX")
            .build();
        
        List<CorreosNotificaciones> notificaciones = Arrays.asList(notificacion1);
        Page<CorreosNotificaciones> page = new PageImpl<>(notificaciones, pageable, 1);
        
        when(notificacionesRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(page);
        
        // Act
        Page<NotificacionDTO> resultado = useCase.ejecutar(filtros, pageable);
        
        // Assert
        assertNotNull(resultado);
        assertEquals(1, resultado.getTotalElements());
        assertEquals("ACOSUX", resultado.getContent().get(0).getEmpresa());
        
        verify(notificacionesRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }
    
    @Test
    void testEjecutar_filtrarPorRuc_retornaFiltradas() {
        // Arrange
        FiltrosNotificacion filtros = FiltrosNotificacion.builder()
            .ruc("1234567890001")
            .build();
        
        List<CorreosNotificaciones> notificaciones = Arrays.asList(notificacion1, notificacion2);
        Page<CorreosNotificaciones> page = new PageImpl<>(notificaciones, pageable, 2);
        
        when(notificacionesRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(page);
        
        // Act
        Page<NotificacionDTO> resultado = useCase.ejecutar(filtros, pageable);
        
        // Assert
        assertNotNull(resultado);
        assertEquals(2, resultado.getTotalElements());
        resultado.getContent().forEach(dto -> 
            assertEquals("1234567890001", dto.getRuc())
        );
        
        verify(notificacionesRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }
    
    @Test
    void testEjecutar_filtrarPorTipoNotificacion_retornaFiltradas() {
        // Arrange
        FiltrosNotificacion filtros = FiltrosNotificacion.builder()
            .tipoNotificacion("NOTIFICAR_VENTA_ELECTRONICA_EMITIDA")
            .build();
        
        List<CorreosNotificaciones> notificaciones = Arrays.asList(notificacion1, notificacion2);
        Page<CorreosNotificaciones> page = new PageImpl<>(notificaciones, pageable, 2);
        
        when(notificacionesRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(page);
        
        // Act
        Page<NotificacionDTO> resultado = useCase.ejecutar(filtros, pageable);
        
        // Assert
        assertNotNull(resultado);
        assertEquals(2, resultado.getTotalElements());
        resultado.getContent().forEach(dto -> 
            assertEquals("NOTIFICAR_VENTA_ELECTRONICA_EMITIDA", dto.getTipoNotificacion())
        );
        
        verify(notificacionesRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }
    
    @Test
    void testEjecutar_filtrarPorDestinatario_retornaFiltradas() {
        // Arrange
        FiltrosNotificacion filtros = FiltrosNotificacion.builder()
            .destinatario("test1")
            .build();
        
        List<CorreosNotificaciones> notificaciones = Arrays.asList(notificacion1);
        Page<CorreosNotificaciones> page = new PageImpl<>(notificaciones, pageable, 1);
        
        when(notificacionesRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(page);
        
        // Act
        Page<NotificacionDTO> resultado = useCase.ejecutar(filtros, pageable);
        
        // Assert
        assertNotNull(resultado);
        assertEquals(1, resultado.getTotalElements());
        assertTrue(resultado.getContent().get(0).getDestinatario().contains("test1"));
        
        verify(notificacionesRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }
    
    @Test
    void testEjecutar_filtrarPorTipo_retornaFiltradas() {
        // Arrange
        FiltrosNotificacion filtros = FiltrosNotificacion.builder()
            .tipo("Send")
            .build();
        
        List<CorreosNotificaciones> notificaciones = Arrays.asList(notificacion1);
        Page<CorreosNotificaciones> page = new PageImpl<>(notificaciones, pageable, 1);
        
        when(notificacionesRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(page);
        
        // Act
        Page<NotificacionDTO> resultado = useCase.ejecutar(filtros, pageable);
        
        // Assert
        assertNotNull(resultado);
        assertEquals(1, resultado.getTotalElements());
        assertEquals("Send", resultado.getContent().get(0).getTipo());
        
        verify(notificacionesRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }
    
    @Test
    void testEjecutar_filtrarPorRangoFechas_retornaFiltradas() {
        // Arrange
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -1);
        Date fechaInicio = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, 2);
        Date fechaFin = cal.getTime();
        
        FiltrosNotificacion filtros = FiltrosNotificacion.builder()
            .fechaInicio(fechaInicio)
            .fechaFin(fechaFin)
            .build();
        
        List<CorreosNotificaciones> notificaciones = Arrays.asList(notificacion1, notificacion2);
        Page<CorreosNotificaciones> page = new PageImpl<>(notificaciones, pageable, 2);
        
        when(notificacionesRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(page);
        
        // Act
        Page<NotificacionDTO> resultado = useCase.ejecutar(filtros, pageable);
        
        // Assert
        assertNotNull(resultado);
        assertEquals(2, resultado.getTotalElements());
        
        verify(notificacionesRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }
    
    @Test
    void testEjecutar_filtrosMultiples_retornaFiltradas() {
        // Arrange
        FiltrosNotificacion filtros = FiltrosNotificacion.builder()
            .empresa("ACOSUX")
            .ruc("1234567890001")
            .tipoNotificacion("NOTIFICAR_VENTA_ELECTRONICA_EMITIDA")
            .tipo("Send")
            .build();
        
        List<CorreosNotificaciones> notificaciones = Arrays.asList(notificacion1);
        Page<CorreosNotificaciones> page = new PageImpl<>(notificaciones, pageable, 1);
        
        when(notificacionesRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(page);
        
        // Act
        Page<NotificacionDTO> resultado = useCase.ejecutar(filtros, pageable);
        
        // Assert
        assertNotNull(resultado);
        assertEquals(1, resultado.getTotalElements());
        
        NotificacionDTO dto = resultado.getContent().get(0);
        assertEquals("ACOSUX", dto.getEmpresa());
        assertEquals("1234567890001", dto.getRuc());
        assertEquals("NOTIFICAR_VENTA_ELECTRONICA_EMITIDA", dto.getTipoNotificacion());
        assertEquals("Send", dto.getTipo());
        
        verify(notificacionesRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }
    
    @Test
    void testEjecutar_paginacion_retornaPaginaCorrecta() {
        // Arrange
        FiltrosNotificacion filtros = new FiltrosNotificacion();
        Pageable pageableCustom = PageRequest.of(1, 1); // Segunda página, 1 elemento por página
        
        List<CorreosNotificaciones> notificaciones = Arrays.asList(notificacion2);
        Page<CorreosNotificaciones> page = new PageImpl<>(notificaciones, pageableCustom, 2);
        
        when(notificacionesRepository.findAll(any(Specification.class), eq(pageableCustom)))
            .thenReturn(page);
        
        // Act
        Page<NotificacionDTO> resultado = useCase.ejecutar(filtros, pageableCustom);
        
        // Assert
        assertNotNull(resultado);
        assertEquals(2, resultado.getTotalElements());
        assertEquals(1, resultado.getContent().size());
        assertEquals(1, resultado.getNumber()); // Página 1 (0-indexed)
        
        verify(notificacionesRepository, times(1)).findAll(any(Specification.class), eq(pageableCustom));
    }
    
    @Test
    void testEjecutar_sinResultados_retornaPaginaVacia() {
        // Arrange
        FiltrosNotificacion filtros = FiltrosNotificacion.builder()
            .empresa("EMPRESA_NO_EXISTE")
            .build();
        
        Page<CorreosNotificaciones> page = new PageImpl<>(Collections.emptyList(), pageable, 0);
        
        when(notificacionesRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(page);
        
        // Act
        Page<NotificacionDTO> resultado = useCase.ejecutar(filtros, pageable);
        
        // Assert
        assertNotNull(resultado);
        assertEquals(0, resultado.getTotalElements());
        assertTrue(resultado.getContent().isEmpty());
        
        verify(notificacionesRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }
    
    @Test
    void testObtenerDetalle_idValido_retornaDetalle() throws NotificacionNotFoundException {
        // Arrange
        Integer id = 1;
        when(notificacionesRepository.findById(id))
            .thenReturn(Optional.of(notificacion1));
        
        // Act
        NotificacionDetalleDTO detalle = useCase.obtenerDetalle(id);
        
        // Assert
        assertNotNull(detalle);
        assertEquals(1, detalle.getId());
        assertEquals("test1@example.com", detalle.getDestinatario());
        assertEquals("Send", detalle.getTipo());
        assertEquals("ACOSUX", detalle.getEmpresa());
        assertEquals("{\"eventType\":\"Send\"}", detalle.getInformeJson());
        
        verify(notificacionesRepository, times(1)).findById(id);
    }
    
    @Test
    void testObtenerDetalle_idNoExiste_lanzaExcepcion() {
        // Arrange
        Integer id = 999;
        when(notificacionesRepository.findById(id))
            .thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(NotificacionNotFoundException.class, () -> {
            useCase.obtenerDetalle(id);
        });
        
        verify(notificacionesRepository, times(1)).findById(id);
    }
    
    @Test
    void testObtenerDetalle_idNulo_lanzaExcepcion() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            useCase.obtenerDetalle(null);
        });
        
        verify(notificacionesRepository, never()).findById(any());
    }
    
    @Test
    void testObtenerDetalle_incluyeJsonCompleto() throws NotificacionNotFoundException {
        // Arrange
        Integer id = 1;
        String jsonCompleto = "{\"eventType\":\"Send\",\"mail\":{\"messageId\":\"123\"},\"timestamp\":\"2024-01-01T10:00:00Z\"}";
        notificacion1.setnInforme(jsonCompleto);
        
        when(notificacionesRepository.findById(id))
            .thenReturn(Optional.of(notificacion1));
        
        // Act
        NotificacionDetalleDTO detalle = useCase.obtenerDetalle(id);
        
        // Assert
        assertNotNull(detalle);
        assertEquals(jsonCompleto, detalle.getInformeJson());
        
        verify(notificacionesRepository, times(1)).findById(id);
    }
    
    @Test
    void testMapToDTO_todosLosCampos() {
        // Arrange
        FiltrosNotificacion filtros = new FiltrosNotificacion();
        List<CorreosNotificaciones> notificaciones = Arrays.asList(notificacion1);
        Page<CorreosNotificaciones> page = new PageImpl<>(notificaciones, pageable, 1);
        
        when(notificacionesRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(page);
        
        // Act
        Page<NotificacionDTO> resultado = useCase.ejecutar(filtros, pageable);
        
        // Assert
        NotificacionDTO dto = resultado.getContent().get(0);
        assertEquals(notificacion1.getnSecuencial(), dto.getId());
        assertEquals(notificacion1.getnDestinatario(), dto.getDestinatario());
        assertEquals(notificacion1.getnFecha(), dto.getFecha());
        assertEquals(notificacion1.getnTipo(), dto.getTipo());
        assertEquals(notificacion1.getnObservacion(), dto.getObservacion());
        assertEquals(notificacion1.getnEmpresa(), dto.getEmpresa());
        assertEquals(notificacion1.getnRuc(), dto.getRuc());
        assertEquals(notificacion1.getnClave(), dto.getClave());
        assertEquals(notificacion1.getnTipoNotificacion(), dto.getTipoNotificacion());
    }
    
    @Test
    void testEjecutar_filtrosVacios_ignoraFiltros() {
        // Arrange
        FiltrosNotificacion filtros = FiltrosNotificacion.builder()
            .empresa("")
            .ruc("   ")
            .build();
        
        List<CorreosNotificaciones> notificaciones = Arrays.asList(notificacion1, notificacion2);
        Page<CorreosNotificaciones> page = new PageImpl<>(notificaciones, pageable, 2);
        
        when(notificacionesRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(page);
        
        // Act
        Page<NotificacionDTO> resultado = useCase.ejecutar(filtros, pageable);
        
        // Assert
        assertNotNull(resultado);
        assertEquals(2, resultado.getTotalElements());
        
        verify(notificacionesRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }
}
