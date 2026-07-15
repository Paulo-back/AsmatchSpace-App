package br.fmu.projetoasthmaspace.Presentation.ActivityView;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.facebook.shimmer.ShimmerFrameLayout;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import br.fmu.projetoasthmaspace.Core.Domain.Lembretes.LembreteInstancia;
import br.fmu.projetoasthmaspace.Core.Domain.Lembretes.LembreteTemplate;
import br.fmu.projetoasthmaspace.Core.Domain.Lembretes.LembreteTemplateRequest;
import br.fmu.projetoasthmaspace.Core.Util.LocationHelper;
import br.fmu.projetoasthmaspace.Data.Service.Client.ApiClient;
import br.fmu.projetoasthmaspace.Data.Service.Client.ApiService;
import br.fmu.projetoasthmaspace.Data.worker.LembreteReceiver;
import br.fmu.projetoasthmaspace.R;
import br.fmu.projetoasthmaspace.databinding.ActivityLembretesBinding;
import br.fmu.projetoasthmaspace.databinding.CardLembreteStatBinding;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LembretesActivity extends Fragment {

    private ActivityLembretesBinding binding;
    private boolean isEditMode = false;
    private ApiService api;
    private SimpleDateFormat parseDateFormat;
    private SimpleDateFormat displayDateFormat;
    private String filtroAtivo = null;
    private boolean dadosCarregados = false;

    private final java.util.Set<String> gruposExpandidos = new java.util.HashSet<>();
    private boolean carregando = false; // ← flag anti-duplicata
    private final List<LembreteInstancia> listaDeInstancias = new ArrayList<>();
    private int diasPassadosFiltro = 7;

    private ShimmerFrameLayout shimmerLayout;
    private boolean skeletonInflado = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        parseDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        parseDateFormat.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
        displayDateFormat = new SimpleDateFormat("dd 'de' MMMM 'de' yyyy", new Locale("pt", "BR"));
        displayDateFormat.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
        binding = ActivityLembretesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        api = ApiClient.getApiService(requireContext());

        binding.fabNovoLembrete.setOnClickListener(v -> showTemplateDialog(null));
        binding.fabEditarLembrete.setOnClickListener(v -> {
            isEditMode = !isEditMode;
            toggleUiForEditMode();
            renderizarLista();
        });

        binding.statHoje.getRoot().setOnClickListener(v -> toggleFiltro("hoje"));
        binding.statProgramados.getRoot().setOnClickListener(v -> toggleFiltro("programados"));
        binding.statConcluidos.getRoot().setOnClickListener(v -> toggleFiltro("concluidos"));
        binding.statTodos.getRoot().setOnClickListener(v -> toggleFiltro(null));
        binding.btnFiltrarPeriodo.setOnClickListener(v -> showFiltroPeriodoDialog());
    }

    @Override
    public void onResume() {
        super.onResume();
        dadosCarregados = false;
        carregarInstanciasPorPeriodo();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            dadosCarregados = false;
        } else {
            carregarInstanciasPorPeriodo();
        }
    }

    // ── Skeleton ─────────────────────────────────────────────────────────────

    private void mostrarSkeleton() {
        if (binding == null) return;
        if (!skeletonInflado) {
            shimmerLayout = (ShimmerFrameLayout) binding.skeletonStub.inflate();
            skeletonInflado = true;
        }
        shimmerLayout.setVisibility(View.VISIBLE);
        shimmerLayout.startShimmer();
        getScrollViewLista().setVisibility(View.GONE);
    }

    private void esconderSkeleton() {
        if (binding == null) return;
        if (shimmerLayout != null) {
            shimmerLayout.stopShimmer();
            shimmerLayout.setVisibility(View.GONE);
        }
        getScrollViewLista().setVisibility(View.VISIBLE);
    }

    private View getScrollViewLista() {
        return binding.getRoot().findViewById(R.id.scroll_lista_lembretes);
    }

    // ── Backend ───────────────────────────────────────────────────────────────

    private void carregarInstanciasPorPeriodo() {
        if (carregando) return; // ← bloqueia chamada duplicada
        carregando = true;
        if (!dadosCarregados) mostrarSkeleton();

        api.listarInstanciasPorPeriodo(diasPassadosFiltro).enqueue(new Callback<List<LembreteInstancia>>() {
            @Override
            public void onResponse(Call<List<LembreteInstancia>> call,
                                   Response<List<LembreteInstancia>> response) {
                carregando = false; // ← libera sempre
                if (!isAdded() || binding == null) return;
                if (response.isSuccessful() && response.body() != null) {
                    dadosCarregados = true;
                    listaDeInstancias.clear();
                    listaDeInstancias.addAll(response.body());

                    migrarLembretesAntigos(response.body()); // ← Migra lembretes antigos para o novo formato

                    atualizarPainelDeResumo();
                    atualizarTextoBotaoFiltro();
                    renderizarLista();
                    esconderSkeleton();
                } else {
                    Toast.makeText(getContext(), "Erro ao buscar lembretes", Toast.LENGTH_SHORT).show();
                    esconderSkeleton();
                }
            }

            @Override
            public void onFailure(Call<List<LembreteInstancia>> call, Throwable t) {
                carregando = false; // ← libera sempre
                if (!isAdded() || binding == null) return;
                Toast.makeText(getContext(), "Falha na conexão", Toast.LENGTH_SHORT).show();
                esconderSkeleton();
            }
        });
    }

    private void criarTemplate(LembreteTemplateRequest req, int hora, int minuto, String titulo) {
        api.criarTemplate(req).enqueue(new Callback<LembreteTemplate>() {
            @Override
            public void onResponse(Call<LembreteTemplate> call, Response<LembreteTemplate> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    LembreteTemplate criado = response.body();
                    Toast.makeText(getContext(), "Lembrete salvo!", Toast.LENGTH_SHORT).show();
                    // ✅ Fora do callback de localização — templateId disponível
                    agendarLembrete(hora, minuto, req.dataInicio, titulo,
                            "Lembrete programado!", req.tipoRecorrencia, criado.id,
                            req.dataFim);
                    dadosCarregados = false;
                    carregarInstanciasPorPeriodo();
                } else {
                    Toast.makeText(getContext(), "Erro ao salvar!", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<LembreteTemplate> call, Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "Falha na conexão!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void atualizarTemplate(Long templateId, LembreteTemplateRequest req,
                                   int hora, int minuto) {
        api.atualizarTemplate(templateId, req).enqueue(new Callback<LembreteTemplate>() {
            @Override
            public void onResponse(Call<LembreteTemplate> call, Response<LembreteTemplate> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    cancelarAlarmeLocal(templateId);
                    agendarLembrete(hora, minuto, req.dataInicio, req.titulo,
                            "Lembrete programado!", req.tipoRecorrencia, templateId,
                            req.dataFim);
                    Toast.makeText(getContext(), "Lembrete atualizado!", Toast.LENGTH_SHORT).show();
                    dadosCarregados = false;
                    carregarInstanciasPorPeriodo();
                } else {
                    Toast.makeText(getContext(), "Erro ao atualizar (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<LembreteTemplate> call, Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "Falha de conexão!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deletarTemplate(Long templateId) {
        api.deletarTemplate(templateId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    cancelarAlarmeLocal(templateId);
                    limparHistoricoNotificacoes(templateId);
                    Toast.makeText(getContext(), "Lembrete excluído!", Toast.LENGTH_SHORT).show();
                    dadosCarregados = false;
                    carregarInstanciasPorPeriodo();
                } else {
                    Toast.makeText(getContext(), "Erro ao excluir (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "Falha de conexão!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deletarInstancia(Long instanciaId) {
        api.deletarInstancia(instanciaId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    Toast.makeText(getContext(), "Dia removido!", Toast.LENGTH_SHORT).show();
                    dadosCarregados = false;
                    carregarInstanciasPorPeriodo();
                } else {
                    Toast.makeText(getContext(), "Erro ao remover (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "Falha de conexão!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deletarInstanciaEFuturas(Long instanciaId, Long templateId) {
        api.deletarInstanciaEFuturas(instanciaId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    if (templateId != null) cancelarAlarmeLocal(templateId);
                    Toast.makeText(getContext(), "Este e próximos removidos!", Toast.LENGTH_SHORT).show();
                    dadosCarregados = false;
                    carregarInstanciasPorPeriodo();
                } else {
                    Toast.makeText(getContext(), "Erro ao remover (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "Falha de conexão!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void concluirInstancia(LembreteInstancia instancia) {
        instancia.status = "CONCLUIDO";
        renderizarLista();
        atualizarPainelDeResumo();

        api.atualizarStatus(instancia.instanciaId, "CONCLUIDO").enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!isAdded()) return;
                if (!response.isSuccessful()) {
                    instancia.status = "PENDENTE";
                    renderizarLista();
                    atualizarPainelDeResumo();
                    Toast.makeText(getContext(), "Erro ao concluir (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                if (!isAdded()) return;
                instancia.status = "PENDENTE";
                renderizarLista();
                atualizarPainelDeResumo();
                Toast.makeText(getContext(), "Falha de conexão!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── Filtro período ────────────────────────────────────────────────────────

    private void showFiltroPeriodoDialog() {
        String[] opcoes = {"Últimos 7 dias", "Últimos 15 dias", "Últimos 30 dias", "Últimos 60 dias"};
        int[] valores = {7, 15, 30, 60};
        int indexAtual = 0;
        for (int i = 0; i < valores.length; i++) {
            if (valores[i] == diasPassadosFiltro) { indexAtual = i; break; }
        }
        new AlertDialog.Builder(requireContext(), R.style.DialogTheme)
                .setTitle("Exibir lembretes de:")
                .setSingleChoiceItems(opcoes, indexAtual, (dialog, which) -> {
                    diasPassadosFiltro = valores[which];
                    dialog.dismiss();
                    dadosCarregados = false;
                    carregarInstanciasPorPeriodo();
                    atualizarTextoBotaoFiltro();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void atualizarTextoBotaoFiltro() {
        if (binding != null)
            binding.btnFiltrarPeriodo.setText("Últimos " + diasPassadosFiltro + " dias");
    }

    // ── Dialog criação/edição ─────────────────────────────────────────────────

    private void showTemplateDialog(@Nullable final LembreteInstancia instanciaExistente) {
        final boolean isEditing = instanciaExistente != null;

        final int[] horaSelecionada   = {Calendar.getInstance().get(Calendar.HOUR_OF_DAY)};
        final int[] minutoSelecionado = {Calendar.getInstance().get(Calendar.MINUTE)};
        final String[] dataSelecionada    = {isEditing ? instanciaExistente.data : parseDateFormat.format(new Date())};
        final String[] dataFimSelecionada = {null};
        final String[] tipoRecorrencia    = {"NENHUMA"};
        final String[] diasSemana         = {null};

        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_novo_lembrete, null);

        EditText     tituloInput      = dialogView.findViewById(R.id.edit_text_titulo_lembrete);
        TextView     textHorario      = dialogView.findViewById(R.id.text_horario_selecionado);
        TextView     textData         = dialogView.findViewById(R.id.text_data_selecionada);
        TextView     textDataFim      = dialogView.findViewById(R.id.text_data_fim_selecionada);
        LinearLayout btnHorario       = dialogView.findViewById(R.id.btn_selecionar_horario);
        LinearLayout btnData          = dialogView.findViewById(R.id.btn_selecionar_data);
        LinearLayout btnDataFim       = dialogView.findViewById(R.id.btn_selecionar_data_fim);
        RadioGroup   radioRecorrencia = dialogView.findViewById(R.id.radio_recorrencia);
        View         cardDiasSemana   = dialogView.findViewById(R.id.card_dias_semana);
        View         cardDataFim      = dialogView.findViewById(R.id.card_data_fim);
        View         labelDataFim     = dialogView.findViewById(R.id.label_data_fim);

        CheckBox cbSeg = dialogView.findViewById(R.id.cb_seg);
        CheckBox cbTer = dialogView.findViewById(R.id.cb_ter);
        CheckBox cbQua = dialogView.findViewById(R.id.cb_qua);
        CheckBox cbQui = dialogView.findViewById(R.id.cb_qui);
        CheckBox cbSex = dialogView.findViewById(R.id.cb_sex);
        CheckBox cbSab = dialogView.findViewById(R.id.cb_sab);
        CheckBox cbDom = dialogView.findViewById(R.id.cb_dom);

        if (isEditing) {
            tituloInput.setText(instanciaExistente.titulo);
            String h = instanciaExistente.horario;
            if (h != null && h.contains(":")) {
                String[] p = h.split(":");
                horaSelecionada[0]   = Integer.parseInt(p[0]);
                minutoSelecionado[0] = Integer.parseInt(p[1].substring(0, 2));
            }
            textHorario.setText(String.format("%02d:%02d", horaSelecionada[0], minutoSelecionado[0]));
            textHorario.setTextColor(Color.WHITE);
            textData.setText(formatarDataParaExibicao(dataSelecionada[0]));
            textData.setTextColor(Color.WHITE);
        }

        radioRecorrencia.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_nenhuma) {
                tipoRecorrencia[0] = "NENHUMA";
                cardDiasSemana.setVisibility(View.GONE);
                cardDataFim.setVisibility(View.GONE);
                labelDataFim.setVisibility(View.GONE);
            } else if (checkedId == R.id.radio_diaria) {
                tipoRecorrencia[0] = "DIARIA";
                cardDiasSemana.setVisibility(View.GONE);
                cardDataFim.setVisibility(View.VISIBLE);
                labelDataFim.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.radio_semanal) {
                tipoRecorrencia[0] = "SEMANAL";
                cardDiasSemana.setVisibility(View.VISIBLE);
                cardDataFim.setVisibility(View.VISIBLE);
                labelDataFim.setVisibility(View.VISIBLE);
            }
        });

        btnHorario.setOnClickListener(v ->
                new android.app.TimePickerDialog(getContext(),
                        (view, hora, minuto) -> {
                            horaSelecionada[0]   = hora;
                            minutoSelecionado[0] = minuto;
                            textHorario.setText(String.format("%02d:%02d", hora, minuto));
                            textHorario.setTextColor(Color.WHITE);
                        },
                        horaSelecionada[0], minutoSelecionado[0], true).show());

        btnData.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            try { Date d = parseDateFormat.parse(dataSelecionada[0]); if (d != null) cal.setTime(d); }
            catch (ParseException ignored) {}
            new DatePickerDialog(requireContext(),
                    (view, ano, mes, dia) -> {
                        Calendar sel = Calendar.getInstance();
                        sel.set(ano, mes, dia);
                        dataSelecionada[0] = parseDateFormat.format(sel.getTime());
                        textData.setText(formatarDataParaExibicao(dataSelecionada[0]));
                        textData.setTextColor(Color.WHITE);
                    },
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show();
        });

        btnDataFim.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(requireContext(),
                    (view, ano, mes, dia) -> {
                        Calendar sel = Calendar.getInstance();
                        sel.set(ano, mes, dia);
                        dataFimSelecionada[0] = parseDateFormat.format(sel.getTime());
                        textDataFim.setText(formatarDataParaExibicao(dataFimSelecionada[0]));
                        textDataFim.setTextColor(Color.WHITE);
                    },
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show();
        });

        AlertDialog dialog = new AlertDialog.Builder(getContext(), R.style.DialogTheme)
                .setTitle(isEditing ? "Editar Lembrete" : "Novo Lembrete")
                .setView(dialogView)
                .setPositiveButton("Salvar", null)
                .setNegativeButton("Cancelar", (d, id) -> d.dismiss())
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#4FC3F7"));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#8EADD4"));

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String titulo = tituloInput.getText().toString().trim();
                if (titulo.isEmpty()) {
                    Toast.makeText(getContext(), "Informe o título.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (textHorario.getTextColors().getDefaultColor() ==
                        getResources().getColor(R.color.label_color, null)) {
                    Toast.makeText(getContext(), "Selecione o horário.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if ("SEMANAL".equals(tipoRecorrencia[0])) {
                    StringBuilder sb = new StringBuilder();
                    if (cbSeg.isChecked()) sb.append("1,");
                    if (cbTer.isChecked()) sb.append("2,");
                    if (cbQua.isChecked()) sb.append("3,");
                    if (cbQui.isChecked()) sb.append("4,");
                    if (cbSex.isChecked()) sb.append("5,");
                    if (cbSab.isChecked()) sb.append("6,");
                    if (cbDom.isChecked()) sb.append("7,");
                    if (sb.length() == 0) {
                        Toast.makeText(getContext(), "Selecione ao menos um dia.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    diasSemana[0] = sb.toString().replaceAll(",$", "");
                }

                String horarioFormatado = String.format("%02d:%02d:00",
                        horaSelecionada[0], minutoSelecionado[0]);

                LembreteTemplateRequest req = new LembreteTemplateRequest(
                        titulo, horarioFormatado, dataSelecionada[0],
                        dataFimSelecionada[0], tipoRecorrencia[0], diasSemana[0]);

                if (isEditing) {
                    atualizarTemplate(instanciaExistente.templateId, req,
                            horaSelecionada[0], minutoSelecionado[0]);
                } else {
                    criarTemplate(req, horaSelecionada[0], minutoSelecionado[0], titulo);
                }
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    // ── Renderização da lista ─────────────────────────────────────────────────

    private void renderizarLista() {
        if (binding == null) return;
        binding.containerLembretes.removeAllViews();

        List<LembreteInstancia> listaParaExibir = getListaFiltrada();

        if (listaParaExibir.isEmpty()) {
            TextView vazio = new TextView(getContext());
            vazio.setText(filtroAtivo == null
                    ? "Nenhum lembrete encontrado."
                    : "Nenhum lembrete nesta categoria.");
            vazio.setTextColor(Color.parseColor("#8EADD4"));
            vazio.setTextSize(14f);
            vazio.setPadding(0, 24, 0, 0);
            binding.containerLembretes.addView(vazio);
            return;
        }

        Map<String, List<LembreteInstancia>> agrupados = new HashMap<>();
        for (LembreteInstancia inst : listaParaExibir) {
            try {
                Date date = parseDateFormat.parse(inst.data);
                String key = displayDateFormat.format(date);
                if (!agrupados.containsKey(key)) agrupados.put(key, new ArrayList<>());
                agrupados.get(key).add(inst);
            } catch (ParseException e) {
                Log.e("Lembretes", "Erro ao parsear data: " + inst.data, e);
            }
        }

        List<String> datasOrdenadas = new ArrayList<>(agrupados.keySet());
        Collections.sort(datasOrdenadas, (s1, s2) -> {
            try {
                Date d1 = displayDateFormat.parse(s1);
                Date d2 = displayDateFormat.parse(s2);
                return d2.compareTo(d1);
            } catch (ParseException e) { return 0; }
        });

        LayoutInflater inflater = LayoutInflater.from(getContext());

        for (String dataKey : datasOrdenadas) {
            List<LembreteInstancia> instanciasDoDia = agrupados.get(dataKey);
            instanciasDoDia.sort(Comparator.comparing(i -> i.horario));

            View groupView = inflater.inflate(R.layout.item_data_expandable,
                    binding.containerLembretes, false);
            TextView     dataHeader   = groupView.findViewById(R.id.text_data_header);
            ImageView    deleteGroup  = groupView.findViewById(R.id.icon_delete_group);
            LinearLayout containerDia = groupView.findViewById(R.id.container_lembretes_do_dia);

            dataHeader.setText(dataKey);
            deleteGroup.setVisibility(View.GONE);

            for (LembreteInstancia inst : instanciasDoDia) {
                View itemView = inflater.inflate(R.layout.item_lembrete_edit, containerDia, false);
                TextView  lembreteText     = itemView.findViewById(R.id.text_lembrete);
                ImageView deleteItemIcon   = itemView.findViewById(R.id.icon_delete_item);
                ImageView concluirItemIcon = itemView.findViewById(R.id.icon_concluir_item);
                ImageView iconRecorrencia  = itemView.findViewById(R.id.icon_recorrencia);

                if ("DIARIA".equals(inst.tipoRecorrencia)) {
                    iconRecorrencia.setVisibility(View.VISIBLE);
                    iconRecorrencia.setColorFilter(Color.parseColor("#4FC3F7"), PorterDuff.Mode.SRC_IN);
                } else if ("SEMANAL".equals(inst.tipoRecorrencia)) {
                    iconRecorrencia.setVisibility(View.VISIBLE);
                    iconRecorrencia.setColorFilter(Color.parseColor("#81C784"), PorterDuff.Mode.SRC_IN);
                } else {
                    iconRecorrencia.setVisibility(View.INVISIBLE);
                }

                String statusIcon;
                if (inst.isConcluido()) {
                    statusIcon = "✓";
                    lembreteText.setTextColor(Color.parseColor("#4CAF50"));
                } else if ("IGNORADO".equals(inst.status)) {
                    statusIcon = "✗";
                    lembreteText.setTextColor(Color.parseColor("#8EADD4"));
                } else {
                    statusIcon = "○";
                    lembreteText.setTextColor(Color.WHITE);
                }

                lembreteText.setText(String.format(Locale.getDefault(), "%s %s - %s",
                        statusIcon, inst.getHorarioFormatado(), inst.titulo));

                if (isEditMode) {
                    deleteItemIcon.setVisibility(View.VISIBLE);
                    deleteItemIcon.setColorFilter(
                            ContextCompat.getColor(getContext(), R.color.red_dark),
                            PorterDuff.Mode.SRC_IN);
                    deleteItemIcon.setOnClickListener(v -> showDeleteConfirmationDialog(inst));


                    if (!inst.isConcluido()) {
                        concluirItemIcon.setVisibility(View.VISIBLE);
                        concluirItemIcon.setImageResource(android.R.drawable.checkbox_off_background);
                        concluirItemIcon.setColorFilter(
                                Color.parseColor("#4FC3F7"), PorterDuff.Mode.SRC_IN);
                        concluirItemIcon.setOnClickListener(v -> {
                            concluirItemIcon.setImageResource(android.R.drawable.checkbox_on_background);
                            concluirItemIcon.setColorFilter(
                                    ContextCompat.getColor(getContext(), R.color.green_dark),
                                    PorterDuff.Mode.SRC_IN);
                            concluirItemIcon.setEnabled(false);
                            concluirItemIcon.postDelayed(() -> concluirInstancia(inst), 400);
                        });
                    } else {
                        concluirItemIcon.setVisibility(View.GONE);
                    }

                    lembreteText.setOnClickListener(v -> showTemplateDialog(inst));
                } else {
                    deleteItemIcon.setVisibility(View.GONE);
                    concluirItemIcon.setVisibility(View.GONE);
                    lembreteText.setOnClickListener(null);
                }

                containerDia.addView(itemView);
            }

            // Restaura o estado de expansão que o usuário deixou antes do re-render
            containerDia.setVisibility(
                    gruposExpandidos.contains(dataKey) ? View.VISIBLE : View.GONE);

            dataHeader.setOnClickListener(v -> {
                if (containerDia.getVisibility() == View.GONE) {
                    containerDia.setVisibility(View.VISIBLE);
                    gruposExpandidos.add(dataKey);
                } else {
                    containerDia.setVisibility(View.GONE);
                    gruposExpandidos.remove(dataKey);
                }
            });

            binding.containerLembretes.addView(groupView);
        }
    }

    // ── Painel de resumo e filtros ────────────────────────────────────────────

    private void atualizarPainelDeResumo() {
        long hojeCount       = listaDeInstancias.stream().filter(i -> isToday(i.data)).count();
        long pendentesCount  = listaDeInstancias.stream().filter(i -> !i.isConcluido()).count();
        long todosCount      = listaDeInstancias.size();
        long concluidosCount = listaDeInstancias.stream().filter(LembreteInstancia::isConcluido).count();

        setupCard(binding.statHoje,       "Hoje",      String.valueOf(hojeCount),
                R.drawable.icon_hoje,        R.drawable.bg_icon_hoje,        R.color.blue_navy);
        setupCard(binding.statProgramados, "Pendentes", String.valueOf(pendentesCount),
                R.drawable.icon_programados, R.drawable.bg_icon_programados, R.color.red_dark);
        setupCard(binding.statTodos,       "Todos",    String.valueOf(todosCount),
                R.drawable.icon_todos,       R.drawable.bg_icon_todos,       R.color.gray_dark);
        setupCard(binding.statConcluidos,  "Concluídos", String.valueOf(concluidosCount),
                R.drawable.icon_concluidos,  R.drawable.bg_icon_concluidos,  R.color.green_dark);
    }

    private void toggleFiltro(String filtro) {
        filtroAtivo = (filtro == null || filtro.equals(filtroAtivo)) ? null : filtro;
        destacarCardFiltro();
        renderizarLista();
    }

    private void destacarCardFiltro() {
        float ativo = 1.0f, inativo = 0.5f;
        binding.statHoje.getRoot().setAlpha(
                "hoje".equals(filtroAtivo) || filtroAtivo == null ? ativo : inativo);
        binding.statProgramados.getRoot().setAlpha(
                "programados".equals(filtroAtivo) || filtroAtivo == null ? ativo : inativo);
        binding.statConcluidos.getRoot().setAlpha(
                "concluidos".equals(filtroAtivo) || filtroAtivo == null ? ativo : inativo);
        binding.statTodos.getRoot().setAlpha(filtroAtivo == null ? ativo : inativo);
    }

    private List<LembreteInstancia> getListaFiltrada() {
        if (filtroAtivo == null) return new ArrayList<>(listaDeInstancias);
        List<LembreteInstancia> filtrada = new ArrayList<>();
        for (LembreteInstancia i : listaDeInstancias) {
            switch (filtroAtivo) {
                case "hoje":        if (isToday(i.data))  filtrada.add(i); break;
                case "programados": if (!i.isConcluido()) filtrada.add(i); break;
                case "concluidos":  if (i.isConcluido())  filtrada.add(i); break;
            }
        }
        return filtrada;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showDeleteConfirmationDialog(LembreteInstancia inst) {
        if (inst == null || inst.templateId == null) return;

        boolean isRecorrente = "DIARIA".equals(inst.tipoRecorrencia)
                || "SEMANAL".equals(inst.tipoRecorrencia);

        if (!isRecorrente) {
            new AlertDialog.Builder(requireContext(), R.style.DialogTheme)
                    .setTitle("Excluir Lembrete")
                    .setMessage("Deseja excluir este lembrete?")
                    .setPositiveButton("Excluir", (d, w) -> deletarTemplate(inst.templateId))
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }

        final int[] opcaoSelecionada = {0};
        String[] opcoes = {
                "Somente este dia",
                "Este e todos os próximos",
                "Todos os dias (excluir tudo)"
        };

        new AlertDialog.Builder(requireContext(), R.style.DialogTheme)
                .setTitle("Excluir lembrete recorrente")
                .setSingleChoiceItems(opcoes, 0, (dialog, which) -> opcaoSelecionada[0] = which)
                .setPositiveButton("Excluir", (dialog, w) -> {
                    switch (opcaoSelecionada[0]) {
                        case 0: deletarInstancia(inst.instanciaId);        break;
                        case 1: deletarInstanciaEFuturas(inst.instanciaId, inst.templateId); break;
                        case 2: deletarTemplate(inst.templateId);           break;
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void setupCard(CardLembreteStatBinding b, String title, String count,
                           int iconRes, int bgRes, int tintColorRes) {
        b.statTitle.setText(title);
        b.statCount.setText(count);
        b.statIcon.setImageResource(iconRes);
        b.statIcon.setBackgroundResource(bgRes);
        b.statIcon.setColorFilter(ContextCompat.getColor(getContext(), tintColorRes),
                PorterDuff.Mode.SRC_IN);
    }

    private void toggleUiForEditMode() {
        if (isEditMode) {
            binding.fabEditarLembrete.setText("Concluir");
            binding.fabNovoLembrete.setVisibility(View.GONE);
        } else {
            binding.fabEditarLembrete.setText("Editar");
            binding.fabNovoLembrete.setVisibility(View.VISIBLE);
        }
    }

    private boolean isToday(String dataStr) {
        if (dataStr == null || dataStr.isEmpty()) return false;
        try {
            Date date = parseDateFormat.parse(dataStr);
            Calendar hoje = Calendar.getInstance();
            Calendar data = Calendar.getInstance();
            data.setTime(date);
            return hoje.get(Calendar.YEAR) == data.get(Calendar.YEAR)
                    && hoje.get(Calendar.DAY_OF_YEAR) == data.get(Calendar.DAY_OF_YEAR);
        } catch (ParseException e) { return false; }
    }

    private String formatarDataParaExibicao(String dataStr) {
        try {
            Date d = parseDateFormat.parse(dataStr);
            return displayDateFormat.format(d);
        } catch (ParseException e) { return dataStr; }
    }

    private void agendarLembrete(int hora, int minuto, String dataStr,
                                 String titulo, String mensagem,
                                 String tipoRecorrencia, long templateId,
                                 @Nullable String dataFim) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo"));
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
            Date dataParsed = sdf.parse(dataStr);
            if (dataParsed != null) c.setTime(dataParsed);
        } catch (ParseException ignored) {}

        c.set(Calendar.HOUR_OF_DAY, hora);
        c.set(Calendar.MINUTE, minuto);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        if (c.before(Calendar.getInstance())) {
            if ("DIARIA".equals(tipoRecorrencia)) {
                c.add(Calendar.DAY_OF_MONTH, 1);
            } else if ("SEMANAL".equals(tipoRecorrencia)) {
                c.add(Calendar.DAY_OF_MONTH, 7);
            } else {
                Log.d("Lembretes", "Horário já passou e sem recorrência, não agendado.");
                return;
            }
        }

        // ✅ requestCode = templateId (estável, único, sem colisão)
        int requestCode = (int) templateId;

        Intent intent = new Intent(getContext(), LembreteReceiver.class);
        intent.putExtra("titulo", titulo);
        intent.putExtra("mensagem", mensagem);
        intent.putExtra("hora", hora);
        intent.putExtra("minuto", minuto);
        intent.putExtra("recorrencia", tipoRecorrencia);
        intent.putExtra("requestCode", requestCode);
        intent.putExtra("templateId", templateId); // ✅ passado explicitamente
        intent.putExtra("dataFim", dataFim); // yyyy-MM-dd ou null

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                getContext(), requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager =
                (AlarmManager) requireActivity().getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        // ✅ Salva no SharedPreferences para sobreviver a reboot
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
        String dataAgendada = sdf.format(c.getTime());
        SharedPreferences prefs = requireContext()
                .getSharedPreferences("lembretes_reboot", Context.MODE_PRIVATE);
        // formato: titulo|mensagem|hora|minuto|recorrencia|data|templateId|dataFim
        String valor = titulo + "|" + mensagem + "|" + hora + "|" + minuto
                + "|" + tipoRecorrencia + "|" + dataAgendada + "|" + templateId
                + "|" + (dataFim == null ? "" : dataFim);
        prefs.edit().putString("lembrete_" + requestCode, valor).apply();

        Log.d("Lembretes", "Agendando '" + titulo + "' para " + c.getTime()
                + " [" + tipoRecorrencia + "] requestCode=" + requestCode);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pendingIntent);
                Toast.makeText(getContext(), "Agendado como alarme aproximado.", Toast.LENGTH_LONG).show();
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pendingIntent);
        }

//        Toast.makeText(getContext(), "DEBUG: alarme p/ " + c.getTime(), Toast.LENGTH_LONG).show();
    }

    private void cancelarAlarmeLocal(long templateId) {
        Context ctx = getContext();
        if (ctx == null) return;

        int requestCode = (int) templateId;

        Intent intent = new Intent(ctx, LembreteReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                ctx, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) alarmManager.cancel(pendingIntent);
        pendingIntent.cancel();

        // Remove da persistência de reboot — senão o BootReceiver ressuscita o alarme
        ctx.getSharedPreferences("lembretes_reboot", Context.MODE_PRIVATE)
                .edit().remove("lembrete_" + requestCode).apply();

        Log.d("Lembretes", "Alarme cancelado — templateId=" + templateId);
    }

    private void limparHistoricoNotificacoes(long templateId) {
        Context ctx = getContext();
        if (ctx == null) return;
        new Thread(() ->
                br.fmu.projetoasthmaspace.Data.Local.NotificacaoDatabase
                        .getInstance(ctx).dao().deletarPorTemplate(templateId)
        ).start();
    }

//método separado chamado uma única vez via flag SharedPreferences
    private void migrarLembretesAntigos(List<LembreteInstancia> instancias) {
        SharedPreferences prefs = requireContext()
                .getSharedPreferences("lembretes_reboot", Context.MODE_PRIVATE);

        // Evita rodar mais de uma vez
        if (prefs.getBoolean("migrado_v2", false)) return;

        SharedPreferences.Editor editor = prefs.edit();

        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String valor = (String) entry.getValue();
            if (valor == null) continue;
            String[] partes = valor.split("\\|");
            if (partes.length != 6) continue; // só entradas antigas

            String titulo = partes[0];
            int hora      = Integer.parseInt(partes[2]);
            int minuto    = Integer.parseInt(partes[3]);

            // Busca o templateId correspondente nas instâncias carregadas
            Long templateId = null;
            for (LembreteInstancia inst : instancias) {
                if (inst.titulo.equals(titulo)
                        && inst.horario != null
                        && inst.horario.startsWith(String.format("%02d:%02d", hora, minuto))) {
                    templateId = inst.templateId;
                    break;
                }
            }

            if (templateId == null) {
                // Não achou correspondência — remove entrada órfã
                editor.remove(entry.getKey());
                Log.d("Migração", "Entrada órfã removida: " + titulo);
                continue;
            }

            // Reconstrói com 7 campos e chave correta
            String novoValor = partes[0] + "|" + partes[1] + "|" + partes[2] + "|"
                    + partes[3] + "|" + partes[4] + "|" + partes[5] + "|" + templateId;
            String novaChave = "lembrete_" + (int) (long) templateId;

            editor.remove(entry.getKey()); // remove chave antiga (hash)
            editor.putString(novaChave, novoValor); // adiciona com chave nova
            Log.d("Migração", "Migrado: " + titulo + " → templateId=" + templateId);
        }

        editor.putBoolean("migrado_v2", true);
        editor.apply();

        // Reagenda tudo com os dados corrigidos
        if (getContext() != null) {
            new br.fmu.projetoasthmaspace.Data.worker.BootReceiver()
                    .onReceive(requireContext(),
                            new Intent(Intent.ACTION_BOOT_COMPLETED));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}