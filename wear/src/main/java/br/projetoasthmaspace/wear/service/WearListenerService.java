package br.projetoasthmaspace.wear.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

import br.projetoasthmaspace.wear.R;

public class WearListenerService extends WearableListenerService {

    private static final String TAG = "WearListenerService";

    // Paths — devem ser idênticos aos usados no módulo :app
    public static final String PATH_QUALIDADE_AR = "/qualidade-ar";
    public static final String PATH_LEMBRETE     = "/lembrete";

    // IDs dos canais de notificação
    private static final String CANAL_AR        = "canal_qualidade_ar";
    private static final String CANAL_LEMBRETE  = "canal_lembrete";

    // IDs únicos para as notificações
    private static final int NOTIF_ID_AR        = 100;
    private static final int NOTIF_ID_LEMBRETE  = 200;

    @Override
    public void onCreate() {
        super.onCreate();
        criarCanaisNotificacao();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent event : dataEvents) {
            if (event.getType() != DataEvent.TYPE_CHANGED) continue;

            String path = event.getDataItem().getUri().getPath();
            DataMap dataMap = DataMapItem
                    .fromDataItem(event.getDataItem())
                    .getDataMap();

            if (PATH_QUALIDADE_AR.equals(path)) {
                tratarQualidadeAr(dataMap);
            } else if (PATH_LEMBRETE.equals(path)) {
                tratarLembrete(dataMap);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Qualidade do Ar
    // -------------------------------------------------------------------------

    private void tratarQualidadeAr(DataMap dataMap) {
        int aqi          = dataMap.getInt("aqi", 1);
        String descricao = dataMap.getString("descricao", "");
        String cidade    = dataMap.getString("cidade", "sua região");

        Log.d(TAG, "AQI recebido: " + aqi + " — " + descricao);

        String titulo  = tituloParaAqi(aqi);
        String mensagem = "AQI " + aqi + " em " + cidade + ". " + descricao;
        int prioridade  = aqi > 100
                ? NotificationCompat.PRIORITY_HIGH
                : NotificationCompat.PRIORITY_DEFAULT;

        exibirNotificacao(CANAL_AR, NOTIF_ID_AR, titulo, mensagem, prioridade);
    }

    private String tituloParaAqi(int aqi) {
        if (aqi <= 50)  return "✅ Ar bom";
        if (aqi <= 100) return "😐 Ar moderado";
        if (aqi <= 150) return "⚠️ Ar prejudicial a sensíveis";
        if (aqi <= 200) return "🚨 Ar prejudicial";
        return "☠️ Ar perigoso";
    }

    // -------------------------------------------------------------------------
    // Lembretes de medicação
    // -------------------------------------------------------------------------

    private void tratarLembrete(DataMap dataMap) {
        String medicamento = dataMap.getString("medicamento", "medicamento");
        String horario     = dataMap.getString("horario", "");
        String observacao  = dataMap.getString("observacao", "");

        Log.d(TAG, "Lembrete recebido: " + medicamento + " às " + horario);

        String titulo   = "💊 Hora do remédio";
        String mensagem = medicamento;
        if (!horario.isEmpty())     mensagem += " — " + horario;
        if (!observacao.isEmpty())  mensagem += "\n" + observacao;

        exibirNotificacao(CANAL_LEMBRETE, NOTIF_ID_LEMBRETE,
                titulo, mensagem, NotificationCompat.PRIORITY_HIGH);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void exibirNotificacao(String canalId, int notifId,
                                   String titulo, String mensagem, int prioridade) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, canalId)
                .setSmallIcon(R.drawable.ic_notificacao_wear) // ícone 24x24 monocromático
                .setContentTitle(titulo)
                .setContentText(mensagem)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(mensagem))
                .setPriority(prioridade)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(this).notify(notifId, builder.build());
        } catch (SecurityException e) {
            Log.e(TAG, "Permissão de notificação negada no watch", e);
        }
    }

    private void criarCanaisNotificacao() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Canal qualidade do ar
        NotificationChannel canalAr = new NotificationChannel(
                CANAL_AR,
                "Qualidade do Ar",
                NotificationManager.IMPORTANCE_DEFAULT);
        canalAr.setDescription("Alertas de qualidade do ar da sua região");

        // Canal lembretes
        NotificationChannel canalLembrete = new NotificationChannel(
                CANAL_LEMBRETE,
                "Lembretes de Medicação",
                NotificationManager.IMPORTANCE_HIGH);
        canalLembrete.setDescription("Avisos de hora de tomar o medicamento");

        nm.createNotificationChannel(canalAr);
        nm.createNotificationChannel(canalLembrete);
    }
}
