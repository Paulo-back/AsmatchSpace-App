package br.fmu.projetoasthmaspace.Data.worker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;
import java.util.TimeZone;

public class NotificacaoScheduler {

    private static final String TAG = "NotificacaoScheduler";

    // Janelas de verificação: 7h, 10h, 13h, 16h, 19h, 22h
    private static final int[] JANELAS = {7, 10, 13, 16, 19, 22};

    /**
     * Chamado uma vez no Application.onCreate para garantir que
     * sempre existe um alarme agendado.
     */
    public static void agendarVerificacaoAr(Context context) {
        agendarProximoAlarme(context);
    }

    /**
     * Agenda o próximo alarme da sequência.
     * Chamado também pelo QualidadeArReceiver após cada disparo.
     */
    public static void agendarProximoAlarme(Context context) {
        long proximoDisparo = calcularProximoDisparo();

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = criarPendingIntent(context);

        if (alarmManager == null) return;

        // Android 12+: verifica se pode agendar alarmes exatos
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Sem permissão para alarmes exatos. Usando inexato.");
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        proximoDisparo,
                        pendingIntent
                );
                return;
            }
        }

        // Alarme exato — funciona mesmo em Doze Mode
        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                proximoDisparo,
                pendingIntent
        );

        Log.d(TAG, "Próximo alarme agendado para: " + new java.util.Date(proximoDisparo));
    }

    /**
     * Cancela todos os alarmes agendados (útil ao fazer logout).
     */
    public static void cancelarAlarmes(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(criarPendingIntent(context));
            Log.d(TAG, "Alarmes cancelados.");
        }
    }

    private static PendingIntent criarPendingIntent(Context context) {
        Intent intent = new Intent(context, QualidadeArReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }

    private static long calcularProximoDisparo() {
        // IMPORTANTE: usa fuso de São Paulo para não errar horário
        Calendar agora = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo"));
        int horaAtual  = agora.get(Calendar.HOUR_OF_DAY);
        int minAtual   = agora.get(Calendar.MINUTE);

        int proximaJanela = -1;

        for (int janela : JANELAS) {
            // Só considera janela que ainda não passou (com 1 min de margem)
            if (horaAtual < janela || (horaAtual == janela && minAtual < 1)) {
                proximaJanela = janela;
                break;
            }
        }

        Calendar proxima = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo"));
        proxima.set(Calendar.MINUTE, 0);
        proxima.set(Calendar.SECOND, 0);
        proxima.set(Calendar.MILLISECOND, 0);

        if (proximaJanela == -1) {
            // Passou de 22h — agenda para amanhã às 7h
            proxima.add(Calendar.DAY_OF_MONTH, 1);
            proxima.set(Calendar.HOUR_OF_DAY, 7);
        } else {
            proxima.set(Calendar.HOUR_OF_DAY, proximaJanela);
        }

        return proxima.getTimeInMillis();
    }
}