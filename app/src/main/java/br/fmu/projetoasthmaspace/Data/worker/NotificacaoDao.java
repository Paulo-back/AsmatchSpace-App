package br.fmu.projetoasthmaspace.Data.worker;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import br.fmu.projetoasthmaspace.Data.Local.NotificacaoEntity;

@Dao
public interface NotificacaoDao {

    @Insert
    void inserir(NotificacaoEntity notificacao);

    @Query("SELECT * FROM notificacoes ORDER BY id DESC")
    List<NotificacaoEntity> listarTodas();

    @Query("UPDATE notificacoes SET lida = 1 WHERE id = :id")
    void marcarComoLida(long id);

    @Query("DELETE FROM notificacoes WHERE id = :id")
    void deletar(long id);

    @Query("SELECT COUNT(*) FROM notificacoes WHERE lida = 0")
    int contarNaoLidas();

    // Evita duplicar notificação de ar no mesmo dia
    @Query("SELECT COUNT(*) FROM notificacoes WHERE tipo = 'AR' AND dataHora LIKE :dataPrefix || '%'")
    int contarNotificacoesArNaData(String dataPrefix);

    @Query("UPDATE notificacoes SET lida = 1")
    void marcarTodasComoLidas();

    @Query("DELETE FROM notificacoes")
    void deletarTodas();
    @Query("DELETE FROM notificacoes WHERE templateId = :templateId")
    void deletarPorTemplate(long templateId);
}