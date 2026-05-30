package br.fmu.projetoasthmaspace.Core.Domain.Lembretes;
//PODE SER DELETADA
public class LembreteResponse {
    public Long id;
    public String titulo;
    public String data;
    public String horario;
    public boolean concluido;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getHorario() {
        return horario;
    }

    public void setHorario(String horario) {
        this.horario = horario;
    }

    public boolean isConcluido() {
        return this.concluido;
    }


    public void setConcluido(boolean concluido) {
        this.concluido = concluido;
    }

    public String getHorarioFormatado() {
        if (horario == null) return "";
        return horario.length() >= 5 ? horario.substring(0, 5) : horario;
    }

}
