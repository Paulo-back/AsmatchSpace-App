package br.fmu.projetoasthmaspace.Presentation.ActivityView;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.facebook.shimmer.ShimmerFrameLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import br.fmu.projetoasthmaspace.Core.Domain.Lembretes.LembreteInstancia;
import br.fmu.projetoasthmaspace.R;
import br.fmu.projetoasthmaspace.Data.Service.Client.ApiClient;
import br.fmu.projetoasthmaspace.Data.Service.Client.ApiService;
import br.fmu.projetoasthmaspace.databinding.ActivityTarefasBinding;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TarefasActivity extends Fragment {

    private ActivityTarefasBinding binding;
    private ApiService api;
    private boolean dadosCarregados = false;
    private boolean carregando = false; // ← flag anti-duplicata

    private TarefasAdapter adapter;
    private final List<LembreteInstancia> tarefasPendentes  = new ArrayList<>();
    private final List<LembreteInstancia> tarefasConcluidas = new ArrayList<>();

    private ShimmerFrameLayout shimmerLayout;
    private boolean skeletonInflado = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = ActivityTarefasBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        api = ApiClient.getApiService(requireContext());
        binding.recyclerTarefas.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    @Override
    public void onResume() {
        super.onResume();
        dadosCarregados = false;
        carregarInstanciasDeHoje();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            dadosCarregados = false;
        } else {
            carregarInstanciasDeHoje();
        }
    }

    // ── Skeleton ──────────────────────────────────────────────────────────────

    private void mostrarSkeleton() {
        if (binding == null) return;
        if (!skeletonInflado) {
            shimmerLayout = (ShimmerFrameLayout) binding.skeletonStub.inflate();
            skeletonInflado = true;
        }
        shimmerLayout.setVisibility(View.VISIBLE);
        shimmerLayout.startShimmer();
        binding.scrollTarefas.setVisibility(View.GONE);
    }

    private void esconderSkeleton() {
        if (binding == null) return;
        if (shimmerLayout != null) {
            shimmerLayout.stopShimmer();
            shimmerLayout.setVisibility(View.GONE);
        }
        binding.scrollTarefas.setVisibility(View.VISIBLE);
    }

    // ── Backend ───────────────────────────────────────────────────────────────

    private void carregarInstanciasDeHoje() {
        if (carregando) return; // ← bloqueia chamada duplicata
        carregando = true;
        if (!dadosCarregados) mostrarSkeleton();

        api.instanciasDeHoje().enqueue(new Callback<List<LembreteInstancia>>() {
            @Override
            public void onResponse(Call<List<LembreteInstancia>> call,
                                   Response<List<LembreteInstancia>> response) {
                carregando = false; // ← libera sempre
                if (!isAdded() || binding == null) return;

                if (response.isSuccessful() && response.body() != null) {
                    dadosCarregados = true;
                    tarefasPendentes.clear();
                    tarefasConcluidas.clear();

                    for (LembreteInstancia inst : response.body()) {
                        if (inst.isConcluido()) tarefasConcluidas.add(inst);
                        else                    tarefasPendentes.add(inst);
                    }

                    Log.d("TAREFAS", "Pendentes: " + tarefasPendentes.size()
                            + " | Concluídas: " + tarefasConcluidas.size());

                    atualizarUI();
                    esconderSkeleton();
                } else {
                    Log.e("TAREFAS", "Erro HTTP: " + response.code());
                    esconderSkeleton();
                }
            }

            @Override
            public void onFailure(Call<List<LembreteInstancia>> call, Throwable t) {
                carregando = false; // ← libera sempre
                if (!isAdded() || binding == null) return;
                Log.e("TAREFAS", "Falha: " + t.getMessage());
                esconderSkeleton();
            }
        });
    }

    private void concluirInstancia(LembreteInstancia tarefa) {
        tarefasPendentes.remove(tarefa);
        tarefasConcluidas.add(tarefa);
        tarefa.status = "CONCLUIDO";
        atualizarUI();

        api.atualizarStatus(tarefa.instanciaId, "CONCLUIDO").enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    Log.e("TAREFAS", "Erro ao concluir: " + response.code());
                    if (isAdded()) {
                        tarefasConcluidas.remove(tarefa);
                        tarefasPendentes.add(tarefa);
                        tarefa.status = "PENDENTE";
                        atualizarUI();
                    }
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("TAREFAS", "Falha ao concluir: " + t.getMessage());
                if (isAdded()) {
                    tarefasConcluidas.remove(tarefa);
                    tarefasPendentes.add(tarefa);
                    tarefa.status = "PENDENTE";
                    atualizarUI();
                }
            }
        });
    }

    private void reabrirInstancia(LembreteInstancia tarefa) {
        tarefasConcluidas.remove(tarefa);
        tarefasPendentes.add(tarefa);
        tarefa.status = "PENDENTE";
        atualizarUI();

        api.atualizarStatus(tarefa.instanciaId, "PENDENTE").enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) {
                    Log.e("TAREFAS", "Erro ao reabrir: " + response.code());
                    if (isAdded()) {
                        tarefasPendentes.remove(tarefa);
                        tarefasConcluidas.add(tarefa);
                        tarefa.status = "CONCLUIDO";
                        atualizarUI();
                    }
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("TAREFAS", "Falha ao reabrir: " + t.getMessage());
                if (isAdded()) {
                    tarefasPendentes.remove(tarefa);
                    tarefasConcluidas.add(tarefa);
                    tarefa.status = "CONCLUIDO";
                    atualizarUI();
                }
            }
        });
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private void atualizarUI() {
        if (!isAdded() || binding == null) return;

        int n = tarefasPendentes.size();
        binding.contadorTarefas.setText(
                n == 1 ? "1 tarefa pendente"
                        : String.format(Locale.getDefault(), "%d tarefas pendentes", n));

        if (adapter == null) {
            adapter = new TarefasAdapter(tarefasPendentes, this::concluirInstancia);
            binding.recyclerTarefas.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }

        atualizarConcluidasHoje();
    }

    private void atualizarConcluidasHoje() {
        if (!isAdded() || binding == null) return;

        if (tarefasConcluidas.isEmpty()) {
            binding.tituloConcluidas.setVisibility(View.GONE);
            binding.containerConcluidas.setVisibility(View.GONE);
            return;
        }

        binding.tituloConcluidas.setVisibility(View.VISIBLE);
        binding.containerConcluidas.setVisibility(View.VISIBLE);
        binding.containerConcluidas.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        for (LembreteInstancia inst : tarefasConcluidas) {
            View item = inflater.inflate(R.layout.item_tarefa_concluida,
                    binding.containerConcluidas, false);
            CheckBox checkbox = item.findViewById(R.id.checkbox_concluida);

            checkbox.setText(inst.getHorarioFormatado() + " — " + inst.titulo);
            checkbox.setChecked(true);
            checkbox.setPaintFlags(
                    checkbox.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);

            checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!isChecked) reabrirInstancia(inst);
            });

            binding.containerConcluidas.addView(item);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}