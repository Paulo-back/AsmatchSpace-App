package br.fmu.projetoasthmaspace.Data.worker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        Log.d(TAG, "Boot detectado — reagendando alarmes.");

        // 1. Reagenda qualidade do ar
        NotificacaoScheduler.agendarProximoAlarme(context);

        // 2. Reagenda lembretes salvos
        reagendarLembretes(context);
    }

    private void reagendarLembretes(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences("lembretes_reboot", Context.MODE_PRIVATE);
        Map<String, ?> todos = prefs.getAll();

        if (todos.isEmpty()) {
            Log.d(TAG, "Nenhum lembrete salvo para reagendar.");
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));

        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        for (Map.Entry<String, ?> entry : todos.entrySet()) {
            // Ignora entradas que não sejam String
            if (!(entry.getValue() instanceof String)) continue;

            try {
                String[] partes = ((String) entry.getValue()).split("\\|");
                if (partes.length < 7) {
                    Log.w(TAG, "Entrada inválida (formato antigo?), ignorando: " + entry.getKey());
                    continue;
                }

                String titulo      = partes[0];
                String mensagem    = partes[1];
                int    hora        = Integer.parseInt(partes[2]);
                int    minuto      = Integer.parseInt(partes[3]);
                String recorrencia = partes[4];
                String dataStr     = partes[5];
                long   templateId  = Long.parseLong(partes[6]);
                String dataFim     = (partes.length >= 8 && !partes[7].isEmpty()) ? partes[7] : null;
                int    requestCode = (int) templateId;

                Calendar c = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo"));
                Date dataParsed = sdf.parse(dataStr);
                if (dataParsed != null) c.setTime(dataParsed);
                c.set(Calendar.HOUR_OF_DAY, hora);
                c.set(Calendar.MINUTE, minuto);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);

                if (c.before(Calendar.getInstance())) {
                    if ("NENHUMA".equals(recorrencia)) {
                        prefs.edit().remove(entry.getKey()).apply();
                        Log.d(TAG, "Lembrete expirado removido: " + titulo);
                        continue;
                    }
                    int diasParaAvancar = "SEMANAL".equals(recorrencia) ? 7 : 1;
                    while (c.before(Calendar.getInstance())) {
                        c.add(Calendar.DAY_OF_MONTH, diasParaAvancar);
                    }
                    // ✅ Atualiza data no SharedPreferences
                    String novaData  = sdf.format(c.getTime());
                    // formato: titulo|mensagem|hora|minuto|recorrencia|data|templateId
                    String novoValor = titulo + "|" + mensagem + "|" + hora + "|" + minuto
                            + "|" + recorrencia + "|" + novaData + "|" + templateId;
                    prefs.edit().putString(entry.getKey(), novoValor).apply();
                }

                Intent lembreteIntent = new Intent(context, LembreteReceiver.class);
                lembreteIntent.putExtra("titulo", titulo);
                lembreteIntent.putExtra("mensagem", mensagem);
                lembreteIntent.putExtra("hora", hora);
                lembreteIntent.putExtra("minuto", minuto);
                lembreteIntent.putExtra("recorrencia", recorrencia);
                lembreteIntent.putExtra("requestCode", requestCode);
                lembreteIntent.putExtra("templateId", templateId); // ✅
                lembreteIntent.putExtra("dataFim", dataFim);

                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        context, requestCode, lembreteIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        && alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pendingIntent);
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pendingIntent);
                }

                Log.d(TAG, "Reagendado: " + titulo + " para " + c.getTime()
                        + " requestCode=" + requestCode);

            } catch (ParseException | NumberFormatException e) {
                Log.e(TAG, "Erro ao reagendar lembrete: " + entry.getKey(), e);
            }
        }
    }
}