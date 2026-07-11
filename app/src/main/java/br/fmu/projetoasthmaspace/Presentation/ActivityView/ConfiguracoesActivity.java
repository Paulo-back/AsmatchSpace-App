package br.fmu.projetoasthmaspace.Presentation.ActivityView;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


import androidx.work.WorkManager;

import br.fmu.projetoasthmaspace.Core.Util.AlterarSenhaRequest;
import br.fmu.projetoasthmaspace.Data.worker.NotificacaoScheduler;
import br.fmu.projetoasthmaspace.Core.Session.UserSessionManager;
import br.fmu.projetoasthmaspace.R;
import br.fmu.projetoasthmaspace.Data.Service.Client.ApiClient;
import br.fmu.projetoasthmaspace.Data.Service.Client.ApiService;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ConfiguracoesActivity extends BaseActivity  {

    private static final String PREFS_CONFIG      = "CONFIG";
    private static final String KEY_NOTIFICACOES  = "notificacoes_ativas";
    private static final String KEY_LEMBRETES     = "lembretes_ativos";
    private static final String KEY_TEMA          = "tema_escuro";

    private SharedPreferences prefs;
    private UserSessionManager session;

    private TextView txtStatusNotif;
    private TextView txtStatusLembretes;
    private TextView txtStatusTema;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configuracoes);

        prefs   = getSharedPreferences(PREFS_CONFIG, MODE_PRIVATE);
        session = new UserSessionManager(this);

        txtStatusNotif      = findViewById(R.id.txtStatusNotif);
        txtStatusLembretes  = findViewById(R.id.txtStatusLembretes);

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());

        setupNotificacoes();
        setupLembretes();
//        setupTema();
        setupAlterarSenha();
        setupExcluirConta();
        setupSobreApp();
        setupLogout();

        setupLocalizacao();

    }

    private void setupLocalizacao() {
        findViewById(R.id.btnPermissaoLocalizacao).setOnClickListener(v -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.fromParts("package", getPackageName(), null));
            startActivity(intent);
        });
    }

    // ── NOTIFICAÇÕES DE QUALIDADE DO AR ───────────────────────────
    private void setupNotificacoes() {
        boolean ativo = prefs.getBoolean(KEY_NOTIFICACOES, true);
        atualizarStatus(txtStatusNotif, ativo);

        findViewById(R.id.toggleNotificacoes).setOnClickListener(v -> {
            boolean novo = !prefs.getBoolean(KEY_NOTIFICACOES, true);
            prefs.edit().putBoolean(KEY_NOTIFICACOES, novo).apply();
            atualizarStatus(txtStatusNotif, novo);

            if (novo) {
                NotificacaoScheduler.agendarVerificacaoAr(this);
                Toast.makeText(this, "Notificações do ar ativadas", Toast.LENGTH_SHORT).show();
            } else {
                WorkManager.getInstance(this).cancelUniqueWork("qualidade_ar_worker");
                Toast.makeText(this, "Notificações do ar desativadas", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── LEMBRETES DE MEDICAÇÃO ────────────────────────────────────
    private void setupLembretes() {
        boolean ativo = prefs.getBoolean(KEY_LEMBRETES, true);
        atualizarStatus(txtStatusLembretes, ativo);

        findViewById(R.id.toggleLembretes).setOnClickListener(v -> {
            boolean novo = !prefs.getBoolean(KEY_LEMBRETES, true);
            prefs.edit().putBoolean(KEY_LEMBRETES, novo).apply();
            atualizarStatus(txtStatusLembretes, novo);

            String msg = novo ? "Lembretes de medicação ativados" : "Lembretes de medicação desativados";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            // O LembreteReceiver já lê essa preferência antes de disparar a notificação
        });
    }


    // ── ALTERAR SENHA ─────────────────────────────────────────────
    private void setupAlterarSenha() {
        findViewById(R.id.btnAlterarSenha).setOnClickListener(v ->
                startActivity(new Intent(this, AlterarSenhaActivity.class))
        );
    }

    private void mostrarDialogSenha() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_alterar_senha, null);

        EditText etAtual      = dialogView.findViewById(R.id.etSenhaAtual);
        EditText etNova       = dialogView.findViewById(R.id.etNovaSenha);
        EditText etConfirmar  = dialogView.findViewById(R.id.etConfirmarSenha);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.DialogTheme)
                .setTitle("Alterar Senha")
                .setView(dialogView)
                .setPositiveButton("Salvar", null) // null para controlar manualmente
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
                String atual     = etAtual.getText().toString().trim();
                String nova      = etNova.getText().toString().trim();
                String confirmar = etConfirmar.getText().toString().trim();

                if (atual.isEmpty() || nova.isEmpty() || confirmar.isEmpty()) {
                    Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!nova.equals(confirmar)) {
                    Toast.makeText(this, "As senhas não coincidem", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (nova.length() < 6) {
                    Toast.makeText(this, "Mínimo 6 caracteres", Toast.LENGTH_SHORT).show();
                    return;
                }

                enviarAlteracaoSenha(atual, nova, confirmar);
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void enviarAlteracaoSenha(String atual, String nova, String confirmar) {
        ApiService api = ApiClient.getApiService(this);
        api.alterarSenha(new AlterarSenhaRequest(atual, nova, confirmar))
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(ConfiguracoesActivity.this,
                                    "Senha alterada com sucesso!", Toast.LENGTH_SHORT).show();
                        } else if (response.code() == 400) {
                            Toast.makeText(ConfiguracoesActivity.this,
                                    "Senha atual incorreta", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(ConfiguracoesActivity.this,
                                    "Erro ao alterar senha", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Toast.makeText(ConfiguracoesActivity.this,
                                "Erro de conexão", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ── EXCLUIR CONTA ─────────────────────────────────────────────
    private void excluirConta() {
        ApiService api = ApiClient.getApiService(this);
        Long clienteId = session.getClienteId();

        if (clienteId == -1L) {
            // ID não está em cache — busca no servidor e tenta de novo
            api.getMeuId().enqueue(new Callback<Long>() {
                @Override
                public void onResponse(Call<Long> call, Response<Long> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        session.saveClienteId(response.body()); // repovoa o cache
                        inativarNoServidor(response.body());
                    } else {
                        Toast.makeText(ConfiguracoesActivity.this,
                                "Não foi possível identificar sua conta. Tente novamente.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onFailure(Call<Long> call, Throwable t) {
                    Toast.makeText(ConfiguracoesActivity.this,
                            "Erro de conexão", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        inativarNoServidor(clienteId);
    }

    private void setupExcluirConta() {
        findViewById(R.id.btnExcluirConta).setOnClickListener(v ->
                new AlertDialog.Builder(this, R.style.DialogTheme)
                        .setTitle("Excluir Conta")
                        .setMessage("Tem certeza? Esta ação não pode ser desfeita.")
                        .setPositiveButton("Excluir", (d, w) -> excluirConta())
                        .setNegativeButton("Cancelar", null)
                        .show()
        );
    }

    private void inativarNoServidor(Long clienteId) {
        ApiClient.getApiService(this).inativarCliente(clienteId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(ConfiguracoesActivity.this,
                            "Conta encerrada.", Toast.LENGTH_LONG).show();
                    fazerLogout();
                } else {
                    Toast.makeText(ConfiguracoesActivity.this,
                            "Erro ao excluir conta", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(ConfiguracoesActivity.this,
                        "Erro de conexão", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── SOBRE O APP ───────────────────────────────────────────────
    private void setupSobreApp() {
        findViewById(R.id.btnSobreApp).setOnClickListener(v ->
                new AlertDialog.Builder(this, R.style.DialogTheme)
                        .setTitle("Sobre o Asthma Space")
                        .setMessage(
                                "Versão: 3.0.0\n\n" +
                                        "Aplicativo de apoio a pacientes com asma para monitorar " +
                                        "sintomas, qualidade do ar e lembretes de medicação.\n\n" +
                                        "Desenvolvido por: Paulo Rosa, Edimário Silva e Stefanne Pardim\n" +
                                        "Contato: asthmaspace@gmail.com"
                        )
                        .setPositiveButton("Fechar", null)
                        .show()
        );
    }

    // ── LOGOUT ────────────────────────────────────────────────────
    private void setupLogout() {
        findViewById(R.id.btnLogout).setOnClickListener(v ->
                new AlertDialog.Builder(this, R.style.DialogTheme)
                        .setTitle("Sair")
                        .setMessage("Deseja sair da sua conta?")
                        .setPositiveButton("Sair", (d, w) -> fazerLogout())
                        .setNegativeButton("Cancelar", null)
                        .show()
        );
    }

    private void fazerLogout() {
        new UserSessionManager(this).logout(this);
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ── HELPERS ───────────────────────────────────────────────────
    private void atualizarStatus(TextView txt, boolean ativo) {
        txt.setText(ativo ? "Ativado" : "Desativado");
        txt.setTextColor(ativo ? 0xFF4FC3F7 : 0xFF5A7A9F);
    }

    private void atualizarStatusTema(boolean escuro) {
        txtStatusTema.setText(escuro ? "Escuro" : "Claro");
        txtStatusTema.setTextColor(escuro ? 0xFF4FC3F7 : 0xFF5A7A9F);
    }
}