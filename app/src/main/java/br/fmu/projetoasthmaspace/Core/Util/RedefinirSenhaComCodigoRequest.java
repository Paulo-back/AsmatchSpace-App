package br.fmu.projetoasthmaspace.Core.Util;

public class RedefinirSenhaComCodigoRequest {
    private String email;
    private String codigo;
    private String novaSenha;

    public RedefinirSenhaComCodigoRequest(String email, String codigo, String novaSenha) {
        this.email     = email;
        this.codigo    = codigo;
        this.novaSenha = novaSenha;
    }

    public String getEmail()     { return email; }
    public String getCodigo()    { return codigo; }
    public String getNovaSenha() { return novaSenha; }
}