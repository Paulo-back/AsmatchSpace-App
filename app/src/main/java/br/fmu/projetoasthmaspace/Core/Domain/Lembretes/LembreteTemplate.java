package br.fmu.projetoasthmaspace.Core.Domain.Lembretes;

public class LembreteTemplate {
    public Long id;
    public String titulo;
    public String horario;
    public String dataInicio;
    public String dataFim;

    public String statusHoje;
    public String tipoRecorrencia; // "NENHUMA", "DIARIA", "SEMANAL"
    public String diasSemana;      // "1,3,5" etc.

    public String getHorarioFormatado() {
        if (horario == null) return "";
        return horario.length() >= 5 ? horario.substring(0, 5) : horario;
    }
}