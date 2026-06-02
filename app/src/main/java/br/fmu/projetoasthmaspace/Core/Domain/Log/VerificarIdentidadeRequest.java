package br.fmu.projetoasthmaspace.Core.Domain.Log;


public class VerificarIdentidadeRequest {
    private String email;
    private String dataNascimento;
    private String cpf;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDataNascimento() { return dataNascimento; }
    public void setDataNascimento(String dataNascimento) { this.dataNascimento = dataNascimento; } // fix

    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }

}
