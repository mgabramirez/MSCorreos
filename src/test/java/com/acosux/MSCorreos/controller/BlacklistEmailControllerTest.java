package com.acosux.MSCorreos.controller;

import com.acosux.MSCorreos.service.BlacklistEmailService;
import com.acosux.MSCorreos.util.RespuestaWebTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests unitarios para BlacklistEmailController.
 * Valida el endpoint GET /api/v1/blacklist-emails/count.
 */
@WebMvcTest(BlacklistEmailController.class)
public class BlacklistEmailControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private BlacklistEmailService blacklistService;
    
    /**
     * Test para el endpoint GET /api/v1/blacklist-emails/count.
     * Verifica que retorna 200 OK con el conteo correcto.
     */
    @Test
    public void testCountEmails_ReturnsCorrectCount() throws Exception {
        // Arrange
        long expectedCount = 42L;
        when(blacklistService.countEmails()).thenReturn(expectedCount);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/blacklist-emails/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoOperacion").value(RespuestaWebTO.EstadoOperacionEnum.EXITO.getValor()))
                .andExpect(jsonPath("$.extraInfo.count").value(expectedCount));
    }
    
    /**
     * Test para el endpoint GET /api/v1/blacklist-emails/count con conteo cero.
     * Verifica que retorna 200 OK con count = 0.
     */
    @Test
    public void testCountEmails_ReturnsZeroWhenEmpty() throws Exception {
        // Arrange
        when(blacklistService.countEmails()).thenReturn(0L);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/blacklist-emails/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoOperacion").value(RespuestaWebTO.EstadoOperacionEnum.EXITO.getValor()))
                .andExpect(jsonPath("$.extraInfo.count").value(0));
    }
}
