package br.fmu.projetoasthmaspace.Presentation.ActivityView;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.List;

import br.fmu.projetoasthmaspace.Core.Navegation.NavegacaoCallback;
import br.fmu.projetoasthmaspace.Core.Util.AirQualityUtils;
import br.fmu.projetoasthmaspace.Presentation.Adapter.PoluenteAdapter;
import br.fmu.projetoasthmaspace.Presentation.Fragment.EducativoFragment;
import br.fmu.projetoasthmaspace.R;
import br.fmu.projetoasthmaspace.Data.Service.QualityAir.AirResponse;
import br.fmu.projetoasthmaspace.Data.Service.QualityAir.ApiOpenWeather;
import br.fmu.projetoasthmaspace.Data.Service.QualityAir.ApiServiceOpenWeather;
import br.fmu.projetoasthmaspace.databinding.ActivityTelaInicialBinding;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TelaInicialFragment extends Fragment {

    private static final String TAG = "TelaInicial";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private ActivityTelaInicialBinding binding;
    private PoluenteAdapter adapter;
    private List<Poluente> poluentes = new ArrayList<>();

    private FusedLocationProviderClient fusedLocationClient;

    // ── NOVO: referências ao skeleton ──────────────────────────────────────────
    private ShimmerFrameLayout shimmerLayout;
    private boolean skeletonInflado = false;
    // ──────────────────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = ActivityTelaInicialBinding.inflate(inflater, container, false);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        criarLinkEducativo();
        setupRecycler();

        // ── NOVO: mostra skeleton antes de iniciar a chamada ──────────────────
        mostrarSkeleton();
        // ─────────────────────────────────────────────────────────────────────

        verificarPermissoesECarregar();
    }

    // ── NOVO: helpers de skeleton ──────────────────────────────────────────────

    private void mostrarSkeleton() {
        // Infla o ViewStub apenas na primeira vez
        if (!skeletonInflado) {
            shimmerLayout = (ShimmerFrameLayout) binding.skeletonStub.inflate();
            skeletonInflado = true;
        }
        shimmerLayout.setVisibility(View.VISIBLE);
        shimmerLayout.startShimmer();
        binding.scrollConteudo.setVisibility(View.GONE);
    }

    private void esconderSkeleton() {
        if (shimmerLayout != null) {
            shimmerLayout.stopShimmer();
            shimmerLayout.setVisibility(View.GONE);
        }
        binding.scrollConteudo.setVisibility(View.VISIBLE);
    }

    // ──────────────────────────────────────────────────────────────────────────

    private void setupRecycler() {
        adapter = new PoluenteAdapter(poluentes);
        binding.recyclerPoluentes.setLayoutManager(new GridLayoutManager(getContext(), 2));
        binding.recyclerPoluentes.setAdapter(adapter);
    }

    private void criarLinkEducativo() {
        String textoCompleto = "Não entende o que esses dados significam? Saiba mais.";
        SpannableString spannableString = new SpannableString(textoCompleto);

        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                if (getActivity() instanceof NavegacaoCallback) {
                    ((NavegacaoCallback) getActivity()).navegarParaEducativo();
                }
            }
        };

        String textoLink = "Saiba mais.";
        int inicio = textoCompleto.indexOf(textoLink);
        int fim = inicio + textoLink.length();
        spannableString.setSpan(clickableSpan, inicio, fim, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        binding.textLinkEducativo.setText(spannableString);
        binding.textLinkEducativo.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void verificarPermissoesECarregar() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            obterLocalizacaoECarregar();
        } else {
            requestPermissions(
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                obterLocalizacaoECarregar();
            } else {
                Toast.makeText(getContext(),
                        "Permissão de localização negada. Usando São Paulo como padrão.",
                        Toast.LENGTH_LONG).show();
                carregarDadosReais(-23.5505, -46.6333, "São Paulo, SP");
            }
        }
    }

    private void obterLocalizacaoECarregar() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(requireActivity(), location -> {
                    if (location != null) {
                        carregarDadosReais(location.getLatitude(), location.getLongitude(),
                                "Sua Localização");
                    } else {
                        Toast.makeText(getContext(),
                                "Não foi possível obter sua localização. Usando São Paulo.",
                                Toast.LENGTH_SHORT).show();
                        carregarDadosReais(-23.5505, -46.6333, "São Paulo, SP");
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(),
                            "Erro ao obter localização. Usando São Paulo.",
                            Toast.LENGTH_SHORT).show();
                    carregarDadosReais(-23.5505, -46.6333, "São Paulo, SP");
                });
    }

    private void carregarDadosReais(double lat, double lon, String nomeCidade) {
        ApiServiceOpenWeather api = ApiOpenWeather.getApiService();

        api.getAirQuality(lat, lon).enqueue(new Callback<AirResponse>() {

            @Override
            public void onResponse(Call<AirResponse> call, Response<AirResponse> response) {
                if (!response.isSuccessful()
                        || response.body() == null
                        || response.body().list.isEmpty()) {
                    Log.e(TAG, "Resposta sem sucesso: " + response.code());
                    // ── NOVO: esconde skeleton mesmo em erro ─────────────────
                    if (getView() != null) esconderSkeleton();
                    // ─────────────────────────────────────────────────────────
                    return;
                }

                AirResponse data = response.body();
                int aqi      = data.list.get(0).main.aqi;
                double pm25  = data.list.get(0).components.pm2_5;
                double pm10  = data.list.get(0).components.pm10;
                double o3    = data.list.get(0).components.o3;
                double no2   = data.list.get(0).components.no2;
                double so2   = data.list.get(0).components.so2;
                double co    = data.list.get(0).components.co;

                atualizarUI(aqi, pm25, pm10, o3, no2, so2, co, nomeCidade);
            }

            @Override
            public void onFailure(Call<AirResponse> call, Throwable t) {
                Log.e(TAG, "Erro na API: " + t.getMessage());
                // ── NOVO: esconde skeleton em falha de rede ───────────────────
                if (getView() != null) esconderSkeleton();
                // ─────────────────────────────────────────────────────────────
                Toast.makeText(getContext(),
                        "Erro ao carregar dados da qualidade do ar",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void atualizarUI(int aqi, double pm25, double pm10,
                             double o3, double no2, double so2,
                             double co, String nomeCidade) {

        binding.textLocalizacao.setText(nomeCidade);
        binding.textAqiValor.setText(String.valueOf(aqi));
        binding.textAqiStatus.setText(AirQualityUtils.statusAqi(aqi));
        binding.textRecomendacao.setText(AirQualityUtils.gerarRecomendacaoAqi(aqi));

        poluentes.clear();
        poluentes.add(new Poluente("PM2.5", "Partículas Finas",      fmt(pm25) + " pg/m³", AirQualityUtils.statusPm25(pm25)));
        poluentes.add(new Poluente("PM10",  "Partículas Inaláveis",  fmt(pm10) + " pg/m³", AirQualityUtils.statusPm10(pm10)));
        poluentes.add(new Poluente("O₃",    "Ozônio",                fmt(o3)   + " pg/m³", AirQualityUtils.statusO3(o3)));
        poluentes.add(new Poluente("NO₂",   "Dióxido de Nitrogênio", fmt(no2)  + " pg/m³", AirQualityUtils.statusNo2(no2)));
        poluentes.add(new Poluente("SO₂",   "Dióxido de Enxofre",    fmt(so2)  + " pg/m³", AirQualityUtils.statusSo2(so2)));
        poluentes.add(new Poluente("CO",    "Monóxido de Carbono",   fmt(co)   + " pg/m³", AirQualityUtils.statusCo(co)));

        adapter.notifyDataSetChanged();

        // ── NOVO: só esconde o skeleton depois de tudo preenchido ─────────────
        esconderSkeleton();
        // ─────────────────────────────────────────────────────────────────────
    }
    private String fmt(double valor) {
        return String.format(java.util.Locale.US, "%.2f", valor);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}