package com.example.auditpanel.model;

/**
 * Represents a single banking transaction log entry.
 * Format (pipe-separated): transaction_id|timestamp|cuenta_origen|cuenta_destino|
 *   importe|divisa|tipo_operacion|ip_origen|dispositivo_id|pais_origen|pais_destino|
 *   sesion_id|concepto|estado
 *
 * tipos_operacion: TRANSFERENCIA, RETIRO_CAJERO, DEPOSITO, PAGO_COMERCIO, PAGO_ONLINE, CIERRE_CUENTA
 * estados: COMPLETADA, FALLIDA, PENDIENTE
 */
public class BankingTransactionEvent {

    private String transactionId;
    private String timestamp;
    private String cuentaOrigen;
    private String cuentaDestino;
    private Double importe;
    private String divisa;
    private String tipoOperacion;
    private String ipOrigen;
    private String dispositivoId;
    private String paisOrigen;
    private String paisDestino;
    private String sesionId;
    private String concepto;
    private String estado;

    public BankingTransactionEvent() {
    }

    public BankingTransactionEvent(String transactionId, String timestamp, String cuentaOrigen,
                                   String cuentaDestino, Double importe, String divisa,
                                   String tipoOperacion, String ipOrigen, String dispositivoId,
                                   String paisOrigen, String paisDestino, String sesionId,
                                   String concepto, String estado) {
        this.transactionId = transactionId;
        this.timestamp = timestamp;
        this.cuentaOrigen = cuentaOrigen;
        this.cuentaDestino = cuentaDestino;
        this.importe = importe;
        this.divisa = divisa;
        this.tipoOperacion = tipoOperacion;
        this.ipOrigen = ipOrigen;
        this.dispositivoId = dispositivoId;
        this.paisOrigen = paisOrigen;
        this.paisDestino = paisDestino;
        this.sesionId = sesionId;
        this.concepto = concepto;
        this.estado = estado;
    }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getCuentaOrigen() { return cuentaOrigen; }
    public void setCuentaOrigen(String cuentaOrigen) { this.cuentaOrigen = cuentaOrigen; }

    public String getCuentaDestino() { return cuentaDestino; }
    public void setCuentaDestino(String cuentaDestino) { this.cuentaDestino = cuentaDestino; }

    public Double getImporte() { return importe; }
    public void setImporte(Double importe) { this.importe = importe; }

    public String getDivisa() { return divisa; }
    public void setDivisa(String divisa) { this.divisa = divisa; }

    public String getTipoOperacion() { return tipoOperacion; }
    public void setTipoOperacion(String tipoOperacion) { this.tipoOperacion = tipoOperacion; }

    public String getIpOrigen() { return ipOrigen; }
    public void setIpOrigen(String ipOrigen) { this.ipOrigen = ipOrigen; }

    public String getDispositivoId() { return dispositivoId; }
    public void setDispositivoId(String dispositivoId) { this.dispositivoId = dispositivoId; }

    public String getPaisOrigen() { return paisOrigen; }
    public void setPaisOrigen(String paisOrigen) { this.paisOrigen = paisOrigen; }

    public String getPaisDestino() { return paisDestino; }
    public void setPaisDestino(String paisDestino) { this.paisDestino = paisDestino; }

    public String getSesionId() { return sesionId; }
    public void setSesionId(String sesionId) { this.sesionId = sesionId; }

    public String getConcepto() { return concepto; }
    public void setConcepto(String concepto) { this.concepto = concepto; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
