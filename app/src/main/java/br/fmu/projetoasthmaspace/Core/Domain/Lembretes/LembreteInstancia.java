package br.fmu.projetoasthmaspace.Core.Domain.Lembretes;

public class LembreteInstancia {
    public Long instanciaId;
    public Long templateId;
    public String titulo;
    public String horario;
    public String data;
    public String status;
    public String tipoRecorrencia; // ← adicione esta linha

    public boolean isConcluido() {
        return "CONCLUIDO".equals(status);
    }

    public String getHorarioFormatado() {
        if (horario == null) return "";
        return horario.length() >= 5 ? horario.substring(0, 5) : horario;
    }
}