package br.fmu.projetoasthmaspace.Data.worker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public class QualidadeArReceiver extends BroadcastReceiver {

    private static final String TAG = "QualidadeArReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Alarme recebido — iniciando Worker");

        // Dispara o Worker (faz a chamada de rede em background)
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(QualidadeArWorker.class)
                .build();
        WorkManager.getInstance(context).enqueue(workRequest);

        // Agenda o próximo alarme da sequência
        NotificacaoScheduler.agendarProximoAlarme(context);
    }
}