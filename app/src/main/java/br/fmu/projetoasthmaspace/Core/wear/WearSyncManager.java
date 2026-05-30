package br.fmu.projetoasthmaspace.Core.wear;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

public class WearSyncManager {

    private static final String TAG = "WearSyncManager";

    private static final String PATH_QUALIDADE_AR = "/qualidade-ar";
    private static final String PATH_LEMBRETE     = "/lembrete";

    private final Context context;

    public WearSyncManager(Context context) {
        this.context = context.getApplicationContext();
    }

    // -------------------------------------------------------------------------
    // Qualidade do ar — chamado no QualidadeArWorker após buscar o AQI
    // -------------------------------------------------------------------------

    public void enviarQualidadeAr(int aqi, String descricao, String cidade) {
        PutDataMapRequest dataMap = PutDataMapRequest.create(PATH_QUALIDADE_AR);
        dataMap.getDataMap().putInt("aqi", aqi);
        dataMap.getDataMap().putString("descricao", descricao);
        dataMap.getDataMap().putString("cidade", cidade);
        // timestamp garante que o DataLayer entende como "dado novo"
        // mesmo que o AQI não mude
        dataMap.getDataMap().putLong("ts", System.currentTimeMillis());

        enviar(dataMap.asPutDataRequest().setUrgent(), "qualidade do ar");
    }

    // -------------------------------------------------------------------------
    // Lembrete — chamado no NotificacaoScheduler junto com a notificação local
    // -------------------------------------------------------------------------

    public void enviarLembrete(String medicamento, String horario, String observacao) {
        PutDataMapRequest dataMap = PutDataMapRequest.create(PATH_LEMBRETE);
        dataMap.getDataMap().putString("medicamento", medicamento);
        dataMap.getDataMap().putString("horario", horario != null ? horario : "");
        dataMap.getDataMap().putString("observacao", observacao != null ? observacao : "");
        dataMap.getDataMap().putLong("ts", System.currentTimeMillis());

        enviar(dataMap.asPutDataRequest().setUrgent(), "lembrete");
    }

    // -------------------------------------------------------------------------
    // Helper interno
    // -------------------------------------------------------------------------

    private void enviar(PutDataRequest request, String tipo) {
        Wearable.getDataClient(context)
                .putDataItem(request)
                .addOnSuccessListener(item ->
                        Log.d(TAG, "Enviado ao watch: " + tipo))
                .addOnFailureListener(e ->
                        // Falha silenciosa — watch pode não estar pareado
                        Log.w(TAG, "Watch não disponível para: " + tipo));
    }
}
