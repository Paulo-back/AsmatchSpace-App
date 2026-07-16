package br.fmu.projetoasthmaspace.Core.Util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Map;

import br.fmu.projetoasthmaspace.Data.Local.NotificacaoDatabase;
import br.fmu.projetoasthmaspace.Data.worker.LembreteReceiver;

public class AlarmeLembreteUtil {

    private static final String TAG = "AlarmeLembreteUtil";

    /** Cancela todos os alarmes de lembrete e limpa a persistência local.
     *  Chamar no logout — os lembretes pertencem à conta, não ao aparelho. */
    public static void cancelarTodos(Context context) {
        SharedPreferences prefs = context
                .getSharedPreferences("lembretes_reboot", Context.MODE_PRIVATE);
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        SharedPreferences.Editor editor = prefs.edit();

        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("lembrete_")) continue; // preserva flags como migrado_v2

            try {
                int requestCode = Integer.parseInt(key.substring("lembrete_".length()));
                Intent intent = new Intent(context, LembreteReceiver.class);
                PendingIntent pi = PendingIntent.getBroadcast(
                        context, requestCode, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                if (alarmManager != null) alarmManager.cancel(pi);
                pi.cancel();
                editor.remove(key);
                Log.d(TAG, "Cancelado no logout — " + key);
            } catch (NumberFormatException e) {
                editor.remove(key); // entrada corrompida: remove mesmo assim
            }
        }
        editor.apply();

        // Histórico local pertence à conta que saiu — não deve vazar para a próxima
        new Thread(() ->
                NotificacaoDatabase.getInstance(context).dao().deletarTodas()
        ).start();
    }
}