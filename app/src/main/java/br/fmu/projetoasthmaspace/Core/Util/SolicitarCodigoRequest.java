package br.fmu.projetoasthmaspace.Core.Util;

public class SolicitarCodigoRequest {
    private String email;

    public SolicitarCodigoRequest(String email) {
        this.email = email;
    }

    public String getEmail() { return email; }
}