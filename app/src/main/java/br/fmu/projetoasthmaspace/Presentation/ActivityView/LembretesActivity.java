package br.fmu.projetoasthmaspace.Presentation.ActivityView;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
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
    private final List<LembreteInstancia> listaDeInstancias = new ArrayList<>();
    private int diasPassadosFiltro = 7;

    // ── Skeleton ──────────────────────────────────────────────────────────────
    private ShimmerFrameLayout shimmerLayout;
    private boolean skeletonInflado = false;
    // ─────────────────────────────────────────────────────────────────────────

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
        if (!hidden) {
            carregarInstanciasPorPeriodo();
        }
    }

    // ── Skeleton helpers ──────────────────────────────────────────────────────

    private void mostrarSkeleton() {
        if (binding == null) return;
        if (!skeletonInflado) {
            shimmerLayout = (ShimmerFrameLayout) binding.skeletonStub.inflate();
            skeletonInflado = true;
        }
        shimmerLayout.setVisibility(View.VISIBLE);
        shimmerLayout.startShimmer();
        // Esconde apenas o ScrollView da lista; cabeçalho continua visível
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

    /**
     * O ScrollView da lista não tem id próprio no XML original.
     * Usamos getChildAt para localizá-lo de forma segura pelo ViewStub stub.
     * Como o ViewBinding não gera binding para ScrollViews sem id,
     * adicionamos um id agora via XML — veja a nota abaixo.
     */
    private View getScrollViewLista() {
        // O id scroll_lista_lembretes foi adicionado no activity_lembretes.xml
        return binding.getRoot().findViewById(R.id.scroll_lista_lembretes);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void carregarInstanciasPorPeriodo() {
        if (!dadosCarregados) mostrarSkeleton();

        api.listarInstanciasPorPeriodo(diasPassadosFiltro).enqueue(new Callback<List<LembreteInstancia>>() {
            @Override
            public void onResponse(Call<List<LembreteInstancia>> call,
                                   Response<List<LembreteInstancia>> response) {
                if (!isAdded() || binding == null) return;
                if (response.isSuccessful() && response.body() != null) {
                    dadosCarregados = true;
                    listaDeInstancias.clear();
                    listaDeInstancias.addAll(response.body());
                    atualizarPainelDeResumo();
                    atualizarTextoBotaoFiltro();
                    renderizarLista();
                    esconderSkeleton(); // ← só após tudo renderizado
                } else {
                    Toast.makeText(getContext(), "Erro ao buscar lembretes", Toast.LENGTH_SHORT).show();
                    esconderSkeleton(); // ← evita tela travada
                }
            }

            @Override
            public void onFailure(Call<List<LembreteInstancia>> call, Throwable t) {
                if (!isAdded() || binding == null) return;
                Toast.makeText(getContext(), "Falha na conexão", Toast.LENGTH_SHORT).show();
                esconderSkeleton(); // ← evita tela travada
            }
        });
    }

    // Extraia a data do request antes de chamar
// O req já tem dataInicio no formato yyyy-MM-dd
    private void criarTemplate(LembreteTemplateRequest req, int hora, int minuto, String titulo) {
        api.criarTemplate(req).enqueue(new Callback<LembreteTemplate>() {
            @Override
            public void onResponse(Call<LembreteTemplate> call, Response<LembreteTemplate> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    Toast.makeText(getContext(), "Lembrete salvo!", Toast.LENGTH_SHORT).show();
                    LocationHelper.obterLocalizacao(requireContext(),
                            (lat, lon) -> agendarLembrete(hora, minuto, req.dataInicio,
                                    titulo, "Lembrete programado!", req.tipoRecorrencia));
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

    private void atualizarTemplate(Long templateId, LembreteTemplateRequest req) {
        api.atualizarTemplate(templateId, req).enqueue(new Callback<LembreteTemplate>() {
            @Override
            public void onResponse(Call<LembreteTemplate> call,
                                   Response<LembreteTemplate> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    Toast.makeText(getContext(), "Lembrete atualizado!", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(getContext(), "Lembrete excluído!", Toast.LENGTH_SHORT).show();
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

    private void showFiltroPeriodoDialog() {
        String[] opcoes = {"Últimos 7 dias", "Últimos 15 dias", "Últimos 30 dias", "Últimos 60 dias"};
        int[]    valores = {7, 15, 30, 60};

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
        binding.btnFiltrarPeriodo.setText("Últimos " + diasPassadosFiltro + " dias");
    }

    // ── Dialog de criação/edição ──────────────────────────────────────────────

    private void showTemplateDialog(@Nullable final LembreteInstancia instanciaExistente) {
        final boolean isEditing = instanciaExistente != null;

        final int[]    horaSelecionada    = {Calendar.getInstance().get(Calendar.HOUR_OF_DAY)};
        final int[]    minutoSelecionado  = {Calendar.getInstance().get(Calendar.MINUTE)};
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
                    atualizarTemplate(instanciaExistente.templateId, req);
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
            TextView     dataHeader    = groupView.findViewById(R.id.text_data_header);
            ImageView    deleteGroup   = groupView.findViewById(R.id.icon_delete_group);
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
                    iconRecorrencia.setVisibility(View.GONE);
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

            dataHeader.setOnClickListener(v ->
                    containerDia.setVisibility(
                            containerDia.getVisibility() == View.GONE
                                    ? View.VISIBLE : View.GONE));

            binding.containerLembretes.addView(groupView);
        }
    }

    // ── Painel de resumo e filtros ────────────────────────────────────────────

    private void atualizarPainelDeResumo() {
        long hojeCount       = listaDeInstancias.stream().filter(i -> isToday(i.data)).count();
        long pendentesCount  = listaDeInstancias.stream().filter(i -> !i.isConcluido()).count();
        long todosCount      = listaDeInstancias.size();
        long concluidosCount = listaDeInstancias.stream().filter(LembreteInstancia::isConcluido).count();

        setupCard(binding.statHoje,       "Hoje",       String.valueOf(hojeCount),
                R.drawable.icon_hoje,        R.drawable.bg_icon_hoje,        R.color.blue_navy);
        setupCard(binding.statProgramados, "Pendentes", String.valueOf(pendentesCount),
                R.drawable.icon_programados, R.drawable.bg_icon_programados, R.color.red_dark);
        setupCard(binding.statTodos,       "Todos",     String.valueOf(todosCount),
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
        new AlertDialog.Builder(requireContext())
                .setTitle("Excluir Lembrete")
                .setMessage("Isso removerá o lembrete e todas as suas recorrências. Confirma?")
                .setPositiveButton("Excluir", (d, w) -> deletarTemplate(inst.templateId))
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
                                 String titulo, String mensagem, String tipoRecorrencia) {
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

        // Se o horário já passou, avança conforme a recorrência
        if (c.before(Calendar.getInstance())) {
            if ("DIARIA".equals(tipoRecorrencia)) {
                // Próximo disparo = amanhã no mesmo horário
                c.add(Calendar.DAY_OF_MONTH, 1);
            } else if ("SEMANAL".equals(tipoRecorrencia)) {
                // Próximo disparo = semana que vem no mesmo horário
                c.add(Calendar.DAY_OF_MONTH, 7);
            } else {
                // NENHUMA e já passou — não agenda
                Log.d("Lembretes", "Horário já passou e sem recorrência, não agendado.");
                return;
            }
        }

        Intent intent = new Intent(getContext(), LembreteReceiver.class);
        intent.putExtra("titulo", titulo);
        intent.putExtra("mensagem", mensagem);
        intent.putExtra("hora", hora);
        intent.putExtra("minuto", minuto);
        intent.putExtra("recorrencia", tipoRecorrencia);

        int requestCode = Math.abs((titulo + hora + minuto).hashCode());
        intent.putExtra("requestCode", requestCode);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                getContext(),
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager =
                (AlarmManager) requireActivity().getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Log.d("Lembretes", "Agendando '" + titulo + "' para " + c.getTime() + " [" + tipoRecorrencia + "]");

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
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
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}