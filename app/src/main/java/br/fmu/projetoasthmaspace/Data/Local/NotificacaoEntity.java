package br.fmu.projetoasthmaspace.Data.Local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notificacoes")
public class NotificacaoEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String titulo;
    public String mensagem;
    public String dataHora;  // "dd/MM/yyyy HH:mm"
    public boolean lida;
    public String tipo;      // "AR" ou "LEMBRETE"
    public long templateId;  // -1 = sem vínculo (ex: notificações de AR)

    public NotificacaoEntity(String titulo, String mensagem, String dataHora, String tipo) {
        this.titulo     = titulo;
        this.mensagem   = mensagem;
        this.dataHora   = dataHora;
        this.lida       = false;
        this.tipo       = tipo;
        this.templateId = -1;
    }



}
