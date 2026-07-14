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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import br.fmu.projetoasthmaspace.Data.Local.NotificacaoDatabase;
import br.fmu.projetoasthmaspace.Data.Local.NotificacaoEntity;
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
        String dataFim = intent.getStringExtra("dataFim");
        long   templateId  = intent.getLongExtra("templateId", -1L); // ✅
        int    requestCode = intent.getIntExtra("requestCode",
                Math.abs((titulo + "_" + hora + "_" + minuto).hashCode()));

        if (titulo == null || mensagem == null || hora < 0 || minuto < 0) return;

        dispararNotificacao(context, titulo, mensagem, requestCode);

        String dataHora = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo")).getTime());
        NotificacaoEntity notif = new NotificacaoEntity(titulo, mensagem, dataHora, "LEMBRETE");
        notif.templateId = templateId;
        new Thread(() ->
                NotificacaoDatabase.getInstance(context).dao().inserir(notif)
        ).start();

        reagendarSeRecorrente(context, titulo, mensagem, hora, minuto,
                recorrencia, requestCode, templateId, dataFim); // ✅ passa templateId
    }

    private void dispararNotificacao(Context context, String titulo, String mensagem, int notifId) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(
                    CANAL_ID, "Lembretes de Medicação", NotificationManager.IMPORTANCE_HIGH));
        }


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
                                       int hora, int minuto, String recorrencia,
                                       int requestCode, long templateId, String dataFim) {
        if (recorrencia == null || "NENHUMA".equals(recorrencia)) return;

        Calendar proximo = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo"));
        proximo.set(Calendar.HOUR_OF_DAY, hora);
        proximo.set(Calendar.MINUTE, minuto);
        proximo.set(Calendar.SECOND, 0);
        proximo.set(Calendar.MILLISECOND, 0);

        if ("DIARIA".equals(recorrencia)) {
            proximo.add(Calendar.DAY_OF_MONTH, 1);
        } else if ("SEMANAL".equals(recorrencia)) {
            proximo.add(Calendar.DAY_OF_MONTH, 7);
        } else {
            return;
        }

        // ✅ Prazo encerrado: não reagenda e limpa a persistência de reboot
        if (dataFim != null && !dataFim.isEmpty()) {
            try {
                SimpleDateFormat sdfFim = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                sdfFim.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
                Date fim = sdfFim.parse(dataFim);
                if (fim != null) {
                    Calendar fimDoDia = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo"));
                    fimDoDia.setTime(fim);
                    fimDoDia.set(Calendar.HOUR_OF_DAY, 23);
                    fimDoDia.set(Calendar.MINUTE, 59);
                    fimDoDia.set(Calendar.SECOND, 59);
                    if (proximo.after(fimDoDia)) {
                        context.getSharedPreferences("lembretes_reboot", Context.MODE_PRIVATE)
                                .edit().remove("lembrete_" + requestCode).apply();
                        Log.d(TAG, "Prazo encerrado (" + dataFim + ") — não reagendado. requestCode=" + requestCode);
                        return;
                    }
                }
            } catch (ParseException e) {
                Log.e(TAG, "dataFim inválida: " + dataFim);
            }
        }

        Intent novoIntent = new Intent(context, LembreteReceiver.class);
        novoIntent.putExtra("titulo", titulo);
        novoIntent.putExtra("mensagem", mensagem);
        novoIntent.putExtra("hora", hora);
        novoIntent.putExtra("minuto", minuto);
        novoIntent.putExtra("recorrencia", recorrencia);
        novoIntent.putExtra("requestCode", requestCode);
        novoIntent.putExtra("templateId", templateId); // ✅
        novoIntent.putExtra("dataFim", dataFim);

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

        // ✅ Atualiza SharedPreferences com a nova data e templateId
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
        String novaData = sdf.format(proximo.getTime());

        SharedPreferences prefs = context.getSharedPreferences("lembretes_reboot", Context.MODE_PRIVATE);
        String key   = "lembrete_" + requestCode;
        // formato: titulo|mensagem|hora|minuto|recorrencia|data|templateId
        String valor = titulo + "|" + mensagem + "|" + hora + "|" + minuto
                + "|" + recorrencia + "|" + novaData + "|" + templateId;
        prefs.edit().putString(key, valor).apply();

        Log.d(TAG, "Reagendado (" + recorrencia + ") para: " + novaData
                + " " + hora + ":" + minuto + " requestCode=" + requestCode);
    }
}