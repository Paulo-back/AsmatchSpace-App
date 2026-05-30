package br.fmu.projetoasthmaspace.Data.worker;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import br.fmu.projetoasthmaspace.R;

public class LembreteReceiver extends BroadcastReceiver {

    private static final String TAG      = "LembreteReceiver";
    private static final String CANAL_ID = "LEMBRETES";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences config = context.getSharedPreferences("CONFIG", Context.MODE_PRIVATE);
        if (!config.getBoolean("lembretes_ativos", true)) return;

        String titulo      = intent.getStringExtra("titulo");
        String mensagem    = intent.getStringExtra("mensagem");
        int    hora        = intent.getIntExtra("hora", -1);
        int    minuto      = intent.getIntExtra("minuto", -1);
        String recorrencia = intent.getStringExtra("recorrencia");
        int    requestCode = intent.getIntExtra("requestCode",
                Math.abs((titulo + "_" + hora + "_" + minuto).hashCode()));

        if (titulo == null || mensagem == null || hora < 0 || minuto < 0) return;

        dispararNotificacao(context, titulo, mensagem, requestCode);
        reagendarSeRecorrente(context, titulo, mensagem, hora, minuto, recorrencia, requestCode);
    }

    private void dispararNotificacao(Context context, String titulo, String mensagem, int notifId) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(
                    CANAL_ID, "Lembretes de Medicação", NotificationManager.IMPORTANCE_HIGH));
        }

        // ✅ Abre MainActivity e sinaliza para navegar até NotificacoesActivity
        Intent intent = new Intent(context, br.fmu.projetoasthmaspace.Presentation.ActivityView.MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("ABRIR_NOTIFICACOES", true);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, notifId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, CANAL_ID)
                        .setSmallIcon(R.drawable.ic_notificacao)
                        .setContentTitle(titulo)
                        .setContentText(mensagem)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(mensagem))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent) // ✅
                        .setAutoCancel(true);

        manager.notify(notifId, builder.build());
    }

    private void reagendarSeRecorrente(Context context, String titulo, String mensagem,
                                       int hora, int minuto, String recorrencia, int requestCode) {
        if (recorrencia == null || "NENHUMA".equals(recorrencia)) return;

        Calendar proximo = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo"));

        if ("DIARIA".equals(recorrencia)) {
            proximo.add(Calendar.DAY_OF_MONTH, 1);
        } else if ("SEMANAL".equals(recorrencia)) {
            proximo.add(Calendar.DAY_OF_MONTH, 7);
        } else {
            return;
        }

        proximo.set(Calendar.HOUR_OF_DAY, hora);
        proximo.set(Calendar.MINUTE, minuto);
        proximo.set(Calendar.SECOND, 0);
        proximo.set(Calendar.MILLISECOND, 0);

        Intent novoIntent = new Intent(context, LembreteReceiver.class);
        novoIntent.putExtra("titulo", titulo);
        novoIntent.putExtra("mensagem", mensagem);
        novoIntent.putExtra("hora", hora);
        novoIntent.putExtra("minuto", minuto);
        novoIntent.putExtra("recorrencia", recorrencia);
        novoIntent.putExtra("requestCode", requestCode);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, novoIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, proximo.getTimeInMillis(), pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, proximo.getTimeInMillis(), pendingIntent);
        }

        // Atualiza a data no SharedPreferences para o próximo reboot saber a data correta
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
        String novaData = sdf.format(proximo.getTime());

        SharedPreferences prefs = context.getSharedPreferences("lembretes_reboot", Context.MODE_PRIVATE);
        String key   = "lembrete_" + requestCode;
        String valor = titulo + "|" + mensagem + "|" + hora + "|" + minuto + "|" + recorrencia + "|" + novaData;
        prefs.edit().putString(key, valor).apply();

        Log.d(TAG, "Reagendado (" + recorrencia + ") para: " + novaData + " " + hora + ":" + minuto);
    }
}