package br.fmu.projetoasthmaspace.Core.Domain.Lembretes;

public class LembreteTemplateRequest {
    public String titulo;
    public String horario;
    public String dataInicio;
    public String dataFim;
    public String tipoRecorrencia;
    public String diasSemana;

    // Lembrete simples (sem recorrência)
    public LembreteTemplateRequest(String titulo, String horario, String dataInicio) {
        this.titulo          = titulo;
        this.horario         = horario;
        this.dataInicio      = dataInicio;
        this.tipoRecorrencia = "NENHUMA";
    }

    // Lembrete com recorrência
    public LembreteTemplateRequest(String titulo, String horario, String dataInicio,
                                   String dataFim, String tipoRecorrencia, String diasSemana) {
        this.titulo          = titulo;
        this.horario         = horario;
        this.dataInicio      = dataInicio;
        this.dataFim         = dataFim;
        this.tipoRecorrencia = tipoRecorrencia;
        this.diasSemana      = diasSemana;
    }
}