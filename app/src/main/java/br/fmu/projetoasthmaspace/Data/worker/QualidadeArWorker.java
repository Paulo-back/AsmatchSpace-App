package br.fmu.projetoasthmaspace.Data.worker;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Tasks;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import br.fmu.projetoasthmaspace.Core.Util.AirQualityUtils;
import br.fmu.projetoasthmaspace.Core.wear.WearSyncManager;
import br.fmu.projetoasthmaspace.Data.Local.NotificacaoDatabase;
import br.fmu.projetoasthmaspace.Data.Local.NotificacaoEntity;
import br.fmu.projetoasthmaspace.Presentation.ActivityView.MainActivity;
import br.fmu.projetoasthmaspace.R;
import br.fmu.projetoasthmaspace.Data.Service.QualityAir.AirResponse;
import br.fmu.projetoasthmaspace.Data.Service.QualityAir.ApiOpenWeather;

public class QualidadeArWorker extends Worker {

    private static final String TAG      = "QualidadeArWorker";
    private static final String CANAL_ID = "QUALIDADE_AR";

    private static final double LAT_PADRAO = -23.5505;
    private static final double LON_PADRAO = -46.6333;

    // SharedPreferences para persistir estado entre execuções
    private static final String PREFS_NAME   = "qualidade_ar_prefs";
    private static final String KEY_AQI_ANT  = "aqi_anterior";
    private static final String KEY_DATA_ANT = "data_anterior"; // dd/MM/yyyy

    // Classificação por faixa
    private static final int AQI_BOM      = 2; // ≤ 2 = bom/aceitável
    private static final int AQI_CRITICO  = 5; // = 5 sempre notifica ignorando teto

    public QualidadeArWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            double[] coords = obterCoordenadas();
            double lat = coords[0];
            double lon = coords[1];
            Log.d(TAG, "Coordenadas: " + lat + ", " + lon);

            retrofit2.Response<AirResponse> response = ApiOpenWeather
                    .getApiService()
                    .getAirQuality(lat, lon)
                    .execute();

            if (!response.isSuccessful() || response.body() == null
                    || response.body().list == null
                    || response.body().list.isEmpty()) {
                Log.e(TAG, "Resposta inválida: " + response.code());
                return Result.retry();
            }

            int aqiAtual = response.body().list.get(0).main.aqi;

            // --- Formatadores de data ---
            SimpleDateFormat sdfDataHora = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt", "BR"));
            sdfDataHora.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
            SimpleDateFormat sdfData = new SimpleDateFormat("dd/MM/yyyy", new Locale("pt", "BR"));
            sdfData.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));

            String dataHora   = sdfDataHora.format(new Date());
            String dataHoje   = sdfData.format(new Date());

            // --- Estado anterior ---
            SharedPreferences prefs = getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            int    aqiAnterior  = prefs.getInt(KEY_AQI_ANT, -1);   // -1 = nunca registrado
            String dataAnterior = prefs.getString(KEY_DATA_ANT, "");
            boolean primeiroDia = !dataHoje.equals(dataAnterior);

            // --- Contagem de hoje no banco ---
            NotificacaoDatabase db = NotificacaoDatabase.getInstance(getApplicationContext());
            int totalHoje = db.dao().contarNotificacoesArNaData(dataHoje);

            // --- Classificações ---
            boolean arRuimAgora    = aqiAtual  > AQI_BOM;   // AQI 3, 4 ou 5
            boolean arBomAgora     = aqiAtual  <= AQI_BOM;  // AQI 1 ou 2
            boolean arRuimAntes    = aqiAnterior > AQI_BOM && aqiAnterior != -1;
            boolean piorou         = aqiAtual > aqiAnterior && aqiAnterior != -1;
            boolean melhorouParaBom= arBomAgora && arRuimAntes;
            boolean critico        = aqiAtual == AQI_CRITICO;
            boolean abaixoDoTeto   = totalHoje < 4;

            // --- Decisão de notificar ---
            // Motivo é usado para logar e personalizar a mensagem
            String motivo = null;

            if (primeiroDia) {
                // Primeiro check do dia — notifica sempre, independente do AQI
                motivo = "PRIMEIRO_DIA";
            } else if (critico && abaixoDoTeto) {
                // AQI 5 — sempre notifica enquanto houver espaço no teto
                motivo = "CRITICO";
            } else if (piorou && arRuimAgora && abaixoDoTeto) {
                // Piorou E está em faixa ruim
                motivo = "PIOROU";
            } else if (melhorouParaBom) {
                // Melhorou saindo da faixa ruim — notificação positiva
                motivo = "MELHOROU";
            }

            // --- Executa notificação se há motivo ---
            if (motivo != null) {
                String titulo   = getTitulo(aqiAtual, motivo);
                String mensagem = AirQualityUtils.gerarRecomendacaoAqi(aqiAtual);

                db.dao().inserir(new NotificacaoEntity(titulo, mensagem, dataHora, "AR"));
                dispararNotificacaoSistema(titulo, mensagem);

                new WearSyncManager(getApplicationContext())
                        .enviarQualidadeAr(aqiAtual, mensagem, "sua região");

                Log.d(TAG, "Notificação enviada — motivo: " + motivo
                        + " | AQI: " + aqiAtual
                        + " | anterior: " + aqiAnterior
                        + " | total hoje: " + (totalHoje + 1));
            } else {
                Log.d(TAG, "Sem motivo para notificar — AQI: " + aqiAtual
                        + " | anterior: " + aqiAnterior
                        + " | total hoje: " + totalHoje);
            }

            // --- Sempre persiste o estado atual para a próxima execução ---
            prefs.edit()
                    .putInt(KEY_AQI_ANT, aqiAtual)
                    .putString(KEY_DATA_ANT, dataHoje)
                    .apply();

            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Erro no worker: " + e.getMessage());
            return Result.retry();
        }
    }

    private double[] obterCoordenadas() {
        Context ctx = getApplicationContext();

        boolean temPermissao =
                ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
                                == PackageManager.PERMISSION_GRANTED;

        if (!temPermissao) {
            Log.w(TAG, "Sem permissão de localização, usando padrão.");
            return new double[]{LAT_PADRAO, LON_PADRAO};
        }

        try {
            FusedLocationProviderClient client =
                    LocationServices.getFusedLocationProviderClient(ctx);
            android.location.Location location = Tasks.await(client.getLastLocation());
            if (location != null) {
                return new double[]{location.getLatitude(), location.getLongitude()};
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter localização: " + e.getMessage());
        }

        Log.w(TAG, "Localização null, usando padrão.");
        return new double[]{LAT_PADRAO, LON_PADRAO};
    }

    private String getTitulo(int aqi, String motivo) {
        String[] emojis = {"", "🟢", "🟡", "🟠", "🔴", "🟣"};
        String emoji = (aqi >= 1 && aqi <= 5) ? emojis[aqi] : "🔵";
        String status = AirQualityUtils.statusAqi(aqi);

        switch (motivo) {

            case "PRIMEIRO_DIA":
                return emoji + " Qualidade do Ar: " + status;

            case "MELHOROU":
                return "🟢 Ar melhorou: " + status;

            case "CRITICO":
                return "🟣 Alerta crítico: " + status;

            case "PIOROU":
                return emoji + " Ar piorou: " + status;

            default:
                return emoji + " Qualidade do Ar: " + status;
        }
    }

    private void dispararNotificacaoSistema(String titulo, String mensagem) {
        Context ctx = getApplicationContext();
        NotificationManager manager =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    CANAL_ID, "Qualidade do Ar", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(canal);
        }

        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("ABRIR_NOTIFICACOES", true);

        int flagsPending = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flagsPending |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(ctx, 0, intent, flagsPending);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(ctx, CANAL_ID)
                        .setSmallIcon(R.drawable.ic_notificacao)
                        .setContentTitle(titulo)
                        .setContentText(mensagem)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(mensagem))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }
}